import "reflect-metadata";
import type { INestApplication } from "@nestjs/common";
import { Test } from "@nestjs/testing";
import { createJwtVerifier, type Logger } from "@swiftdrop/common";
import { JWT_VERIFIER, LOGGER } from "@swiftdrop/common/nest";
import { createLocalJWKSet, exportJWK, generateKeyPair, SignJWT } from "jose";
import { io, type Socket } from "socket.io-client";
import { REDIS } from "../../src/infrastructure/redis.module";
import type { Timeline } from "../../src/modules/timeline/domain/timeline";
import { REALTIME_ADAPTER, TrackingGateway } from "../../src/modules/realtime/tracking.gateway";
import { canJoin, roomsFor, rooms } from "../../src/modules/realtime/domain/rooms";

const ISSUER = "http://localhost:8180/realms/swiftdrop";
const TRACKING = "SD261020K7Q2M9X";
const PARCEL = "0b6f4a9e-7c1d-4e2f-9a3b-5c6d7e8f9a0b";
const CUSTOMER = "11111111-0000-4000-8000-000000000001";
const OTHER_CUSTOMER = "22222222-0000-4000-8000-000000000002";
const MERCHANT = "00000000-0000-4000-8000-000000000001";

const timeline: Timeline = {
  trackingNumber: TRACKING,
  parcelId: PARCEL,
  ownerIds: [CUSTOMER],
  customerId: CUSTOMER,
  courierId: null,
  merchantId: MERCHANT,
  fromCity: "Colombo",
  toCity: "Kandy",
  recipientMasked: "K*** P.",
  currentStatus: "OUT_FOR_DELIVERY",
  lastChangedAt: "2026-10-20T14:02:00Z",
  events: [{ eventId: "e9", status: "OUT_FOR_DELIVERY", city: "Kandy", at: "2026-10-20T14:02:00Z" }],
  appliedEventIds: ["e9"],
  version: 3,
};

const silentLogger = { info: () => {}, warn: () => {}, error: () => {}, debug: () => {}, child: () => silentLogger } as unknown as Logger;

const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

describe("room rules (pure)", () => {
  const user = (over: Partial<{ id: string; roles: string[]; merchantId: string }>) => ({ id: "x", roles: [], ...over }) as never;

  it("lets anyone join a well-formed public tracking room only", () => {
    expect(canJoin(`tracking:${TRACKING}`)).toBe(true);
    expect(canJoin("tracking:../../etc")).toBe(false);
    expect(canJoin(`tracking:${TRACKING}x`)).toBe(false);
  });

  it("allows a user room only for its own sub", () => {
    expect(canJoin(`user:${CUSTOMER}`, user({ id: CUSTOMER }))).toBe(true);
    expect(canJoin(`user:${CUSTOMER}`, user({ id: OTHER_CUSTOMER }))).toBe(false);
    expect(canJoin(`user:${CUSTOMER}`)).toBe(false);
  });

  it("allows a merchant room only with the matching merchant_id claim", () => {
    expect(canJoin(`merchant:${MERCHANT}`, user({ merchantId: MERCHANT }))).toBe(true);
    expect(canJoin(`merchant:${MERCHANT}`, user({ merchantId: "99999999-0000-4000-8000-000000000009" }))).toBe(false);
    expect(canJoin(`merchant:${MERCHANT}`, user({}))).toBe(false);
  });

  it("allows dispatch to dispatchers and admins only", () => {
    expect(canJoin("dispatch", user({ roles: ["DISPATCHER"] }))).toBe(true);
    expect(canJoin("dispatch", user({ roles: ["ADMIN"] }))).toBe(true);
    expect(canJoin("dispatch", user({ roles: ["CUSTOMER"] }))).toBe(false);
    expect(canJoin("dispatch")).toBe(false);
  });

  it("sends a change to the tracking, owner, merchant and dispatch rooms", () => {
    expect(roomsFor(timeline)).toEqual([rooms.tracking(TRACKING), rooms.user(CUSTOMER), rooms.dispatch, rooms.merchant(MERCHANT)]);
  });
});

describe("tracking Socket.IO gateway", () => {
  let app: INestApplication;
  let gateway: TrackingGateway;
  let url: string;
  let sign: (claims: Record<string, unknown>) => Promise<string>;
  const sockets: Socket[] = [];

  beforeAll(async () => {
    const { privateKey, publicKey } = await generateKeyPair("RS256");
    const keys = createLocalJWKSet({ keys: [{ ...(await exportJWK(publicKey)), kid: "k", alg: "RS256" }] });
    sign = (claims) =>
      new SignJWT(claims).setProtectedHeader({ alg: "RS256", kid: "k" }).setIssuer(ISSUER).setAudience("swiftdrop-api")
        .setExpirationTime("5m").sign(privateKey);
    const verifier = createJwtVerifier({ issuer: ISSUER, audience: "swiftdrop-api", keys });

    const moduleRef = await Test.createTestingModule({
      providers: [
        TrackingGateway,
        { provide: JWT_VERIFIER, useValue: verifier },
        { provide: REDIS, useValue: {} },
        { provide: LOGGER, useValue: silentLogger },
        // In-process test: no Redis adapter (its cross-replica behaviour is covered by the F6 integration test).
        { provide: REALTIME_ADAPTER, useValue: false },
      ],
    }).compile();
    app = moduleRef.createNestApplication({ logger: false });
    await app.listen(0, "127.0.0.1");
    gateway = app.get(TrackingGateway);
    const address = app.getHttpServer().address() as { port: number };
    url = `http://127.0.0.1:${address.port}`;
  });

  afterAll(async () => {
    sockets.forEach((s) => s.disconnect());
    await app.close();
  });

  /** Connects with an optional token; resolves once connected, rejects on a refused handshake. */
  function connect(token?: string): Promise<Socket> {
    const socket = io(url, {
      path: "/ws/tracking",
      transports: ["websocket"],
      auth: token ? { token } : {},
      reconnection: false,
      forceNew: true,
    });
    sockets.push(socket);
    return new Promise((resolve, reject) => {
      socket.once("connect", () => resolve(socket));
      socket.once("connect_error", (error) => reject(error));
    });
  }

  const join = (socket: Socket, room: string) => socket.emitWithAck("join", { room }) as Promise<{ ok: boolean; error?: string }>;

  it("refuses a handshake with an invalid token", async () => {
    await expect(connect("not-a-token")).rejects.toThrow("UNAUTHORIZED");
  });

  it("lets an anonymous socket join a public tracking room only", async () => {
    const anon = await connect();
    expect(await join(anon, `tracking:${TRACKING}`)).toEqual({ ok: true });
    expect(await join(anon, `user:${CUSTOMER}`)).toEqual({ ok: false, error: "forbidden" });
    expect(await join(anon, "dispatch")).toEqual({ ok: false, error: "forbidden" });
  });

  it("lets an owner join its own user room but not another user's room", async () => {
    const owner = await connect(await sign({ sub: CUSTOMER, realm_access: { roles: ["CUSTOMER"] } }));
    expect(await join(owner, `user:${CUSTOMER}`)).toEqual({ ok: true });
    expect(await join(owner, `user:${OTHER_CUSTOMER}`)).toEqual({ ok: false, error: "forbidden" });
    expect(await join(owner, "dispatch")).toEqual({ ok: false, error: "forbidden" });
  });

  it("lets a dispatcher join the dispatch room", async () => {
    const dispatcher = await connect(await sign({ sub: "disp-1", realm_access: { roles: ["DISPATCHER"] } }));
    expect(await join(dispatcher, "dispatch")).toEqual({ ok: true });
  });

  it("checks the merchant_id claim before a merchant joins its room", async () => {
    const own = await connect(await sign({ sub: "m-user", merchant_id: MERCHANT, realm_access: { roles: ["MERCHANT"] } }));
    expect(await join(own, `merchant:${MERCHANT}`)).toEqual({ ok: true });
    const stranger = await connect(await sign({ sub: "m-other", merchant_id: "99999999-0000-4000-8000-000000000009", realm_access: { roles: ["MERCHANT"] } }));
    expect(await join(stranger, `merchant:${MERCHANT}`)).toEqual({ ok: false, error: "forbidden" });
  });

  it("pushes the masked view to the rooms that should hear it, and to nobody else", async () => {
    const owner = await connect(await sign({ sub: CUSTOMER, realm_access: { roles: ["CUSTOMER"] } }));
    const anon = await connect();
    const stranger = await connect(await sign({ sub: OTHER_CUSTOMER, realm_access: { roles: ["CUSTOMER"] } }));
    await join(owner, `user:${CUSTOMER}`);
    await join(anon, `tracking:${TRACKING}`);
    await join(stranger, `user:${OTHER_CUSTOMER}`);

    const received = (socket: Socket) =>
      new Promise<unknown>((resolve) => {
        socket.once("parcel.status", resolve);
        setTimeout(() => resolve(null), 1500);
      });
    const ownerPayload = received(owner);
    const anonPayload = received(anon);
    const strangerPayload = received(stranger);

    await gateway.timelineChanged(timeline);
    await wait(50);

    const body = (await ownerPayload) as Record<string, unknown>;
    expect(body).toEqual({
      trackingNumber: TRACKING,
      currentStatus: "OUT_FOR_DELIVERY",
      from: "Colombo",
      to: "Kandy",
      recipient: "K*** P.",
      events: [{ status: "OUT_FOR_DELIVERY", city: "Kandy", at: "2026-10-20T14:02:00Z" }],
    });
    expect(await anonPayload).toEqual(body);
    expect(await strangerPayload).toBeNull();
    expect(JSON.stringify(body)).not.toMatch(/\d{9,}/);
  });
});
