import { computeSignature, signatureHeader, verifySignature } from "../../src/modules/webhooks/domain/signer";
import { BackoffSchedule } from "../../src/modules/webhooks/domain/backoff";
import { parcelCreatedData, parcelStatusChangedData } from "../../src/modules/webhooks/domain/payload";
import { isBlockedAddress, UrlPolicy } from "../../src/modules/webhooks/domain/url-policy";
import type { ParcelCreated, ParcelStatusChanged } from "@swiftdrop/common";

describe("signer", () => {
  const body = '{"id":"whd_1","type":"parcel.created"}';

  it("matches an independently computed HMAC-SHA256 test vector", () => {
    expect(computeSignature("whsec_test", 1_700_000_000, body))
      .toBe("e6ab0bb1932cef9d2bc8e25d3265e511a7e53ad37fbb0a594e9cd5fc0bb7bf38");
    expect(signatureHeader("whsec_test", 1_700_000_000, body))
      .toBe("t=1700000000,v1=e6ab0bb1932cef9d2bc8e25d3265e511a7e53ad37fbb0a594e9cd5fc0bb7bf38");
  });

  it("verifies a fresh signature and rejects tampering, a wrong secret and an old timestamp", () => {
    const header = signatureHeader("whsec_test", 1_700_000_000, body);
    expect(verifySignature(header, body, "whsec_test", 1_700_000_100)).toBe(true);
    expect(verifySignature(header, body.replace("created", "deleted"), "whsec_test", 1_700_000_100)).toBe(false);
    expect(verifySignature(header, body, "whsec_other", 1_700_000_100)).toBe(false);
    expect(verifySignature(header, body, "whsec_test", 1_700_000_301)).toBe(false);
    expect(verifySignature(undefined, body, "whsec_test", 1_700_000_000)).toBe(false);
    expect(verifySignature("t=1700000000,v1=abc", body, "whsec_test", 1_700_000_000)).toBe(false);
  });
});

describe("backoff schedule", () => {
  it("allows seven attempts with the default delays of 30 s, 2 m, 10 m, 30 m, 2 h and 6 h", () => {
    const schedule = new BackoffSchedule();
    expect(schedule.maxAttempts).toBe(7);
    expect([1, 2, 3, 4, 5, 6].map((n) => schedule.delayMs(n))).toEqual([30_000, 120_000, 600_000, 1_800_000, 7_200_000, 21_600_000]);
    expect(schedule.delayMs(9)).toBe(21_600_000);
  });

  it("can be shortened for the demo and refuses an empty or negative schedule", () => {
    expect(new BackoffSchedule([1, 2]).maxAttempts).toBe(3);
    expect(() => new BackoffSchedule([])).toThrow();
    expect(() => new BackoffSchedule([5, -1])).toThrow();
  });
});

describe("payload allow-list", () => {
  const created: ParcelCreated = {
    parcelId: "p-1", trackingNumber: "SD261114K7Q2M9X", source: "MERCHANT", customerId: null, merchantId: "m-1",
    merchantOrderRef: "ORD-42", pickup: { city: "Colombo 03", district: "Colombo", zoneCode: "WP-COL" },
    dropoff: { city: "Kandy", district: "Kandy" }, sizeClass: "S", weightKg: 2, serviceLevel: "STANDARD",
    fee: { amount: 650, currency: "LKR" },
    contacts: { senderName: "Lanka Books", senderEmail: "orders@lankabooks.lk", senderPhone: "0771234567",
      recipientName: "Saman Perera", recipientPhone: "0712345678" },
    createdAt: "2026-11-14T08:00:00Z",
  };

  it("never copies contacts, names or phone numbers from ParcelCreated", () => {
    const data = parcelCreatedData(created);
    expect(data).toMatchObject({ parcelId: "p-1", merchantOrderRef: "ORD-42", status: "CREATED", fee: { amount: 650, currency: "LKR" } });
    expect(data.pickup).toEqual({ city: "Colombo 03", district: "Colombo" });
    expect(JSON.stringify(data)).not.toMatch(/Saman|0712345678|0771234567|lankabooks|contacts/);
  });

  it("leaves out notes, the courier and who signed for the parcel", () => {
    const changed: ParcelStatusChanged = {
      parcelId: "p-1", trackingNumber: "SD261114K7Q2M9X", customerId: "c-1", merchantId: "m-1", courierId: "courier-9",
      fromStatus: "OUT_FOR_DELIVERY", toStatus: "DELIVERED", note: "Left with neighbour at No. 12",
      proof: { type: "NAME", receivedBy: "Kamal Silva", proofId: null }, changedBy: { userId: "courier-9", role: "COURIER" },
      changedAt: "2026-11-14T10:00:00Z",
    };
    const data = parcelStatusChangedData(changed, "ORD-42");
    expect(data).toEqual({ parcelId: "p-1", trackingNumber: "SD261114K7Q2M9X", merchantOrderRef: "ORD-42",
      fromStatus: "OUT_FOR_DELIVERY", status: "DELIVERED", failureReason: null, proofType: "NAME", changedAt: "2026-11-14T10:00:00Z" });
    expect(parcelStatusChangedData({ ...changed, proof: null, fromStatus: undefined }, null))
      .toMatchObject({ proofType: null, fromStatus: null, merchantOrderRef: null });
  });
});

describe("URL policy (SSRF)", () => {
  const publicHost = new UrlPolicy({ allowHttp: false, allowPrivateNetworks: false, lookup: async () => ["93.184.216.34"] });

  it.each(["127.0.0.1", "10.1.2.3", "172.20.0.5", "192.168.1.10", "169.254.169.254", "100.64.0.1", "0.0.0.0",
    "::1", "fd00::1", "fe80::1", "::ffff:127.0.0.1", "not-an-ip"])("blocks %s", (address) => {
    expect(isBlockedAddress(address)).toBe(true);
  });

  it.each(["93.184.216.34", "8.8.8.8", "2606:4700:4700::1111"])("allows public address %s", (address) => {
    expect(isBlockedAddress(address)).toBe(false);
  });

  it("accepts a public https URL", async () => {
    await expect(publicHost.assertAllowed("https://shop.example.com/hooks")).resolves.toBeInstanceOf(URL);
  });

  it.each([
    ["http://shop.example.com/hooks", "https"],
    ["ftp://shop.example.com", "https"],
    ["not a url", "not valid"],
    ["https://user:pass@shop.example.com", "credentials"],
    ["https://127.0.0.1/hooks", "private"],
    ["https://[::1]/hooks", "private"],
  ])("rejects %s", async (url, reason) => {
    await expect(publicHost.assertAllowed(url)).rejects.toMatchObject({ status: 400, code: "WEBHOOK_URL_REJECTED",
      message: expect.stringContaining(reason) });
  });

  it("rejects a host name that resolves to an internal address or does not resolve", async () => {
    const internal = new UrlPolicy({ allowHttp: false, allowPrivateNetworks: false, lookup: async () => ["93.184.216.34", "10.0.0.7"] });
    const missing = new UrlPolicy({ allowHttp: false, allowPrivateNetworks: false, lookup: async () => { throw new Error("ENOTFOUND"); } });
    const empty = new UrlPolicy({ allowHttp: false, allowPrivateNetworks: false, lookup: async () => [] });
    await expect(internal.assertAllowed("https://rebind.example.com")).rejects.toMatchObject({ code: "WEBHOOK_URL_REJECTED" });
    await expect(missing.assertAllowed("https://nowhere.example.com")).rejects.toMatchObject({ message: expect.stringContaining("resolved") });
    await expect(empty.assertAllowed("https://empty.example.com")).rejects.toMatchObject({ code: "WEBHOOK_URL_REJECTED" });
  });

  it("allows http and private addresses only when the demo network opts in", async () => {
    const demo = new UrlPolicy({ allowHttp: true, allowPrivateNetworks: true });
    await expect(demo.assertAllowed("http://merchant-simulator:4000/webhooks")).resolves.toBeInstanceOf(URL);
  });

  it("resolves real host names through DNS by default", async () => {
    await expect(new UrlPolicy({ allowHttp: false, allowPrivateNetworks: false }).assertAllowed("https://localhost/hooks"))
      .rejects.toMatchObject({ code: "WEBHOOK_URL_REJECTED" });
  });
});
