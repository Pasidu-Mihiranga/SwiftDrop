import "reflect-metadata";
import type { INestApplication } from "@nestjs/common";
import { Test } from "@nestjs/testing";
import { SwiftDropCommonModule } from "@swiftdrop/common/nest";
import { createLocalJWKSet, exportJWK, generateKeyPair, SignJWT } from "jose";
import request from "supertest";
import {
  DEAD_LETTERS, DELIVERIES, DELIVERY_QUEUE, ENDPOINTS, MERCHANT_STATES, PARCEL_REFS, SECRET_CIPHER, SENDER, WEBHOOK_SETTINGS,
} from "../../src/modules/webhooks/application/ports";
import { APPLICATION_SERVICES, CONTROLLERS } from "../../src/modules/webhooks/webhooks.module";
import { fakes, ScriptedSender, testSettings } from "./fakes";

const ISSUER = "http://localhost:8180/realms/swiftdrop";

describe("webhook HTTP API", () => {
  let app: INestApplication;
  let token: (sub: string, roles: string[], merchantId?: string) => Promise<string>;
  const f = fakes();

  beforeAll(async () => {
    const { privateKey, publicKey } = await generateKeyPair("RS256");
    const keys = createLocalJWKSet({ keys: [{ ...(await exportJWK(publicKey)), kid: "k", alg: "RS256" }] });
    token = (sub, roles, merchantId) => new SignJWT({ sub, realm_access: { roles }, ...(merchantId ? { merchant_id: merchantId } : {}) })
      .setProtectedHeader({ alg: "RS256", kid: "k" }).setIssuer(ISSUER).setAudience("swiftdrop-api").setExpirationTime("5m").sign(privateKey);

    const moduleRef = await Test.createTestingModule({
      imports: [SwiftDropCommonModule.forRoot({ service: "webhook-service", issuer: ISSUER, jwksUri: "http://unused", keys })],
      controllers: CONTROLLERS,
      providers: [
        ...APPLICATION_SERVICES,
        { provide: ENDPOINTS, useValue: f.endpoints },
        { provide: DELIVERIES, useValue: f.deliveries },
        { provide: MERCHANT_STATES, useValue: f.merchants },
        { provide: PARCEL_REFS, useValue: f.parcelRefs },
        { provide: DELIVERY_QUEUE, useValue: f.queue },
        { provide: DEAD_LETTERS, useValue: f.deadLetters },
        { provide: SECRET_CIPHER, useValue: f.cipher },
        { provide: SENDER, useValue: new ScriptedSender() },
        { provide: WEBHOOK_SETTINGS, useValue: testSettings() },
      ],
    }).compile();
    app = moduleRef.createNestApplication({ logger: false });
    await app.init();
  });

  afterAll(() => app.close());

  const http = () => request(app.getHttpServer());
  const merchantA = () => token("owner-a", ["MERCHANT"], "m-a");
  const merchantB = () => token("owner-b", ["MERCHANT"], "m-b");

  async function registerAsA(): Promise<{ id: string; secret: string }> {
    const res = await http().post("/api/v1/webhooks/endpoints").set("Authorization", `Bearer ${await merchantA()}`)
      .send({ url: "https://shop.example.com/hooks", eventTypes: ["parcel.created", "parcel.status_changed"] }).expect(201);
    return res.body;
  }

  it("registers an endpoint and returns the secret only in that answer", async () => {
    const res = await http().post("/api/v1/webhooks/endpoints").set("Authorization", `Bearer ${await merchantA()}`)
      .send({ url: "https://shop.example.com/hooks", eventTypes: ["parcel.created"] }).expect(201);
    expect(res.body).toMatchObject({ url: "https://shop.example.com/hooks", eventTypes: ["parcel.created"], active: true });
    expect(res.body.secret).toMatch(/^whsec_/);

    const list = await http().get("/api/v1/webhooks/endpoints").set("Authorization", `Bearer ${await merchantA()}`).expect(200);
    expect(list.body.length).toBeGreaterThan(0);
    expect(JSON.stringify(list.body)).not.toContain("whsec_");
    expect(JSON.stringify(list.body)).not.toContain("secretEnc");
  });

  it("rejects an invalid body and an internal URL with problem responses", async () => {
    const bad = await http().post("/api/v1/webhooks/endpoints").set("Authorization", `Bearer ${await merchantA()}`)
      .send({ url: "https://shop.example.com", eventTypes: ["parcel.deleted"], extra: 1 }).expect(400);
    expect(bad.body.code).toBe("VALIDATION_FAILED");
    const ssrf = await http().post("/api/v1/webhooks/endpoints").set("Authorization", `Bearer ${await merchantA()}`)
      .send({ url: "https://10.0.0.7/hooks", eventTypes: ["parcel.created"] }).expect(400);
    expect(ssrf.body.code).toBe("WEBHOOK_URL_REJECTED");
  });

  it("merchant B cannot see, test or delete merchant A's endpoint or deliveries", async () => {
    const { id } = await registerAsA();
    await http().post(`/api/v1/webhooks/endpoints/${id}/test`).set("Authorization", `Bearer ${await merchantA()}`).expect(202);

    const auth = `Bearer ${await merchantB()}`;
    expect((await http().get("/api/v1/webhooks/endpoints").set("Authorization", auth).expect(200)).body).toEqual([]);
    await http().post(`/api/v1/webhooks/endpoints/${id}/test`).set("Authorization", auth).expect(404);
    await http().delete(`/api/v1/webhooks/endpoints/${id}`).set("Authorization", auth).expect(404);
    expect((await http().get("/api/v1/webhooks/deliveries").set("Authorization", auth).expect(200)).body.page.totalElements).toBe(0);

    await http().delete(`/api/v1/webhooks/endpoints/${id}`).set("Authorization", `Bearer ${await merchantA()}`).expect(204);
  });

  it("shows the merchant's delivery log with paging and a status filter", async () => {
    const { id } = await registerAsA();
    const ping = await http().post(`/api/v1/webhooks/endpoints/${id}/test`).set("Authorization", `Bearer ${await merchantA()}`).expect(202);
    expect(ping.body).toEqual({ deliveryId: expect.stringMatching(/^whd_/), status: "QUEUED" });

    const res = await http().get("/api/v1/webhooks/deliveries?status=PENDING&size=5").set("Authorization", `Bearer ${await merchantA()}`).expect(200);
    expect(res.body.page).toMatchObject({ page: 0, size: 5 });
    expect(res.body.content[0]).toMatchObject({ status: "PENDING", eventType: "webhook.test", attemptCount: 0, nextRetryAt: null });
    await http().get("/api/v1/webhooks/deliveries?size=500").set("Authorization", `Bearer ${await merchantA()}`).expect(400);
  });

  it("only merchants manage endpoints, and a merchant user needs a merchant id", async () => {
    await http().get("/api/v1/webhooks/endpoints").expect(401);
    await http().get("/api/v1/webhooks/endpoints").set("Authorization", `Bearer ${await token("c-1", ["CUSTOMER"])}`).expect(403);
    await http().get("/api/v1/webhooks/endpoints").set("Authorization", `Bearer ${await token("admin-1", ["ADMIN"])}`).expect(403);
    const res = await http().get("/api/v1/webhooks/endpoints").set("Authorization", `Bearer ${await token("new", ["MERCHANT"])}`).expect(403);
    expect(res.body.code).toBe("MERCHANT_REQUIRED");
  });

  it("only admins list all deliveries and replay a dead one", async () => {
    const { id } = await registerAsA();
    const ping = await http().post(`/api/v1/webhooks/endpoints/${id}/test`).set("Authorization", `Bearer ${await merchantA()}`);
    const deliveryId: string = ping.body.deliveryId;
    await f.deliveries.update(deliveryId, { status: "DEAD", deadLetteredAt: new Date() });

    await http().post(`/api/v1/admin/webhooks/deliveries/${deliveryId}/replay`).set("Authorization", `Bearer ${await merchantA()}`).expect(403);
    await http().get("/api/v1/admin/webhooks/deliveries").set("Authorization", `Bearer ${await token("d-1", ["DISPATCHER"])}`).expect(403);

    const admin = `Bearer ${await token("admin-1", ["ADMIN"])}`;
    const dead = await http().get("/api/v1/admin/webhooks/deliveries?status=DEAD").set("Authorization", admin).expect(200);
    expect(dead.body.content.map((d: { id: string }) => d.id)).toContain(deliveryId);
    const replayed = await http().post(`/api/v1/admin/webhooks/deliveries/${deliveryId}/replay`).set("Authorization", admin).expect(202);
    expect(replayed.body.status).toBe("PENDING");
    const again = await http().post(`/api/v1/admin/webhooks/deliveries/${deliveryId}/replay`).set("Authorization", admin).expect(409);
    expect(again.body.code).toBe("DELIVERY_NOT_DEAD");
  });
});
