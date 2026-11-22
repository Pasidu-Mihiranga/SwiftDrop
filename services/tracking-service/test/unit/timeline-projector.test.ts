import type { ParcelAssigned, ParcelCreated, ParcelStatusChanged } from "@swiftdrop/common";
import { applyAssigned, applyCreated, applyStatus } from "../../src/modules/timeline/domain/timeline-projector";
import { toPublicView } from "../../src/modules/timeline/domain/public-view";
import type { Timeline } from "../../src/modules/timeline/domain/timeline";

const PARCEL = "0b6f4a9e-7c1d-4e2f-9a3b-5c6d7e8f9a0b";
const TRACKING = "SD261020K7Q2M9X";
const CUSTOMER = "11111111-0000-4000-8000-000000000001";
const COURIER = "a1b2c3d4-0000-4000-8000-00000000c001";
const MERCHANT = "00000000-0000-4000-8000-000000000001";

const created = (over: Partial<ParcelCreated> = {}): ParcelCreated => ({
  parcelId: PARCEL,
  trackingNumber: TRACKING,
  source: "CUSTOMER",
  customerId: CUSTOMER,
  merchantId: null,
  merchantOrderRef: null,
  pickup: { city: "Colombo", district: "Colombo" },
  dropoff: { city: "Kandy", district: "Kandy" },
  sizeClass: "S",
  weightKg: 2,
  serviceLevel: "STANDARD",
  fee: { amount: 650, currency: "LKR" },
  contacts: { recipientName: "Kamal Perera", recipientPhone: "0771234567", senderName: null, senderEmail: null, senderPhone: null },
  createdAt: "2026-10-20T08:00:00Z",
  ...over,
});

const status = (over: Partial<ParcelStatusChanged>): ParcelStatusChanged => ({
  parcelId: PARCEL,
  trackingNumber: TRACKING,
  customerId: CUSTOMER,
  merchantId: null,
  courierId: COURIER,
  fromStatus: null,
  toStatus: "PICKED_UP",
  note: null,
  failureReason: null,
  proof: null,
  location: { city: "Colombo", district: "Colombo", zoneCode: null },
  changedBy: { userId: COURIER, role: "COURIER" },
  changedAt: "2026-10-20T09:00:00Z",
  ...over,
});

const assigned = (over: Partial<ParcelAssigned> = {}): ParcelAssigned => ({
  assignmentId: "asg-1",
  parcelId: PARCEL,
  trackingNumber: TRACKING,
  courierId: COURIER,
  previousCourierId: null,
  runDate: "2026-10-20",
  assignedBy: { userId: "disp-1", role: "DISPATCHER" },
  assignedAt: "2026-10-20T08:30:00Z",
  ...over,
});

/** Builds a timeline the way the consumers do: created first, then the other events. */
function build(steps: Array<(t: Timeline | null) => Timeline>): Timeline | null {
  return steps.reduce<Timeline | null>((t, step) => step(t), null);
}

describe("timeline projection", () => {
  it("creates a timeline from ParcelCreated with masked recipient and city-only locations", () => {
    const t = applyCreated(null, created(), "evt-created")!;
    expect(t.trackingNumber).toBe(TRACKING);
    expect(t.fromCity).toBe("Colombo");
    expect(t.toCity).toBe("Kandy");
    expect(t.recipientMasked).toBe("K*** P.");
    expect(t.currentStatus).toBe("CREATED");
    expect(t.ownerIds).toEqual([CUSTOMER]);
    expect(t.version).toBe(1);
  });

  it("applies a redelivered event exactly once", () => {
    const first = applyCreated(null, created(), "evt-created")!;
    const again = applyCreated(first, created(), "evt-created");
    expect(again).toBe(first);
    const statusOnce = applyStatus(first, status({}), "evt-pickup");
    expect(applyStatus(statusOnce, status({}), "evt-pickup")).toBe(statusOnce);
  });

  it("places an out-of-order event by time and never moves currentStatus backwards", () => {
    const t = build([
      (cur) => applyCreated(cur, created(), "e-created"),
      (cur) => applyStatus(cur, status({ toStatus: "OUT_FOR_DELIVERY", changedAt: "2026-10-20T12:00:00Z" }), "e-ofd"),
      // arrives late, but happened before the out-for-delivery change
      (cur) => applyStatus(cur, status({ toStatus: "PICKED_UP", changedAt: "2026-10-20T09:00:00Z" }), "e-pickup"),
    ])!;
    expect(t.events.map((e) => e.status)).toEqual(["CREATED", "PICKED_UP", "OUT_FOR_DELIVERY"]);
    expect(t.currentStatus).toBe("OUT_FOR_DELIVERY");
    expect(t.lastChangedAt).toBe("2026-10-20T12:00:00Z");
  });

  it("starts a shell timeline when a status arrives before ParcelCreated, then completes it", () => {
    const shell = applyStatus(null, status({ toStatus: "PICKED_UP" }), "e-pickup");
    expect(shell.fromCity).toBeNull();
    expect(shell.currentStatus).toBe("PICKED_UP");
    const complete = applyCreated(shell, created(), "e-created");
    expect(complete.fromCity).toBe("Colombo");
    expect(complete.events.map((e) => e.status)).toEqual(["CREATED", "PICKED_UP"]);
    expect(complete.currentStatus).toBe("PICKED_UP");
  });

  it("records a courier assignment without adding a status event", () => {
    const t = build([(cur) => applyCreated(cur, created(), "e-created"), (cur) => applyAssigned(cur, assigned(), "e-asg")])!;
    expect(t.courierId).toBe(COURIER);
    expect(t.ownerIds).toEqual([CUSTOMER, COURIER]);
    expect(t.events).toHaveLength(1);
    expect(t.currentStatus).toBe("CREATED");
  });

  it("keeps the merchant id separately, for the merchant_id claim check", () => {
    const t = applyCreated(null, created({ customerId: null, merchantId: MERCHANT, source: "MERCHANT" }), "e-created");
    expect(t.merchantId).toBe(MERCHANT);
    expect(t.ownerIds).toEqual([]);
  });

  it("does not store a phone number or a full recipient name anywhere in the timeline", () => {
    const t = build([
      (cur) => applyCreated(cur, created(), "e-created"),
      (cur) => applyStatus(cur, status({}), "e-pickup"),
      (cur) => applyAssigned(cur, assigned(), "e-asg"),
    ])!;
    const serialised = JSON.stringify(t);
    expect(serialised).not.toContain("0771234567");
    expect(serialised).not.toContain("Kamal");
    expect(serialised).not.toContain("Perera");
  });

  it("public view exposes city and masked name only", () => {
    const t = build([(cur) => applyCreated(cur, created(), "e-created"), (cur) => applyStatus(cur, status({}), "e-pickup")])!;
    const view = toPublicView(t);
    expect(view).toEqual({
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
    expect(JSON.stringify(view)).not.toContain("0771234567");
  });
});
