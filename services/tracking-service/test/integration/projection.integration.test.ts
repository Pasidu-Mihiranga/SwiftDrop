import { randomUUID } from "node:crypto";
import {
  contractExample,
  createTopicsWhenReady,
  type EventEnvelope,
  MongoIdempotencyStore,
  publishEvent,
  startEventConsumer,
  Topics,
} from "@swiftdrop/common";
import { type StartedKafkaContainer, KafkaContainer } from "@testcontainers/kafka";
import { type StartedMongoDBContainer, MongoDBContainer } from "@testcontainers/mongodb";
import { type Consumer, Kafka, logLevel, type Producer } from "kafkajs";
import { type Connection, createConnection } from "mongoose";
import { trackingHandlers } from "../../src/consumers/parcel-events.consumer";
import { toPublicView } from "../../src/modules/timeline/domain/public-view";
import { MongoTimelineRepository } from "../../src/modules/timeline/infrastructure/mongo-timeline.repository";
import { timelineSchema } from "../../src/modules/timeline/infrastructure/timeline.schema";
import type { TimelineBroadcaster } from "../../src/modules/timeline/timeline.broadcast.port";
import { TimelineService } from "../../src/modules/timeline/timeline.service";

// Starts real Kafka and MongoDB containers. Docker must be running.
jest.setTimeout(180_000);

const TRACKING = "SD261020K7Q2M9X";
const PARCEL = "0b6f4a9e-7c1d-4e2f-9a3b-5c6d7e8f9a0b";
const silent = { info: () => {}, warn: () => {}, error: () => {}, debug: () => {}, child: () => silentLogger };
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const silentLogger: any = silent;

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/** A fresh copy of a contract example with its own eventId, bound to the test parcel. */
function event(
  contract: "parcel-created.v1" | "parcel-status-changed.v1" | "parcel-assigned.v1",
  eventType: string,
  data: Record<string, unknown> = {},
  eventId: string = randomUUID(),
): EventEnvelope<unknown> {
  const base = contractExample(contract) as EventEnvelope<Record<string, unknown>>;
  return { ...base, eventType, eventId, data: { ...base.data, parcelId: PARCEL, trackingNumber: TRACKING, ...data } };
}

async function waitFor(check: () => Promise<boolean>, timeoutMs = 30_000, describe?: () => Promise<string>): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await check()) return;
    await sleep(500);
  }
  throw new Error(`condition not met in time${describe ? `: ${await describe()}` : ""}`);
}

describe("parcel events to timeline (Kafka and MongoDB)", () => {
  let kafkaContainer: StartedKafkaContainer;
  let mongoContainer: StartedMongoDBContainer;
  let connection: Connection;
  let repository: MongoTimelineRepository;
  let producer: Producer;
  let consumer: Consumer;
  let deadLetterConsumer: Consumer;
  let broadcasts = 0;
  const deadLetterErrors: string[] = [];

  beforeAll(async () => {
    kafkaContainer = await new KafkaContainer("confluentinc/cp-kafka:7.6.1").start();
    mongoContainer = await new MongoDBContainer("mongo:7").start();
    // The container runs a single-member replica set that advertises its container id; connect directly to it.
    const uri = new URL(mongoContainer.getConnectionString());
    uri.searchParams.set("directConnection", "true");
    connection = createConnection(uri.toString());
    await connection.asPromise(); // fail here with the real error if MongoDB cannot be reached
    repository = new MongoTimelineRepository(connection.model("Timeline", timelineSchema));

    const broadcaster: TimelineBroadcaster = {
      timelineChanged: async () => {
        broadcasts++;
      },
    };
    const kafka = new Kafka({
      clientId: "projection-test",
      brokers: [`${kafkaContainer.getHost()}:${kafkaContainer.getMappedPort(9093)}`],
      logLevel: logLevel.NOTHING,
    });

    const admin = kafka.admin();
    await admin.connect();
    await createTopicsWhenReady(admin, { topics: [{ topic: Topics.TRACKING_DEAD_LETTER }] });
    await admin.disconnect();

    const idempotency = new MongoIdempotencyStore(connection.collection("processed_event"));
    await idempotency.ensureIndexes();
    const started = await startEventConsumer(kafka, {
      groupId: "tracking-service-test",
      consumerName: "tracking-projection",
      topics: [Topics.PARCEL_CREATED, Topics.PARCEL_STATUS_CHANGED, Topics.PARCEL_ASSIGNED],
      handlers: trackingHandlers(new TimelineService(repository), broadcaster),
      idempotency,
      logger: silentLogger,
      deadLetterTopic: Topics.TRACKING_DEAD_LETTER,
    });
    consumer = started.consumer;
    producer = started.producer;

    deadLetterConsumer = kafka.consumer({ groupId: "dead-letter-test" });
    await deadLetterConsumer.connect();
    await deadLetterConsumer.subscribe({ topic: Topics.TRACKING_DEAD_LETTER, fromBeginning: true });
    void deadLetterConsumer.run({
      eachMessage: async ({ message }) => {
        deadLetterErrors.push(message.headers?.["x-error"]?.toString() ?? "");
      },
    });

    // The consumer joins its group asynchronously and reads only messages produced after it joins.
    await sleep(8000);
  });

  afterAll(async () => {
    await consumer?.disconnect();
    await producer?.disconnect();
    await deadLetterConsumer?.disconnect();
    await connection?.close();
    await mongoContainer?.stop();
    await kafkaContainer?.stop();
  });

  it("projects a created parcel, then a status change, into a masked public view", async () => {
    await publishEvent(producer, Topics.PARCEL_CREATED, PARCEL, event("parcel-created.v1", "ParcelCreated"));
    await waitFor(async () => (await repository.findByTrackingNumber(TRACKING)) !== null);
    expect((await repository.findByTrackingNumber(TRACKING))?.currentStatus).toBe("CREATED");

    await publishEvent(
      producer,
      Topics.PARCEL_STATUS_CHANGED,
      PARCEL,
      event("parcel-status-changed.v1", "ParcelStatusChanged", { toStatus: "PICKED_UP" }),
    );
    await waitFor(async () => (await repository.findByTrackingNumber(TRACKING))?.currentStatus === "PICKED_UP");
    const timeline = await repository.findByTrackingNumber(TRACKING);
    const view = toPublicView(timeline!);
    expect(view.recipient).toMatch(/^[A-Z]\*\*\* [A-Z]\.$/);
    expect(JSON.stringify(view)).not.toMatch(/\d{9,}/);
    expect(broadcasts).toBeGreaterThanOrEqual(2);
  });

  it("ignores a redelivered event with the same eventId", async () => {
    const dup = event("parcel-status-changed.v1", "ParcelStatusChanged", { toStatus: "IN_TRANSIT" }, "7d2c1f7e-3b8a-4c6e-9f10-2a4b5c6d7e8f");
    await publishEvent(producer, Topics.PARCEL_STATUS_CHANGED, PARCEL, dup);
    await waitFor(
      async () => (await repository.findByTrackingNumber(TRACKING))?.currentStatus === "IN_TRANSIT",
      30_000,
      async () => JSON.stringify({ state: await repository.findByTrackingNumber(TRACKING), deadLetters: deadLetterErrors }),
    );
    const versionAfterFirst = (await repository.findByTrackingNumber(TRACKING))!.version;

    await publishEvent(producer, Topics.PARCEL_STATUS_CHANGED, PARCEL, dup);
    await sleep(3000);
    expect((await repository.findByTrackingNumber(TRACKING))!.version).toBe(versionAfterFirst);
  });

  it("places a late status change by time and keeps the latest status", async () => {
    await publishEvent(
      producer,
      Topics.PARCEL_STATUS_CHANGED,
      PARCEL,
      event("parcel-status-changed.v1", "ParcelStatusChanged", { toStatus: "DELIVERED", changedAt: "2030-01-02T10:00:00Z" }),
    );
    await waitFor(async () => (await repository.findByTrackingNumber(TRACKING))?.currentStatus === "DELIVERED");

    await publishEvent(
      producer,
      Topics.PARCEL_STATUS_CHANGED,
      PARCEL,
      event("parcel-status-changed.v1", "ParcelStatusChanged", { toStatus: "OUT_FOR_DELIVERY", changedAt: "2030-01-01T10:00:00Z" }),
    );
    await sleep(3000);
    const timeline = (await repository.findByTrackingNumber(TRACKING))!;
    expect(timeline.currentStatus).toBe("DELIVERED");
    const times = timeline.events.map((e) => e.at);
    expect(times).toEqual([...times].sort());
  });

  it("sends an event that breaks its contract to the dead-letter topic with the reason", async () => {
    const bad = event("parcel-status-changed.v1", "ParcelStatusChanged", { parcelId: "not-a-uuid" });
    await publishEvent(producer, Topics.PARCEL_STATUS_CHANGED, PARCEL, bad);
    await waitFor(async () => deadLetterErrors.some((error) => error.includes("Invalid parcel-status-changed.v1")), 90_000);
  });
});
