import "reflect-metadata";
import type { INestApplication } from "@nestjs/common";
import { Test } from "@nestjs/testing";
import { SwiftDropCommonModule } from "@swiftdrop/common/nest";
import { createLocalJWKSet, exportJWK, generateKeyPair, SignJWT } from "jose";
import request from "supertest";
import { NotificationController } from "../src/modules/notifications/notification.controller";
import { DatabaseService } from "../src/persistence/database.service";

const ISSUER = "http://localhost:8180/realms/swiftdrop";

describe("NotificationController", () => {
  let app: INestApplication;
  let sign: (claims: Record<string, unknown>) => Promise<string>;
  let mockDb: any;

  beforeAll(async () => {
    const { privateKey, publicKey } = await generateKeyPair("RS256");
    const keys = createLocalJWKSet({ keys: [{ ...(await exportJWK(publicKey)), kid: "k", alg: "RS256" }] });
    sign = (claims) =>
      new SignJWT(claims)
        .setProtectedHeader({ alg: "RS256", kid: "k" })
        .setIssuer(ISSUER)
        .setAudience("swiftdrop-api")
        .setExpirationTime("5m")
        .sign(privateKey);

    mockDb = {
      notifications: {
        find: jest.fn().mockReturnValue({
          sort: jest.fn().mockReturnThis(),
          skip: jest.fn().mockReturnThis(),
          limit: jest.fn().mockReturnThis(),
          toArray: jest.fn().mockResolvedValue([
            {
              _id: "notif-1",
              userId: "user-cust",
              title: "Booking Confirmed",
              message: "Your parcel is booked",
              read: false,
              createdAt: new Date().toISOString(),
            },
          ]),
        }),
        countDocuments: jest.fn().mockImplementation((query) => {
          if (query.read === false) return Promise.resolve(1);
          return Promise.resolve(1);
        }),
        updateOne: jest.fn().mockResolvedValue({ matchedCount: 1 }),
      },
      preferences: {
        findOne: jest.fn().mockResolvedValue({
          _id: "user-cust",
          email: true,
          sms: false,
          events: ["CREATED", "DELIVERED"],
        }),
        updateOne: jest.fn().mockResolvedValue({ acknowledged: true }),
      },
      smsOutbox: {
        find: jest.fn().mockReturnValue({
          sort: jest.fn().mockReturnThis(),
          limit: jest.fn().mockReturnThis(),
          toArray: jest.fn().mockResolvedValue([
            {
              _id: "sms-1",
              recipientPhoneMasked: "077****567",
              body: "Parcel delivered",
              status: "SENT",
              sentAt: new Date().toISOString(),
            },
          ]),
        }),
      },
    };

    const moduleRef = await Test.createTestingModule({
      imports: [
        SwiftDropCommonModule.forRoot({
          service: "notification-service",
          issuer: ISSUER,
          jwksUri: "http://unused",
          keys,
        }),
      ],
      controllers: [NotificationController],
      providers: [
        { provide: DatabaseService, useValue: mockDb },
      ],
    }).compile();

    app = moduleRef.createNestApplication({ logger: false });
    await app.init();
  });

  afterAll(() => app.close());

  it("GET /api/v1/notifications/me returns caller's notifications and unread count", async () => {
    const token = await sign({ sub: "user-cust", realm_access: { roles: ["CUSTOMER"] } });

    const res = await request(app.getHttpServer())
      .get("/api/v1/notifications/me")
      .set("Authorization", `Bearer ${token}`)
      .expect(200);

    expect(res.body.content).toHaveLength(1);
    expect(res.body.content[0]._id).toBe("notif-1");
    expect(res.body.unreadCount).toBe(1);
  });

  it("POST /api/v1/notifications/me/:id/read marks notification as read", async () => {
    const token = await sign({ sub: "user-cust", realm_access: { roles: ["CUSTOMER"] } });

    await request(app.getHttpServer())
      .post("/api/v1/notifications/me/notif-1/read")
      .set("Authorization", `Bearer ${token}`)
      .expect(204);

    expect(mockDb.notifications.updateOne).toHaveBeenCalledWith(
      { _id: "notif-1", userId: "user-cust" },
      { $set: { read: true } },
    );
  });

  it("GET /api/v1/notifications/preferences returns caller's preferences", async () => {
    const token = await sign({ sub: "user-cust", realm_access: { roles: ["CUSTOMER"] } });

    const res = await request(app.getHttpServer())
      .get("/api/v1/notifications/preferences")
      .set("Authorization", `Bearer ${token}`)
      .expect(200);

    expect(res.body.email).toBe(true);
    expect(res.body.sms).toBe(false);
    expect(res.body.events).toContain("DELIVERED");
  });

  it("PUT /api/v1/notifications/preferences updates caller preferences", async () => {
    const token = await sign({ sub: "user-cust", realm_access: { roles: ["CUSTOMER"] } });

    const res = await request(app.getHttpServer())
      .put("/api/v1/notifications/preferences")
      .set("Authorization", `Bearer ${token}`)
      .send({ email: true, sms: true, events: ["DELIVERED", "FAILED"] })
      .expect(200);

    expect(res.body.email).toBe(true);
    expect(res.body.sms).toBe(true);
    expect(res.body.events).toEqual(["DELIVERED", "FAILED"]);
  });

  it("GET /api/v1/admin/notifications/sms-inbox allows ADMIN and returns messages", async () => {
    const adminToken = await sign({ sub: "user-admin", realm_access: { roles: ["ADMIN"] } });

    const res = await request(app.getHttpServer())
      .get("/api/v1/admin/notifications/sms-inbox")
      .set("Authorization", `Bearer ${adminToken}`)
      .expect(200);

    expect(res.body.messages).toHaveLength(1);
    expect(res.body.messages[0].recipientPhoneMasked).toBe("077****567");
  });

  it("GET /api/v1/admin/notifications/sms-inbox forbids non-admin callers", async () => {
    const custToken = await sign({ sub: "user-cust", realm_access: { roles: ["CUSTOMER"] } });

    await request(app.getHttpServer())
      .get("/api/v1/admin/notifications/sms-inbox")
      .set("Authorization", `Bearer ${custToken}`)
      .expect(403);
  });
});
