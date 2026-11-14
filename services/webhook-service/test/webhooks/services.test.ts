import { createEnvelope, type ParcelCreated, type ParcelStatusChanged } from "@swiftdrop/common";
import { DeliveryLogService } from "../../src/modules/webhooks/application/delivery-log.service";
import { DeliveryProcessor, RetryLaterError } from "../../src/modules/webhooks/application/delivery-processor";
import { EndpointService } from "../../src/modules/webhooks/application/endpoint.service";
import { FanOutService } from "../../src/modules/webhooks/application/fan-out.service";
import { verifySignature } from "../../src/modules/webhooks/domain/signer";
import { UrlPolicy } from "../../src/modules/webhooks/domain/url-policy";
import { AesSecretCipher } from "../../src/modules/webhooks/infrastructure/aes-secret-cipher";
import { fakes, NOW, ScriptedSender, TEST_KEY, testSettings } from "./fakes";

function setUp(sender = new ScriptedSender()) {
  const f = fakes();
  const settings = testSettings();
  return {
    ...f, sender, settings,
    endpointService: new EndpointService(f.endpoints, f.deliveries, f.queue, f.cipher, settings),
    fanOut: new FanOutService(f.endpoints, f.deliveries, f.merchants, f.parcelRefs, f.queue, settings),
    processor: new DeliveryProcessor(f.deliveries, f.endpoints, f.merchants, sender, f.cipher, f.deadLetters, settings),
    log: new DeliveryLogService(f.deliveries, f.queue),
  };
}

const created = (merchantId: string | null, ref = "ORD-42") => createEnvelope("ParcelCreated", {
  parcelId: "p-1", trackingNumber: "SD261114K7Q2M9X", source: merchantId ? "MERCHANT" : "CUSTOMER", merchantId,
  merchantOrderRef: merchantId ? ref : null, pickup: { city: "Colombo 03", district: "Colombo" },
  dropoff: { city: "Kandy", district: "Kandy" }, sizeClass: "S", weightKg: 2, serviceLevel: "STANDARD",
  fee: { amount: 650, currency: "LKR" }, contacts: { recipientName: "Saman Perera", recipientPhone: "0712345678" },
  createdAt: NOW.toISOString(),
} as ParcelCreated, "parcel-service");

const statusChanged = (merchantId: string | null) => createEnvelope("ParcelStatusChanged", {
  parcelId: "p-1", trackingNumber: "SD261114K7Q2M9X", merchantId, fromStatus: "CREATED", toStatus: "PICKED_UP",
  changedBy: { userId: "courier-1", role: "COURIER" }, changedAt: NOW.toISOString(),
} as ParcelStatusChanged, "parcel-service");

describe("endpoint management", () => {
  it("returns the secret once and stores only its ciphertext", async () => {
    const s = setUp();
    const { endpoint, secret } = await s.endpointService.register("m-1", "https://shop.example.com/hooks",
      ["parcel.created", "parcel.created", "parcel.status_changed"]);
    expect(secret).toMatch(/^whsec_[A-Za-z0-9_-]{43}$/);
    expect(endpoint.id).toMatch(/^whe_[0-9a-f]{32}$/);
    expect(endpoint.eventTypes).toEqual(["parcel.created", "parcel.status_changed"]);
    const stored = s.endpoints.rows.get(endpoint.id)!;
    expect(stored.secretEnc).not.toContain(secret);
    expect(s.cipher.decrypt(stored.secretEnc)).toBe(secret);
  });

  it("refuses an internal URL and stores nothing", async () => {
    const s = setUp();
    await expect(s.endpointService.register("m-1", "https://169.254.169.254/latest", ["parcel.created"]))
      .rejects.toMatchObject({ code: "WEBHOOK_URL_REJECTED" });
    expect(s.endpoints.rows.size).toBe(0);
  });

  it("another merchant's endpoint is not found for removal or a test ping", async () => {
    const s = setUp();
    const { endpoint } = await s.endpointService.register("m-1", "https://shop.example.com/hooks", ["parcel.created"]);
    await expect(s.endpointService.remove("m-2", endpoint.id)).rejects.toMatchObject({ status: 404 });
    await expect(s.endpointService.sendTestPing("m-2", endpoint.id)).rejects.toMatchObject({ status: 404 });
    expect(await s.endpointService.list("m-2")).toEqual([]);
    await s.endpointService.remove("m-1", endpoint.id);
    expect(await s.endpointService.list("m-1")).toEqual([]);
  });

  it("queues a test ping as a normal delivery", async () => {
    const s = setUp();
    const { endpoint } = await s.endpointService.register("m-1", "https://shop.example.com/hooks", ["parcel.created"]);
    const id = await s.endpointService.sendTestPing("m-1", endpoint.id);
    expect(s.queue.queued).toEqual([id]);
    expect(s.deliveries.rows.get(id)).toMatchObject({ eventType: "webhook.test", status: "PENDING", merchantId: "m-1" });
  });
});

describe("fan-out", () => {
  it("creates one delivery per subscribed endpoint of the parcel's merchant only", async () => {
    const s = setUp();
    const a = await s.endpointService.register("m-1", "https://a.example.com", ["parcel.created"]);
    await s.endpointService.register("m-1", "https://b.example.com", ["parcel.status_changed"]);
    await s.endpointService.register("m-2", "https://c.example.com", ["parcel.created"]);

    expect(await s.fanOut.onParcelCreated(created("m-1"))).toBe(1);
    const [delivery] = s.deliveries.rows.values();
    expect(delivery).toMatchObject({ endpointId: a.endpoint.id, eventType: "parcel.created", sourceEventType: "ParcelCreated" });
    expect(JSON.stringify(delivery.payload)).not.toContain("0712345678");
  });

  it("a redelivered event does not create a second delivery", async () => {
    const s = setUp();
    await s.endpointService.register("m-1", "https://a.example.com", ["parcel.created"]);
    const event = created("m-1");
    await s.fanOut.onParcelCreated(event);
    expect(await s.fanOut.onParcelCreated(event)).toBe(0);
    expect(s.deliveries.rows.size).toBe(1);
  });

  it("customer parcels create no deliveries", async () => {
    const s = setUp();
    await s.endpointService.register("m-1", "https://a.example.com", ["parcel.created", "parcel.status_changed"]);
    expect(await s.fanOut.onParcelCreated(created(null))).toBe(0);
    expect(await s.fanOut.onParcelStatusChanged(statusChanged(null))).toBe(0);
    expect(s.deliveries.rows.size).toBe(0);
  });

  it("fills merchantOrderRef on a status change from the earlier ParcelCreated", async () => {
    const s = setUp();
    await s.endpointService.register("m-1", "https://a.example.com", ["parcel.status_changed"]);
    await s.fanOut.onParcelCreated(created("m-1", "ORD-77"));
    await s.fanOut.onParcelStatusChanged(statusChanged("m-1"));
    const [delivery] = s.deliveries.rows.values();
    expect(delivery.payload).toMatchObject({ merchantOrderRef: "ORD-77", status: "PICKED_UP", fromStatus: "CREATED" });
  });

  it("holds deliveries for a suspended merchant and releases them on approval", async () => {
    const s = setUp();
    await s.endpointService.register("m-1", "https://a.example.com", ["parcel.created"]);
    const lifecycle = (status: "SUSPENDED" | "APPROVED") => createEnvelope("MerchantLifecycleChanged", {
      merchantId: "m-1", businessName: "Lanka Books", ownerUserId: "u-1", contactEmail: "o@lankabooks.lk", status,
      changedBy: { userId: "admin-1", role: "ADMIN" }, changedAt: NOW.toISOString(),
    }, "merchant-service");

    expect(await s.fanOut.onMerchantLifecycleChanged(lifecycle("SUSPENDED"))).toBe(0);
    expect(await s.fanOut.onParcelCreated(created("m-1"))).toBe(0);
    expect(s.queue.queued).toEqual([]);
    expect(await s.fanOut.onMerchantLifecycleChanged(lifecycle("APPROVED"))).toBe(1);
    expect(s.queue.queued).toHaveLength(1);
    expect(await s.fanOut.onMerchantLifecycleChanged(lifecycle("APPROVED"))).toBe(0);
  });
});

describe("delivery processor", () => {
  async function queuedDelivery(s: ReturnType<typeof setUp>) {
    const { endpoint, secret } = await s.endpointService.register("m-1", "https://shop.example.com/hooks", ["parcel.created"]);
    await s.fanOut.onParcelCreated(created("m-1"));
    return { endpoint, secret, id: s.queue.queued[0] };
  }

  it("signs the body with the endpoint secret and marks the delivery DELIVERED on 2xx", async () => {
    const s = setUp();
    const { secret, id } = await queuedDelivery(s);

    expect(await s.processor.process(id, 1)).toBe("delivered");

    const [sent] = s.sender.sent;
    expect(sent.url).toBe("https://shop.example.com/hooks");
    expect(verifySignature(sent.headers["X-SwiftDrop-Signature"], sent.body, secret, NOW.getTime() / 1000)).toBe(true);
    expect(JSON.parse(sent.body)).toMatchObject({ id, type: "parcel.created", data: { trackingNumber: "SD261114K7Q2M9X" } });
    expect(s.deliveries.rows.get(id)).toMatchObject({ status: "DELIVERED", deliveredAt: NOW, attempts: [{ httpStatus: 200, latencyMs: 12 }] });
    expect(await s.processor.process(id, 2)).toBe("skipped");
  });

  it("schedules a retry with the next delay after a failure", async () => {
    const s = setUp(new ScriptedSender([{ httpStatus: 503, latencyMs: 40 }]));
    const { id } = await queuedDelivery(s);

    await expect(s.processor.process(id, 1)).rejects.toBeInstanceOf(RetryLaterError);
    await expect(s.processor.process(id, 2)).rejects.toMatchObject({ retryInMs: 120_000 });

    expect(s.deliveries.rows.get(id)).toMatchObject({ status: "RETRYING", nextRetryAt: new Date(NOW.getTime() + 120_000) });
    expect(s.deliveries.rows.get(id)!.attempts).toHaveLength(2);
  });

  it("dead-letters after the last attempt and publishes no payload data", async () => {
    const s = setUp(new ScriptedSender([{ latencyMs: 10_000, error: "No answer within 10000 ms" }]));
    const { id, endpoint } = await queuedDelivery(s);

    expect(await s.processor.process(id, 7)).toBe("dead");

    expect(s.deliveries.rows.get(id)).toMatchObject({ status: "DEAD", deadLetteredAt: NOW });
    expect(s.deadLetters.published).toEqual([expect.objectContaining({ deliveryId: id, merchantId: "m-1", endpointId: endpoint.id,
      sourceEventType: "ParcelCreated", attempts: 1, lastHttpStatus: null, lastError: "No answer within 10000 ms" })]);
    expect(JSON.stringify(s.deadLetters.published)).not.toContain("SD261114K7Q2M9X");
  });

  it("re-checks the URL before each POST and counts a blocked address as a failed attempt", async () => {
    const s = setUp();
    const { id } = await queuedDelivery(s);
    const rebound = new DeliveryProcessor(s.deliveries, s.endpoints, s.merchants, s.sender, s.cipher, s.deadLetters,
      testSettings({ urlPolicy: new UrlPolicy({
        allowHttp: false, allowPrivateNetworks: false, lookup: async () => ["10.0.0.7"] }) }));

    await expect(rebound.process(id, 1)).rejects.toBeInstanceOf(RetryLaterError);
    expect(s.sender.sent).toHaveLength(0);
    expect(s.deliveries.rows.get(id)!.attempts[0].error).toContain("private");
  });

  it("holds a delivery while the merchant is suspended and dead-letters one whose endpoint was removed", async () => {
    const s = setUp();
    const { id, endpoint } = await queuedDelivery(s);
    s.merchants.statuses.set("m-1", "SUSPENDED");
    expect(await s.processor.process(id, 1)).toBe("held");
    s.merchants.statuses.set("m-1", "APPROVED");
    await s.endpointService.remove("m-1", endpoint.id);
    expect(await s.processor.process(id, 1)).toBe("dead");
    expect(s.deadLetters.published[0].lastError).toBe("The endpoint was removed");
    expect(await s.processor.process("whd_missing", 1)).toBe("skipped");
  });

  it("records the status code in the dead-letter event when the merchant answered", async () => {
    const s = setUp(new ScriptedSender([{ httpStatus: 500, latencyMs: 5 }]));
    const { id } = await queuedDelivery(s);
    await s.processor.process(id, 7);
    expect(s.deadLetters.published[0]).toMatchObject({ lastHttpStatus: 500, lastError: "Merchant endpoint returned HTTP 500" });
  });
});

describe("delivery log and replay", () => {
  it("lists only the merchant's deliveries, and admins see all", async () => {
    const s = setUp();
    await s.endpointService.register("m-1", "https://a.example.com", ["parcel.created"]);
    await s.endpointService.register("m-2", "https://b.example.com", ["parcel.created"]);
    await s.fanOut.onParcelCreated(created("m-1"));
    await s.fanOut.onParcelCreated(created("m-2"));
    expect((await s.log.forMerchant("m-1", undefined, 0, 20)).items.map((d) => d.merchantId)).toEqual(["m-1"]);
    expect((await s.log.forAdmin(undefined, 0, 20)).total).toBe(2);
    expect((await s.log.forAdmin("DEAD", 0, 20)).total).toBe(0);
  });

  it("replays a DEAD delivery with a new job and keeps its earlier attempts", async () => {
    const s = setUp(new ScriptedSender([{ httpStatus: 503, latencyMs: 5 }]));
    await s.endpointService.register("m-1", "https://a.example.com", ["parcel.created"]);
    await s.fanOut.onParcelCreated(created("m-1"));
    const id = s.queue.queued[0];
    await s.processor.process(id, 7);

    const replayed = await s.log.replay(id);

    expect(replayed.status).toBe("PENDING");
    expect(s.queue.queued).toEqual([id, id]);
    expect(s.deliveries.rows.get(id)).toMatchObject({ status: "PENDING" });
    expect(s.deliveries.rows.get(id)!.attempts).toHaveLength(1);
  });

  it("refuses to replay a delivery that is not DEAD, and an unknown one", async () => {
    const s = setUp();
    await s.endpointService.register("m-1", "https://a.example.com", ["parcel.created"]);
    await s.fanOut.onParcelCreated(created("m-1"));
    await expect(s.log.replay(s.queue.queued[0])).rejects.toMatchObject({ status: 409, code: "DELIVERY_NOT_DEAD" });
    await expect(s.log.replay("whd_missing")).rejects.toMatchObject({ status: 404 });
  });
});

describe("secret cipher", () => {
  it("decrypts what it encrypted and refuses tampered values, a wrong key and a bad key", () => {
    const cipher = new AesSecretCipher(TEST_KEY);
    const stored = cipher.encrypt("whsec_abc");
    expect(stored).toMatch(/^v1:/);
    expect(cipher.encrypt("whsec_abc")).not.toBe(stored);
    expect(cipher.decrypt(stored)).toBe("whsec_abc");
    const bytes = Buffer.from(stored.slice(3), "base64");
    bytes[bytes.length - 1] ^= 1;
    expect(() => cipher.decrypt(`v1:${bytes.toString("base64")}`)).toThrow("Could not decrypt");
    expect(() => new AesSecretCipher(Buffer.alloc(32, 7).toString("base64")).decrypt(stored)).toThrow();
    expect(() => cipher.decrypt("whsec_abc")).toThrow("unknown format");
    expect(() => cipher.decrypt("v1:AAAA")).toThrow("truncated");
    expect(() => new AesSecretCipher(undefined)).toThrow("WEBHOOK_SECRET_KEY");
    expect(() => new AesSecretCipher(Buffer.alloc(16).toString("base64"))).toThrow();
  });
});
