import "reflect-metadata";
import { Controller, Get, type INestApplication, NotFoundException } from "@nestjs/common";
import { Test } from "@nestjs/testing";
import request from "supertest";
import { ApiError, type AuthenticatedUser } from "../src";
import { CurrentUser, PinoNestLogger, Public, RequireRoles, SwiftDropCommonModule } from "../src/nest";
import { ISSUER, testKeys, type TestKeys } from "./support/tokens";

@Controller("api/v1/test")
class TestController {
  @Get("public")
  @Public()
  open() {
    return { ok: true };
  }

  @Get("me")
  me(@CurrentUser() user: AuthenticatedUser) {
    return user;
  }

  @Get("courier-only")
  @RequireRoles("COURIER")
  courierOnly() {
    return { ok: true };
  }

  @Get("missing")
  missing() {
    throw ApiError.notFound("Parcel", "42");
  }

  @Get("nest-404")
  @Public()
  nest404() {
    throw new NotFoundException();
  }

  @Get("boom")
  @Public()
  boom() {
    throw new Error("db password=hunter2 for 0771234567");
  }
}

describe("SwiftDropCommonModule", () => {
  let app: INestApplication;
  let keys: TestKeys;

  beforeAll(async () => {
    keys = await testKeys();
    const moduleRef = await Test.createTestingModule({
      imports: [SwiftDropCommonModule.forRoot({ service: "test-service", issuer: ISSUER, jwksUri: "http://unused", keys: keys.keys })],
      controllers: [TestController],
    }).compile();
    app = moduleRef.createNestApplication({ logger: false });
    await app.init();
  });

  afterAll(() => app.close());

  const tokenFor = (roles: string[]) => keys.sign({ sub: "user-7", realm_access: { roles } });

  it("rejects missing tokens with a problem+json 401", async () => {
    const res = await request(app.getHttpServer()).get("/api/v1/test/me").expect(401);
    expect(res.headers["content-type"]).toContain("application/problem+json");
    expect(res.headers["www-authenticate"]).toBe("Bearer");
    expect(res.body).toMatchObject({ status: 401, code: "UNAUTHORIZED" });
    expect(res.body.traceId).toBe(res.headers["x-correlation-id"]);
  });

  it("rejects invalid tokens", async () => {
    await request(app.getHttpServer()).get("/api/v1/test/me").set("Authorization", "Bearer garbage").expect(401);
  });

  it("injects the authenticated user", async () => {
    const res = await request(app.getHttpServer())
      .get("/api/v1/test/me")
      .set("Authorization", `Bearer ${await tokenFor(["CUSTOMER"])}`)
      .expect(200);
    expect(res.body).toMatchObject({ id: "user-7", roles: ["CUSTOMER"] });
  });

  it("enforces roles with 403", async () => {
    const server = app.getHttpServer();
    await request(server).get("/api/v1/test/courier-only").set("Authorization", `Bearer ${await tokenFor(["CUSTOMER"])}`).expect(403);
    await request(server).get("/api/v1/test/courier-only").set("Authorization", `Bearer ${await tokenFor(["COURIER"])}`).expect(200);
  });

  it("allows public routes and keeps well-formed correlation ids", async () => {
    const res = await request(app.getHttpServer()).get("/api/v1/test/public").set("X-Correlation-Id", "corr-12345678").expect(200);
    expect(res.headers["x-correlation-id"]).toBe("corr-12345678");
  });

  it("maps ApiError and Nest exceptions to problems", async () => {
    const token = await tokenFor(["CUSTOMER"]);
    const notFound = await request(app.getHttpServer()).get("/api/v1/test/missing").set("Authorization", `Bearer ${token}`).expect(404);
    expect(notFound.body).toMatchObject({ code: "PARCEL_NOT_FOUND", type: "https://api.swiftdrop.lk/problems/parcel-not-found" });

    const nest404 = await request(app.getHttpServer()).get("/api/v1/test/nest-404").expect(404);
    expect(nest404.body.code).toBe("NOT_FOUND");
  });

  it("hides internal error details", async () => {
    const res = await request(app.getHttpServer()).get("/api/v1/test/boom").expect(500);
    expect(res.body.code).toBe("INTERNAL_ERROR");
    expect(JSON.stringify(res.body)).not.toContain("hunter2");
  });

  it("exposes health and Prometheus metrics without a token", async () => {
    await request(app.getHttpServer()).get("/health/live").expect(200, { status: "UP" });
    const metrics = await request(app.getHttpServer()).get("/metrics").expect(200);
    expect(metrics.text).toContain("http_server_requests_seconds");
  });
});

describe("PinoNestLogger", () => {
  it("forwards every Nest log level to pino", () => {
    const calls: string[] = [];
    const fake = new Proxy({}, { get: (_t, level: string) => (_obj: unknown, msg: string) => calls.push(`${level}:${msg}`) });
    const logger = new PinoNestLogger(fake as never);
    logger.log("a");
    logger.error("b", "trace");
    logger.warn("c");
    logger.debug("d");
    logger.verbose("e");
    expect(calls).toEqual(["info:a", "error:b", "warn:c", "debug:d", "trace:e"]);
  });
});
