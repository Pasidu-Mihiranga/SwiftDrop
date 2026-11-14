import { createServer, type Server } from "node:http";
import { createEnvelope, type Logger, type ParcelCreated, type WebhookDeliveryDeadLettered } from "@swiftdrop/common";
import { MongoClient, type Db } from "mongodb";
import { GenericContainer, type StartedTestContainer } from "testcontainers";
import { DeliveryLogService } from "../../src/modules/webhooks/application/delivery-log.service";
import { DeliveryProcessor } from "../../src/modules/webhooks/application/delivery-processor";
import { EndpointService } from "../../src/modules/webhooks/application/endpoint.service";
import { FanOutService } from "../../src/modules/webhooks/application/fan-out.service";
import { BackoffSchedule } from "../../src/modules/webhooks/domain/backoff";
import { verifySignature } from "../../src/modules/webhooks/domain/signer";
import { UrlPolicy } from "../../src/modules/webhooks/domain/url-policy";
import { AesSecretCipher } from "../../src/modules/webhooks/infrastructure/aes-secret-cipher";
import { BullMqDeliveryQueue } from "../../src/modules/webhooks/infrastructure/bullmq-delivery-queue";
import { FetchWebhookSender } from "../../src/modules/webhooks/infrastructure/fetch-sender";
import {
  MongoDeliveryRepository, MongoEndpointRepository, MongoMerchantStateRepository, MongoParcelRefRepository,
} from "../../src/modules/webhooks/infrastructure/mongo-repositories";
import { TEST_KEY } from "./fakes";

jest.setTimeout(180_000);

/**
 * The delivery pipeline against real MongoDB and Redis: a parcel event becomes a BullMQ job, the worker
 * POSTs a signed webhook to a local receiver, failures are retried on the (shortened) schedule, the last
 * failure dead-letters the delivery, and an admin replay delivers it once the receiver recovers.
 */
describe("delivery flow (MongoDB + Redis)", () => {
  let mongo: StartedTestContainer;
  let redis: StartedTestContainer;
  let client: MongoClient;
  let db: Db;
  let receiver: Server;
  let receiverUrl: string;
  const received: { body: string; signature?: string }[] = [];
  let answer = 200;
  const deadLetters: WebhookDeliveryDeadLettered[] = [];
  let queue: BullMqDeliveryQueue;
  let services: { endpoints: EndpointService; fanOut: FanOutService; log: DeliveryLogService; deliveries: MongoDeliveryRepository };

  beforeAll(async () => {
    [mongo, redis] = await Promise.all([
      new GenericContainer("mongo:7.0.45").withExposedPorts(27017).start(),
      new GenericContainer("redis:7.4.11-alpine").withExposedPorts(6379).start(),
    ]);
    client = await MongoClient.connect(`mongodb://${mongo.getHost()}:${mongo.getMappedPort(27017)}/webhooks`);
    db = client.db();
    receiver = createServer((req, res) => {
      let body = "";
      req.on("data", (chunk) => (body += chunk));
      req.on("end", () => {
        received.push({ body, signature: req.headers["x-swiftdrop-signature"] as string | undefined });
        res.writeHead(answer).end();
      });
    });
    await new Promise<void>((resolve) => receiver.listen(0, "127.0.0.1", resolve));
    receiverUrl = `http://127.0.0.1:${(receiver.address() as { port: number }).port}/webhooks`;

    const backoff = new BackoffSchedule([0.2, 0.2]);
    const settings = { backoff, timeoutMs: 2_000, urlPolicy: new UrlPolicy({ allowHttp: true, allowPrivateNetworks: true }), now: () => new Date() };
    const endpoints = new MongoEndpointRepository(db);
    const deliveries = new MongoDeliveryRepository(db);
    const merchants = new MongoMerchantStateRepository(db);
    const parcelRefs = new MongoParcelRefRepository(db);
    await Promise.all([endpoints.ensureIndexes(), deliveries.ensureIndexes(), parcelRefs.ensureIndexes()]);
    const cipher = new AesSecretCipher(TEST_KEY);
    queue = new BullMqDeliveryQueue({ host: redis.getHost(), port: redis.getMappedPort(6379), maxRetriesPerRequest: null }, backoff);
    const processor = new DeliveryProcessor(deliveries, endpoints, merchants, new FetchWebhookSender(), cipher,
      { publish: async (event) => { deadLetters.push(event); } }, settings);
    queue.startWorker(processor, { error: () => undefined } as unknown as Logger);
    services = {
      endpoints: new EndpointService(endpoints, deliveries, queue, cipher, settings),
      fanOut: new FanOutService(endpoints, deliveries, merchants, parcelRefs, queue, settings),
      log: new DeliveryLogService(deliveries, queue),
      deliveries,
    };
  });

  afterAll(async () => {
    await queue?.close();
    await client?.close();
    receiver?.close();
    await Promise.all([mongo?.stop(), redis?.stop()]);
  });

  const parcelCreated = (parcelId: string) => createEnvelope("ParcelCreated", {
    parcelId, trackingNumber: "SD261114K7Q2M9X", source: "MERCHANT", merchantId: "m-1", merchantOrderRef: "ORD-1",
    pickup: { city: "Colombo 03", district: "Colombo" }, dropoff: { city: "Kandy", district: "Kandy" },
    sizeClass: "S", weightKg: 2, serviceLevel: "STANDARD", fee: { amount: 650, currency: "LKR" },
    contacts: { recipientName: "Saman Perera", recipientPhone: "0712345678" }, createdAt: new Date().toISOString(),
  } as ParcelCreated, "parcel-service");

  async function waitFor<T>(check: () => Promise<T | undefined | false>, timeoutMs = 20_000): Promise<T> {
    const deadline = Date.now() + timeoutMs;
    for (;;) {
      const value = await check();
      if (value) return value;
      if (Date.now() > deadline) throw new Error("Timed out waiting for the delivery");
      await new Promise((resolve) => setTimeout(resolve, 100));
    }
  }

  it("delivers a signed parcel.created webhook without personal data", async () => {
    answer = 200;
    const { secret } = await services.endpoints.register("m-1", receiverUrl, ["parcel.created"]);
    await services.fanOut.onParcelCreated(parcelCreated("p-ok"));

    const page = await waitFor(async () => {
      const p = await services.log.forMerchant("m-1", "DELIVERED", 0, 10);
      return p.total > 0 && p;
    });
    const delivered = page.items[0];
    expect(delivered.attempts).toHaveLength(1);
    expect(delivered.attempts[0].httpStatus).toBe(200);
    const request = received.find((r) => r.body.includes(delivered.id))!;
    expect(verifySignature(request.signature, request.body, secret, Math.floor(Date.now() / 1000))).toBe(true);
    expect(JSON.parse(request.body)).toMatchObject({ type: "parcel.created", data: { merchantOrderRef: "ORD-1" } });
    expect(request.body).not.toContain("0712345678");
  });

  it("retries a failing endpoint, dead-letters it, and an admin replay delivers it after recovery", async () => {
    answer = 503;
    await services.endpoints.register("m-2", receiverUrl, ["parcel.created"]);
    await services.fanOut.onParcelCreated({ ...parcelCreated("p-fail"), data: { ...parcelCreated("p-fail").data, merchantId: "m-2" } });

    const dead = await waitFor(async () => (await services.log.forMerchant("m-2", "DEAD", 0, 10)).items[0]);
    expect(dead.attempts.map((a) => a.httpStatus)).toEqual([503, 503, 503]);
    expect(deadLetters).toEqual([expect.objectContaining({ deliveryId: dead.id, attempts: 3, lastHttpStatus: 503 })]);

    answer = 200;
    await services.log.replay(dead.id);
    const delivered = await waitFor(async () => {
      const d = await services.deliveries.findById(dead.id);
      return d?.status === "DELIVERED" && d;
    });
    expect(delivered.attempts.map((a) => a.httpStatus)).toEqual([503, 503, 503, 200]);
  });

  it("stores a redelivered event once and holds deliveries for a suspended merchant", async () => {
    answer = 200;
    await services.endpoints.register("m-3", receiverUrl, ["parcel.created"]);
    const merchants = new MongoMerchantStateRepository(db);
    await merchants.save("m-3", "SUSPENDED", new Date());
    await merchants.save("m-3", "APPROVED", new Date(0));
    expect(await merchants.isSuspended("m-3")).toBe(true);

    const event = { ...parcelCreated("p-held"), data: { ...parcelCreated("p-held").data, merchantId: "m-3" } };
    expect(await services.fanOut.onParcelCreated(event)).toBe(0);
    expect(await services.fanOut.onParcelCreated(event)).toBe(0);
    expect((await services.log.forMerchant("m-3", "PENDING", 0, 10)).total).toBe(1);

    await merchants.save("m-3", "APPROVED", new Date());
    expect(await services.fanOut.releaseHeld("m-3")).toBe(1);
    await waitFor(async () => (await services.log.forMerchant("m-3", "DELIVERED", 0, 10)).total === 1);
  });
});
