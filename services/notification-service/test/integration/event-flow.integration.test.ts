import "reflect-metadata";
import type { INestApplication, Type } from "@nestjs/common";
import { Test } from "@nestjs/testing";
import { contractExample, createTopicsWhenReady, type EventEnvelope, Topics, publishEvent } from "@swiftdrop/common";
import { type StartedKafkaContainer, KafkaContainer } from "@testcontainers/kafka";
import { type StartedMongoDBContainer, MongoDBContainer } from "@testcontainers/mongodb";
import { Kafka, type Producer, logLevel } from "kafkajs";
import { GenericContainer, type StartedTestContainer, Wait } from "testcontainers";

// Real Kafka, MongoDB and Mailpit containers (Docker must be running): an event on Kafka must end up
// as an email in Mailpit, and a redelivered event must not send a second one.
jest.setTimeout(420_000);

const TRACKING = "SD261020K7Q2M9X";
const SENDER_EMAIL = "orders@lankabooks.lk";
const PROBE_TRACKING = "SD261020PROBE00";
const PROBE_EMAIL = "probe@example.test";

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

interface MailpitMessage {
  Subject: string;
  To: { Address: string }[];
}

async function waitFor<T>(read: () => Promise<T | null | undefined | false>, timeoutMs = 90_000): Promise<T> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const value = await read();
    if (value) return value;
    await sleep(500);
  }
  throw new Error("condition not met in time");
}

/** A contract example with a fresh event id (or the given one) and the given data changes. */
function event(
  contract: "parcel-created.v1" | "parcel-status-changed.v1",
  data: Record<string, unknown> = {},
  eventId: string = crypto.randomUUID(),
): EventEnvelope<unknown> {
  const base = contractExample(contract) as EventEnvelope<Record<string, unknown>>;
  return { ...base, eventId, data: { ...base.data, trackingNumber: TRACKING, ...data } };
}

describe("notification-service event flow on real Kafka, MongoDB and Mailpit", () => {
  let kafkaContainer: StartedKafkaContainer;
  let mongoContainer: StartedMongoDBContainer;
  let mailpit: StartedTestContainer;
  let mailpitApi: string;
  let producer: Producer;
  let app: INestApplication;

  async function messages(): Promise<MailpitMessage[]> {
    const response = await fetch(`${mailpitApi}/api/v1/messages?limit=100`);
    return ((await response.json()) as { messages: MailpitMessage[] }).messages;
  }

  const withSubject = async (subject: string) => (await messages()).filter((m) => m.Subject === subject);

  beforeAll(async () => {
    [kafkaContainer, mongoContainer, mailpit] = await Promise.all([
      new KafkaContainer("confluentinc/cp-kafka:7.6.1").start(),
      new MongoDBContainer("mongo:7").start(),
      new GenericContainer("axllent/mailpit:v1.32.0")
        .withExposedPorts(1025, 8025)
        .withWaitStrategy(Wait.forHttp("/livez", 8025))
        .start(),
    ]);
    mailpitApi = `http://${mailpit.getHost()}:${mailpit.getMappedPort(8025)}`;
    const brokers = [`${kafkaContainer.getHost()}:${kafkaContainer.getMappedPort(9093)}`];

    // Point the service at the containers.
    process.env.KAFKA_BROKERS = brokers.join(",");
    process.env.MONGO_URI = `${mongoContainer.getConnectionString()}/notifications?directConnection=true`;
    process.env.SMTP_HOST = mailpit.getHost();
    process.env.SMTP_PORT = String(mailpit.getMappedPort(1025));

    // A fresh broker on a busy CI runner can reset the first connections for several seconds after
    // the container reports started; kafkajs's default retries (about five over a few seconds) ran
    // out on one run, so this client keeps trying for up to half a minute.
    const kafka = new Kafka({
      clientId: "event-flow-it",
      brokers,
      logLevel: logLevel.NOTHING,
      connectionTimeout: 10_000,
      retry: { initialRetryTime: 500, retries: 15, maxRetryTime: 30_000 },
    });
    const admin = kafka.admin();
    await admin.connect();
    await createTopicsWhenReady(admin, {
      waitForLeaders: true,
      topics: [
        Topics.PARCEL_CREATED,
        Topics.PARCEL_STATUS_CHANGED,
        Topics.PARCEL_ASSIGNED,
        Topics.MERCHANT_LIFECYCLE,
        Topics.NOTIFICATION_DEAD_LETTER,
      ].map((topic) => ({ topic, numPartitions: 1 })),
    });
    await admin.disconnect();
    producer = kafka.producer();
    await producer.connect();

    // Loaded only now, because the service reads its environment when its modules load.
    const { AppModule } = require("../../src/app.module") as { AppModule: Type<unknown> };
    const moduleRef = await Test.createTestingModule({ imports: [AppModule] }).compile();
    app = moduleRef.createNestApplication({ logger: false });
    await app.init();

    // A new consumer group starts at the latest offset, so events sent before its first fetch are
    // skipped. Send a probe booking for another parcel until its email arrives: from then on the
    // consumer is reading and nothing is missed.
    await waitFor(async () => {
      await publishEvent(producer, Topics.PARCEL_CREATED, "probe", event("parcel-created.v1", {
        trackingNumber: PROBE_TRACKING,
        contacts: { senderName: "Probe", senderEmail: PROBE_EMAIL, recipientName: "Probe", recipientPhone: "+94770000000" },
      }));
      await sleep(2000);
      return (await messages()).some((m) => m.To.some((to) => to.Address === PROBE_EMAIL));
    });
  });

  afterAll(async () => {
    await app?.close();
    await producer?.disconnect();
    await Promise.all([kafkaContainer?.stop(), mongoContainer?.stop(), mailpit?.stop()]);
  });

  it("emails the booking confirmation for ParcelCreated", async () => {
    await publishEvent(producer, Topics.PARCEL_CREATED, TRACKING, event("parcel-created.v1"));

    const [mail] = await waitFor(async () => {
      const found = await withSubject(`SwiftDrop: Booking Confirmed [${TRACKING}]`);
      return found.length > 0 && found;
    });
    expect(mail.To.map((to) => to.Address)).toEqual([SENDER_EMAIL]);
  });

  it("emails a status change once, even when Kafka redelivers the event", async () => {
    const delivered = event("parcel-status-changed.v1", { toStatus: "DELIVERED" });
    await publishEvent(producer, Topics.PARCEL_STATUS_CHANGED, TRACKING, delivered);
    const subject = `SwiftDrop: Parcel Delivered [${TRACKING}]`;
    const [mail] = await waitFor(async () => {
      const found = await withSubject(subject);
      return found.length > 0 && found;
    });
    // The address comes from the contacts cached when the parcel was created.
    expect(mail.To.map((to) => to.Address)).toEqual([SENDER_EMAIL]);

    // The same event again (an at-least-once redelivery), then a different event on the same
    // partition. Once the later event's email is in, the duplicate has been handled too.
    await publishEvent(producer, Topics.PARCEL_STATUS_CHANGED, TRACKING, delivered);
    await publishEvent(producer, Topics.PARCEL_STATUS_CHANGED, TRACKING,
      event("parcel-status-changed.v1", { fromStatus: "DELIVERED", toStatus: "FAILED", proof: null,
        failureReason: "RECIPIENT_UNAVAILABLE" }));
    await waitFor(async () => (await messages()).some((m) => m.Subject.includes("Failed") && m.Subject.includes(TRACKING)));

    expect(await withSubject(subject)).toHaveLength(1);
  });
});
