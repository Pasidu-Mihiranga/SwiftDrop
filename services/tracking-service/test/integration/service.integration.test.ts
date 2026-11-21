import "reflect-metadata";
import type { INestApplication } from "@nestjs/common";
import { Test } from "@nestjs/testing";
import {
  contractExample,
  createJwtVerifier,
  createTopicsWhenReady,
  type EventEnvelope,
  Topics,
  publishEvent,
} from "@swiftdrop/common";
import { SwiftDropCommonModule, JWT_VERIFIER } from "@swiftdrop/common/nest";
import { type StartedKafkaContainer, KafkaContainer } from "@testcontainers/kafka";
import { type StartedMongoDBContainer, MongoDBContainer } from "@testcontainers/mongodb";
import { type StartedRedisContainer, RedisContainer } from "@testcontainers/redis";
import { createLocalJWKSet, exportJWK, generateKeyPair, SignJWT } from "jose";
import { Kafka, type Consumer, type Producer, logLevel } from "kafkajs";
import { TIMELINE_REPOSITORY, type TimelineRepositoryPort } from "../../src/modules/timeline/timeline.repository.port";
import request from "supertest";
import { io, type Socket } from "socket.io-client";

// Real Kafka, MongoDB and Redis containers (Docker must be running). Two replicas share one Redis adapter.
jest.setTimeout(420_000);

const ISSUER = "http://localhost:8180/realms/swiftdrop";
const TRACKING = "SD261020K7Q2M9X";
const PARCEL = "0b6f4a9e-7c1d-4e2f-9a3b-5c6d7e8f9a0b";
const CUSTOMER = "11111111-0000-4000-8000-000000000001";
const STRANGER = "22222222-0000-4000-8000-000000000002";

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

async function waitFor<T>(read: () => Promise<T | null | undefined>, timeoutMs = 60_000): Promise<T> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const value = await read();
    if (value) return value;
    await sleep(500);
  }
  throw new Error("condition not met in time");
}

/** Contract example with a fresh eventId, bound to the test parcel. */
function event(
  contract: "parcel-created.v1" | "parcel-status-changed.v1",
  eventType: string,
  data: Record<string, unknown> = {},
  eventId: string = crypto.randomUUID(),
): EventEnvelope<unknown> {
  const base = contractExample(contract) as EventEnvelope<Record<string, unknown>>;
  return {
    ...base,
    eventType,
    eventId,
    data: { ...base.data, parcelId: PARCEL, trackingNumber: TRACKING, customerId: CUSTOMER, ...data },
  };
}

describe("tracking-service on real Kafka, MongoDB and Redis (two replicas)", () => {
  let kafkaContainer: StartedKafkaContainer;
  let mongoContainer: StartedMongoDBContainer;
  let redisContainer: StartedRedisContainer;
  let replicaA: INestApplication; // the consuming replica (runs the Kafka consumer)
  let replicaB: INestApplication; // serves sockets and REST, no consumer
  let urlA: string;
  let urlB: string;
  let sign: (claims: Record<string, unknown>) => Promise<string>;
  let producer: Producer;
  let deadLetterConsumer: Consumer;
  const deadLetterErrors: string[] = [];
  const sockets: Socket[] = [];

  beforeAll(async () => {
    [kafkaContainer, mongoContainer, redisContainer] = await Promise.all([
      new KafkaContainer("confluentinc/cp-kafka:7.6.1").start(),
      new MongoDBContainer("mongo:7").start(),
      new RedisContainer("redis:7").start(),
    ]);

    // Environment is read when the config module loads, so set it before importing the app.
    const mongo = new URL(mongoContainer.getConnectionString());
    mongo.searchParams.set("directConnection", "true");
    process.env.MONGO_URI = mongo.toString();
    process.env.REDIS_URL = redisContainer.getConnectionUrl();
    process.env.KAFKA_BROKERS = `${kafkaContainer.getHost()}:${kafkaContainer.getMappedPort(9093)}`;
    process.env.KEYCLOAK_ISSUER = ISSUER;
    process.env.KAFKA_CONSUMER_ENABLED = "true";

    const { privateKey, publicKey } = await generateKeyPair("RS256");
    const keys = createLocalJWKSet({ keys: [{ ...(await exportJWK(publicKey)), kid: "k", alg: "RS256" }] });
    sign = (claims) =>
      new SignJWT(claims).setProtectedHeader({ alg: "RS256", kid: "k" }).setIssuer(ISSUER).setAudience("swiftdrop-api")
        .setExpirationTime("5m").sign(privateKey);
    const verifier = createJwtVerifier({ issuer: ISSUER, audience: "swiftdrop-api", keys });

    const kafka = new Kafka({ clientId: "service-test", brokers: [process.env.KAFKA_BROKERS], logLevel: logLevel.NOTHING });
    const admin = kafka.admin();
    await admin.connect();
    await createTopicsWhenReady(admin, { topics: [{ topic: Topics.TRACKING_DEAD_LETTER }] });
    await admin.disconnect();

    // Replica A: the real application module, consumer included. Only the token verifier is swapped for the local key.
    const { AppModule } = require("../../src/app.module") as typeof import("../../src/app.module");
    const moduleA = await Test.createTestingModule({ imports: [AppModule] })
      .overrideProvider(JWT_VERIFIER)
      .useValue(verifier)
      .compile();
    replicaA = moduleA.createNestApplication({ logger: false });
    await replicaA.listen(0, "127.0.0.1");
    urlA = `http://127.0.0.1:${(replicaA.getHttpServer().address() as { port: number }).port}`;

    // Replica B: the same serving stack without the Kafka consumer.
    const { RedisModule } = require("../../src/infrastructure/redis.module") as typeof import("../../src/infrastructure/redis.module");
    const { MongoModule } = require("../../src/infrastructure/mongo.module") as typeof import("../../src/infrastructure/mongo.module");
    const { TimelineModule } = require("../../src/modules/timeline/timeline.module") as typeof import("../../src/modules/timeline/timeline.module");
    const { TrackingModule } = require("../../src/modules/tracking/tracking.module") as typeof import("../../src/modules/tracking/tracking.module");
    const { RealtimeModule } = require("../../src/modules/realtime/realtime.module") as typeof import("../../src/modules/realtime/realtime.module");
    const { MetaModule } = require("../../src/modules/meta/meta.module") as typeof import("../../src/modules/meta/meta.module");
    const moduleB = await Test.createTestingModule({
      imports: [
        SwiftDropCommonModule.forRoot({ service: "tracking-service", issuer: ISSUER, jwksUri: "http://unused", keys }),
        MongoModule,
        RedisModule,
        TimelineModule,
        TrackingModule,
        RealtimeModule,
        MetaModule,
      ],
    }).compile();
    replicaB = moduleB.createNestApplication({ logger: false });
    await replicaB.listen(0, "127.0.0.1");
    urlB = `http://127.0.0.1:${(replicaB.getHttpServer().address() as { port: number }).port}`;

    producer = kafka.producer({ idempotent: true });
    await producer.connect();

    deadLetterConsumer = kafka.consumer({ groupId: "service-dead-letter-test" });
    await deadLetterConsumer.connect();
    await deadLetterConsumer.subscribe({ topic: Topics.TRACKING_DEAD_LETTER, fromBeginning: true });
    void deadLetterConsumer.run({
      eachMessage: async ({ message }) => {
        deadLetterErrors.push(message.headers?.["x-error"]?.toString() ?? "");
      },
    });
    // Give replica A's consumer time to join its group before the first event is published.
    await sleep(10_000);
  });

  afterAll(async () => {
    sockets.forEach((socket) => socket.disconnect());
    await deadLetterConsumer?.disconnect();
    await producer?.disconnect();
    await replicaA?.close();
    await replicaB?.close();
    await mongoContainer?.stop();
    await redisContainer?.stop();
    await kafkaContainer?.stop();
  });

  function connect(url: string, token?: string): Promise<Socket> {
    const socket = io(url, { path: "/ws/tracking", transports: ["websocket"], auth: token ? { token } : {}, reconnection: false, forceNew: true });
    sockets.push(socket);
    return new Promise((resolve, reject) => {
      socket.once("connect", () => resolve(socket));
      socket.once("connect_error", reject);
    });
  }

  it("projects created and status events into a masked public view on replica A", async () => {
    await publishEvent(producer, Topics.PARCEL_CREATED, PARCEL, event("parcel-created.v1", "ParcelCreated"));
    await waitFor(async () => {
      const res = await request(replicaA.getHttpServer()).get(`/api/v1/tracking/public/${TRACKING}`);
      return res.status === 200 ? res.body : null;
    });

    await publishEvent(producer, Topics.PARCEL_STATUS_CHANGED, PARCEL, event("parcel-status-changed.v1", "ParcelStatusChanged", { toStatus: "PICKED_UP" }));
    const body = await waitFor(async () => {
      const res = await request(replicaA.getHttpServer()).get(`/api/v1/tracking/public/${TRACKING}`);
      return res.body?.currentStatus === "PICKED_UP" ? res.body : null;
    });
    expect(body.recipient).toMatch(/^[A-Z]\*\*\* [A-Z]\.$/);
    expect(JSON.stringify(body)).not.toMatch(/0\d{9}|\+\d{9,}/);
  });

  it("delivers a live update to a client on the other replica through the Redis adapter", async () => {
    const watcher = await connect(urlB); // anonymous, on replica B
    expect(await watcher.emitWithAck("join", { room: `tracking:${TRACKING}` })).toEqual({ ok: true });

    const received = new Promise<Record<string, unknown>>((resolve, reject) => {
      watcher.once("parcel.status", resolve);
      setTimeout(() => reject(new Error("no live update received on replica B")), 30_000);
    });
    await publishEvent(producer, Topics.PARCEL_STATUS_CHANGED, PARCEL, event("parcel-status-changed.v1", "ParcelStatusChanged", { toStatus: "OUT_FOR_DELIVERY" }));

    const payload = await received;
    expect(payload.trackingNumber).toBe(TRACKING);
    expect(payload.currentStatus).toBe("OUT_FOR_DELIVERY");
    expect(JSON.stringify(payload)).not.toMatch(/0\d{9}|\+\d{9,}/);
  });

  it("ignores a redelivered event: the stored timeline does not change", async () => {
    const repository = replicaA.get<TimelineRepositoryPort>(TIMELINE_REPOSITORY, { strict: false });
    const dup = event("parcel-status-changed.v1", "ParcelStatusChanged", { toStatus: "IN_TRANSIT" }, "9c3f2a10-6b1e-4d7a-8f25-1e0b7c9d4a61");
    await publishEvent(producer, Topics.PARCEL_STATUS_CHANGED, PARCEL, dup);
    await waitFor(async () => ((await repository.findByTrackingNumber(TRACKING))?.version ?? 0) > 0 ? true : null);
    await waitFor(async () => (await request(replicaA.getHttpServer()).get(`/api/v1/tracking/public/${TRACKING}`)).body?.currentStatus === "IN_TRANSIT");
    const before = (await repository.findByTrackingNumber(TRACKING))!.version;

    await publishEvent(producer, Topics.PARCEL_STATUS_CHANGED, PARCEL, dup);
    await sleep(3000);
    expect((await repository.findByTrackingNumber(TRACKING))!.version).toBe(before);
  });

  it("places a late status change by time in the public view", async () => {
    await publishEvent(producer, Topics.PARCEL_STATUS_CHANGED, PARCEL, event("parcel-status-changed.v1", "ParcelStatusChanged", { toStatus: "DELIVERED", changedAt: "2031-01-02T10:00:00Z" }));
    await waitFor(async () => (await request(replicaA.getHttpServer()).get(`/api/v1/tracking/public/${TRACKING}`)).body?.currentStatus === "DELIVERED");
    await publishEvent(producer, Topics.PARCEL_STATUS_CHANGED, PARCEL, event("parcel-status-changed.v1", "ParcelStatusChanged", { toStatus: "FAILED", failureReason: "REFUSED", changedAt: "2031-01-01T10:00:00Z" }));
    await sleep(3000);
    const res = await request(replicaA.getHttpServer()).get(`/api/v1/tracking/public/${TRACKING}`).expect(200);
    expect(res.body.currentStatus).toBe("DELIVERED");
    const times = res.body.events.map((e: { at: string }) => e.at);
    expect(times).toEqual([...times].sort());
  });

  it("sends a contract-breaking event to the dead-letter topic with the reason", async () => {
    await publishEvent(producer, Topics.PARCEL_STATUS_CHANGED, PARCEL, event("parcel-status-changed.v1", "ParcelStatusChanged", { parcelId: "not-a-uuid" }));
    await waitFor(async () => (deadLetterErrors.some((e) => e.includes("Invalid parcel-status-changed.v1")) ? true : null), 120_000);
  });

  it("applies the owner rules on the real app: the customer reads the timeline, a stranger gets 404", async () => {
    const owner = await sign({ sub: CUSTOMER, realm_access: { roles: ["CUSTOMER"] } });
    const stranger = await sign({ sub: STRANGER, realm_access: { roles: ["CUSTOMER"] } });
    const url = `/api/v1/tracking/parcels/${PARCEL}/timeline`;
    await request(replicaA.getHttpServer()).get(url).set("Authorization", `Bearer ${owner}`).expect(200);
    await request(replicaA.getHttpServer()).get(url).set("Authorization", `Bearer ${stranger}`).expect(404);
    await request(replicaA.getHttpServer()).get(url).expect(401);
  });
});
