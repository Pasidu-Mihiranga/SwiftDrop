import { describe, expect, it } from "vitest";
import { applyUpdate, lastUpdateLabel, stepperFor, TRACKING_NUMBER_PATTERN, type TrackingView } from "./tracking";

const base: TrackingView = {
  trackingNumber: "SD261020K7Q2M9X",
  currentStatus: null,
  from: "Colombo",
  to: "Kandy",
  recipient: "K*** P.",
  events: [],
};

const at = (status: TrackingView["currentStatus"], time: string, city: string | null = "Colombo") => ({
  status: status!,
  city,
  at: time,
});

describe("stepperFor", () => {
  it("shows every step as to-do before the first event", () => {
    const stepper = stepperFor(base);
    expect(stepper.steps.map((s) => s.state)).toEqual(["todo", "todo", "todo", "todo", "todo"]);
    expect(stepper.outcome).toBeNull();
  });

  it("marks the current step and the steps before it as done", () => {
    const view: TrackingView = {
      ...base,
      currentStatus: "IN_TRANSIT",
      events: [at("CREATED", "2026-10-20T08:00:00Z"), at("PICKED_UP", "2026-10-20T09:00:00Z"), at("IN_TRANSIT", "2026-10-20T10:00:00Z")],
    };
    expect(stepperFor(view).steps.map((s) => s.state)).toEqual(["done", "done", "current", "todo", "todo"]);
  });

  it("finishes with delivered and no current step", () => {
    const view: TrackingView = { ...base, currentStatus: "DELIVERED", events: [at("DELIVERED", "2026-10-20T14:00:00Z")] };
    const stepper = stepperFor(view);
    expect(stepper.steps.map((s) => s.state)).toEqual(["done", "done", "done", "done", "current"]);
  });

  it("keeps the reached steps and reports a failed delivery as an outcome", () => {
    const view: TrackingView = {
      ...base,
      currentStatus: "FAILED",
      events: [at("OUT_FOR_DELIVERY", "2026-10-20T13:00:00Z", "Kandy"), at("FAILED", "2026-10-20T15:00:00Z", "Kandy")],
    };
    const stepper = stepperFor(view);
    expect(stepper.outcome).toBe("FAILED");
    expect(stepper.steps.map((s) => s.state)).toEqual(["done", "done", "done", "done", "todo"]);
  });

  it("reports a cancelled parcel with its last reached step", () => {
    const view: TrackingView = { ...base, currentStatus: "CANCELLED", events: [at("CREATED", "2026-10-20T08:00:00Z"), at("CANCELLED", "2026-10-20T08:30:00Z")] };
    const stepper = stepperFor(view);
    expect(stepper.outcome).toBe("CANCELLED");
    expect(stepper.steps.map((s) => s.state)).toEqual(["done", "todo", "todo", "todo", "todo"]);
  });
});

describe("applyUpdate", () => {
  const older: TrackingView = { ...base, currentStatus: "PICKED_UP", events: [at("PICKED_UP", "2026-10-20T09:00:00Z")] };
  const newer: TrackingView = { ...base, currentStatus: "OUT_FOR_DELIVERY", events: [at("OUT_FOR_DELIVERY", "2026-10-20T14:02:00Z", "Kandy")] };

  it("replaces the shown view with a newer one", () => {
    expect(applyUpdate(older, newer)).toBe(newer);
  });

  it("keeps the shown view when an older update arrives late", () => {
    expect(applyUpdate(newer, older)).toBe(newer);
  });

  it("ignores an update for another tracking number", () => {
    const other = { ...newer, trackingNumber: "SD999999999999" };
    expect(applyUpdate(older, other)).toBe(older);
  });
});

describe("lastUpdateLabel", () => {
  it("formats the last event time in Sri Lanka time", () => {
    const view: TrackingView = { ...base, events: [at("OUT_FOR_DELIVERY", "2026-10-20T08:02:00Z", "Kandy")] };
    // 08:02 UTC is 13:32 in Sri Lanka (UTC+5:30).
    expect(lastUpdateLabel(view)).toMatch(/20 Oct 2026, 13:32/);
  });

  it("is null without events or with an invalid time", () => {
    expect(lastUpdateLabel(base)).toBeNull();
    expect(lastUpdateLabel({ ...base, events: [at("CREATED", "not-a-date")] })).toBeNull();
  });
});

describe("TRACKING_NUMBER_PATTERN", () => {
  it("accepts the documented shape and rejects everything else", () => {
    expect(TRACKING_NUMBER_PATTERN.test("SD261020K7Q2M9X")).toBe(true);
    expect(TRACKING_NUMBER_PATTERN.test("SD1")).toBe(false);
    expect(TRACKING_NUMBER_PATTERN.test("sd261020k7q2m9x")).toBe(false);
    expect(TRACKING_NUMBER_PATTERN.test("XX261020K7Q2M9X")).toBe(false);
  });
});
