import "reflect-metadata";
import type { INestApplication } from "@nestjs/common";
import { Test } from "@nestjs/testing";
import { SwiftDropCommonModule } from "@swiftdrop/common/nest";
import { createLocalJWKSet, exportJWK, generateKeyPair, SignJWT } from "jose";
import request from "supertest";
import { canViewTimeline } from "../../src/modules/tracking/domain/access-policy";
import { RateLimitGuard } from "../../src/modules/tracking/rate-limit.guard";
import { TrackingController } from "../../src/modules/tracking/tracking.controller";
import { TrackingQueryService } from "../../src/modules/tracking/tracking.query.service";
import { REDIS } from "../../src/infrastructure/redis.module";
import type { Timeline } from "../../src/modules/timeline/domain/timeline";
import { TIMELINE_REPOSITORY } from "../../src/modules/timeline/timeline.repository.port";

const ISSUER = "http://localhost:8180/realms/swiftdrop";
const TRACKING = "SD261020K7Q2M9X";
const PARCEL = "0b6f4a9e-7c1d-4e2f-9a3b-5c6d7e8f9a0b";
const CUSTOMER = "11111111-0000-4000-8000-000000000001";
const COURIER = "a1b2c3d4-0000-4000-8000-00000000c001";
const OTHER = "22222222-0000-4000-8000-000000000002";
const MERCHANT = "00000000-0000-4000-8000-000000000001";

const timeline: Timeline = {
  trackingNumber: TRACKING,
  parcelId: PARCEL,
  ownerIds: [CUSTOMER, COURIER],
  customerId: CUSTOMER,
  courierId: COURIER,
  merchantId: MERCHANT,
  fromCity: "Colombo",
  toCity: "Kandy",
  recipientMasked: "K*** P.",
  currentStatus: "PICKED_UP",
  lastChangedAt: "2026-10-20T09:00:00Z",
  events: [
    { eventId: "e1", status: "CREATED", city: "Colombo", at: "2026-10-20T08:00:00Z" },
    { eventId: "e2", status: "PICKED_UP", city: "Colombo", at: "2026-10-20T09:00:00Z" },
  ],
  appliedEventIds: ["e1", "e2"],
  version: 2,
};

/** Fake Redis with a plain counter store; `failing` simulates Redis being down. */
function fakeRedis(failing = false) {
  const counts = new Map<string, number>();
  return {
    counts,
    incr: jest.fn(async (key: string) => {
      if (failing) throw new Error("connection refused");
      const next = (counts.get(key) ?? 0) + 1;
      counts.set(key, next);
      return next;
    }),
    expire: jest.fn(async () => 1),
  };
}

describe("tracking REST API", () => {
  let app: INestApplication;
  let sign: (claims: Record<string, unknown>) => Promise<string>;
  let redis: ReturnType<typeof fakeRedis>;

  /** Builds an app with its own signing key; each app verifies only tokens from its own key. */
  async function build(redisClient: ReturnType<typeof fakeRedis>) {
    const { privateKey, publicKey } = await generateKeyPair("RS256");
    const keys = createLocalJWKSet({ keys: [{ ...(await exportJWK(publicKey)), kid: "k", alg: "RS256" }] });
    const signer = (claims: Record<string, unknown>) =>
      new SignJWT(claims).setProtectedHeader({ alg: "RS256", kid: "k" }).setIssuer(ISSUER).setAudience("swiftdrop-api")
        .setExpirationTime("5m").sign(privateKey);
    const repository = {
      findByTrackingNumber: async (no: string) => (no === TRACKING ? timeline : null),
      findByParcelId: async (id: string) => (id === PARCEL ? timeline : null),
      save: async () => undefined,
    };
    const moduleRef = await Test.createTestingModule({
      imports: [SwiftDropCommonModule.forRoot({ service: "tracking-service", issuer: ISSUER, jwksUri: "http://unused", keys })],
      controllers: [TrackingController],
      providers: [
        TrackingQueryService,
        RateLimitGuard,
        { provide: TIMELINE_REPOSITORY, useValue: repository },
        { provide: REDIS, useValue: redisClient },
      ],
    }).compile();
    const instance = moduleRef.createNestApplication({ logger: false });
    await instance.init();
    return { instance, sign: signer };
  }

  beforeAll(async () => {
    redis = fakeRedis();
    ({ instance: app, sign } = await build(redis));
  });

  afterAll(() => app.close());

  describe("public lookup", () => {
    it("returns the masked view with no phone number or street address", async () => {
      const res = await request(app.getHttpServer()).get(`/api/v1/tracking/public/${TRACKING}`).expect(200);
      expect(res.body).toEqual({
        trackingNumber: TRACKING,
        currentStatus: "PICKED_UP",
        from: "Colombo",
        to: "Kandy",
        recipient: "K*** P.",
        events: [
          { status: "CREATED", city: "Colombo", at: "2026-10-20T08:00:00Z" },
          { status: "PICKED_UP", city: "Colombo", at: "2026-10-20T09:00:00Z" },
        ],
      });
      expect(JSON.stringify(res.body)).not.toMatch(/07\d{8}/);
    });

    it("answers unknown and malformed numbers the same way (404, no enumeration hints)", async () => {
      await request(app.getHttpServer()).get("/api/v1/tracking/public/SD000000000000").expect(404);
      const res = await request(app.getHttpServer()).get("/api/v1/tracking/public/not-a-number").expect(404);
      expect(res.body.code).toBe("NOT_FOUND");
    });

    it("needs no token", async () => {
      await request(app.getHttpServer()).get(`/api/v1/tracking/public/${TRACKING}`).expect(200);
    });

    it("answers 429 with Retry-After once the per-IP limit is reached", async () => {
      const { instance: limited } = await build(fakeRedis());
      try {
        const agent = request(limited.getHttpServer());
        for (let i = 0; i < 60; i++) {
          await agent.get(`/api/v1/tracking/public/${TRACKING}`).set("X-Forwarded-For", "203.0.113.9").expect(200);
        }
        const res = await agent.get(`/api/v1/tracking/public/${TRACKING}`).set("X-Forwarded-For", "203.0.113.9").expect(429);
        expect(res.headers["retry-after"]).toBe("60");
        expect(res.body.code).toBe("RATE_LIMITED");
        // another client address still has its own budget
        await agent.get(`/api/v1/tracking/public/${TRACKING}`).set("X-Forwarded-For", "198.51.100.7").expect(200);
      } finally {
        await limited.close();
      }
    });

    it("allows the lookup when Redis is down (fail open)", async () => {
      const { instance: down } = await build(fakeRedis(true));
      try {
        await request(down.getHttpServer()).get(`/api/v1/tracking/public/${TRACKING}`).expect(200);
      } finally {
        await down.close();
      }
    });
  });

  describe("metrics", () => {
    it("exposes the tracking counters on /metrics", async () => {
      const res = await request(app.getHttpServer()).get("/metrics").expect(200);
      expect(res.text).toContain("tracking_public_rate_limited_total");
      expect(res.text).toContain("tracking_socket_connections");
    });
  });

  describe("owner timeline", () => {
    const url = `/api/v1/tracking/parcels/${PARCEL}/timeline`;

    it("rejects an anonymous caller with 401", async () => {
      await request(app.getHttpServer()).get(url).expect(401);
    });

    it("allows the customer and the assigned courier", async () => {
      await request(app.getHttpServer()).get(url).set("Authorization", `Bearer ${await sign({ sub: CUSTOMER, realm_access: { roles: ["CUSTOMER"] } })}`).expect(200);
      await request(app.getHttpServer()).get(url).set("Authorization", `Bearer ${await sign({ sub: COURIER, realm_access: { roles: ["COURIER"] } })}`).expect(200);
    });

    it("allows the merchant that sent the parcel (merchant_id claim)", async () => {
      const token = await sign({ sub: "merchant-user", merchant_id: MERCHANT, realm_access: { roles: ["MERCHANT"] } });
      await request(app.getHttpServer()).get(url).set("Authorization", `Bearer ${token}`).expect(200);
    });

    it("allows dispatchers and admins", async () => {
      await request(app.getHttpServer()).get(url).set("Authorization", `Bearer ${await sign({ sub: "disp-1", realm_access: { roles: ["DISPATCHER"] } })}`).expect(200);
    });

    it("answers another customer with 404, not 403, so existence is not revealed", async () => {
      const res = await request(app.getHttpServer()).get(url).set("Authorization", `Bearer ${await sign({ sub: OTHER, realm_access: { roles: ["CUSTOMER"] } })}`).expect(404);
      expect(res.body.code).toBe("NOT_FOUND");
    });

    it("answers a different merchant with 404", async () => {
      const token = await sign({ sub: "other-merchant", merchant_id: "99999999-0000-4000-8000-000000000009", realm_access: { roles: ["MERCHANT"] } });
      await request(app.getHttpServer()).get(url).set("Authorization", `Bearer ${token}`).expect(404);
    });

    it("answers a malformed parcel id with 404", async () => {
      await request(app.getHttpServer()).get("/api/v1/tracking/parcels/not-a-uuid/timeline").set("Authorization", `Bearer ${await sign({ sub: CUSTOMER, realm_access: { roles: ["CUSTOMER"] } })}`).expect(404);
    });
  });
});

describe("canViewTimeline", () => {
  const user = (over: Partial<{ id: string; roles: string[]; merchantId: string }>) => ({
    id: OTHER,
    roles: [] as string[],
    ...over,
  }) as never;

  it("is false for a stranger with no role or merchant claim", () => {
    expect(canViewTimeline(timeline, user({}))).toBe(false);
  });

  it("does not match a merchant id that is absent on the timeline", () => {
    expect(canViewTimeline({ ...timeline, merchantId: null }, user({ merchantId: MERCHANT }))).toBe(false);
  });
});
