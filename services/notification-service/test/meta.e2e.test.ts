import "reflect-metadata";
import type { INestApplication } from "@nestjs/common";
import { Test } from "@nestjs/testing";
import { SwiftDropCommonModule } from "@swiftdrop/common/nest";
import { createLocalJWKSet, exportJWK, generateKeyPair, SignJWT } from "jose";
import request from "supertest";
import { loadEnv } from "../src/config/env";
import { MetaModule } from "../src/modules/meta/meta.module";

const ISSUER = "http://localhost:8180/realms/swiftdrop";

describe("notification-service meta endpoint", () => {
  let app: INestApplication;
  let sign: (claims: Record<string, unknown>) => Promise<string>;

  beforeAll(async () => {
    const { privateKey, publicKey } = await generateKeyPair("RS256");
    const keys = createLocalJWKSet({ keys: [{ ...(await exportJWK(publicKey)), kid: "k", alg: "RS256" }] });
    sign = (claims) =>
      new SignJWT(claims).setProtectedHeader({ alg: "RS256", kid: "k" }).setIssuer(ISSUER)
        .setAudience("swiftdrop-api").setExpirationTime("5m").sign(privateKey);

    const moduleRef = await Test.createTestingModule({
      imports: [SwiftDropCommonModule.forRoot({ service: "notification-service", issuer: ISSUER, jwksUri: "http://unused", keys }), MetaModule],
    }).compile();
    app = moduleRef.createNestApplication({ logger: false });
    await app.init();
  });

  afterAll(() => app.close());

  it("returns the caller from the token", async () => {
    const token = await sign({ sub: "user-1", realm_access: { roles: ["CUSTOMER"] } });
    const res = await request(app.getHttpServer()).get("/api/v1/notifications/meta/whoami").set("Authorization", `Bearer ${token}`).expect(200);
    expect(res.body).toEqual({ service: "notification-service", userId: "user-1", roles: ["CUSTOMER"], merchantId: null });
  });

  it("rejects anonymous callers with a problem response", async () => {
    const res = await request(app.getHttpServer()).get("/api/v1/notifications/meta/whoami").expect(401);
    expect(res.body.code).toBe("UNAUTHORIZED");
  });

  it("exposes liveness without a token", async () => {
    await request(app.getHttpServer()).get("/health/live").expect(200);
  });
});

describe("configuration", () => {
  it("applies defaults and rejects malformed values", () => {
    expect(loadEnv({}).PORT).toBe(8091);
    expect(() => loadEnv({ KEYCLOAK_ISSUER: "not a url" })).toThrow();
  });
});
