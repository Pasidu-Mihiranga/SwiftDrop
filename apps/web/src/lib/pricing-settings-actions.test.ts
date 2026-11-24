import { beforeEach, describe, expect, it, vi } from "vitest";

const { authMock, gatewayFetchMock, revalidatePathMock } = vi.hoisted(() => ({
  authMock: vi.fn(),
  gatewayFetchMock: vi.fn(),
  revalidatePathMock: vi.fn(),
}));

vi.mock("@/auth", () => ({ auth: authMock }));
vi.mock("@/lib/gateway", () => ({ gatewayFetch: gatewayFetchMock }));
vi.mock("next/cache", () => ({ revalidatePath: revalidatePathMock }));

import {
  addSpecialHandling,
  publishDistanceBands,
  publishPricingSettings,
  updateSameDayVehicle,
  updateSpecialHandling,
} from "@/lib/pricing-actions";

const IDLE = { status: "idle" } as const;

function form(entries: Record<string, string>): FormData {
  const data = new FormData();
  for (const [key, value] of Object.entries(entries)) data.set(key, value);
  return data;
}

function sentBody(): Record<string, unknown> {
  return JSON.parse(gatewayFetchMock.mock.calls[0][1].body as string) as Record<string, unknown>;
}

beforeEach(() => {
  vi.clearAllMocks();
  authMock.mockResolvedValue({ user: { roles: ["ADMIN"] } });
  gatewayFetchMock.mockResolvedValue(new Response("{}", { status: 201 }));
});

describe("publishPricingSettings", () => {
  const settings = (overrides: Record<string, string> = {}) =>
    form({ fuelPercent: "5", taxPercent: "18", minimumCharge: "350", sameDayCutoff: "14:00", effectiveFrom: "2027-01-01", ...overrides });

  it("sends percentages as fractions with the cut-off", async () => {
    const result = await publishPricingSettings(IDLE, settings());

    expect(result).toEqual({ status: "ok", message: "New fee settings take effect 2027-01-01." });
    expect(gatewayFetchMock.mock.calls[0][0]).toBe("/api/v1/admin/pricing/settings");
    expect(gatewayFetchMock.mock.calls[0][1].method).toBe("POST");
    expect(sentBody()).toEqual({
      fuelSurchargePct: 0.05,
      taxPct: 0.18,
      minimumCharge: 350,
      sameDayCutoff: "14:00",
      effectiveFrom: "2027-01-01",
    });
    expect(revalidatePathMock).toHaveBeenCalledWith("/admin/pricing/settings");
  });

  it("sends no cut-off when the field is blank", async () => {
    await publishPricingSettings(IDLE, settings({ sameDayCutoff: "" }));

    expect(sentBody().sameDayCutoff).toBeNull();
  });

  it.each([
    ["a fuel percentage of 100", { fuelPercent: "100" }, "percentages"],
    ["a missing tax", { taxPercent: "" }, "percentages"],
    ["a negative minimum", { minimumCharge: "-1" }, "minimum charge"],
    ["a missing minimum", { minimumCharge: "" }, "minimum charge"],
    ["a cut-off that is not a time", { sameDayCutoff: "2pm" }, "14:00"],
    ["no date", { effectiveFrom: "" }, "date"],
  ])("refuses %s", async (_name, overrides, message) => {
    const result = await publishPricingSettings(IDLE, settings(overrides));

    expect(result.status).toBe("error");
    expect(result.message).toContain(message);
    expect(gatewayFetchMock).not.toHaveBeenCalled();
  });

  it("shows what the service said when it refuses", async () => {
    gatewayFetchMock.mockResolvedValue(new Response(JSON.stringify({
      status: 409, code: "PRICING_SETTINGS_PERIOD_CONFLICT", detail: "Fee settings already start on or after 2027-01-01.",
    }), { status: 409, headers: { "content-type": "application/problem+json" } }));

    const result = await publishPricingSettings(IDLE, settings());

    expect(result).toEqual({ status: "error", message: "Fee settings already start on or after 2027-01-01.", errors: undefined });
  });

  it("refuses someone who is not an administrator", async () => {
    authMock.mockResolvedValue({ user: { roles: ["DISPATCHER"] } });

    expect((await publishPricingSettings(IDLE, settings())).status).toBe("error");
    expect(gatewayFetchMock).not.toHaveBeenCalled();
  });
});

describe("publishDistanceBands", () => {
  it("publishes the filled rows as one chained set", async () => {
    const result = await publishDistanceBands(IDLE, form({
      effectiveFrom: "2027-01-01",
      bandUpTo1: "30", bandFee1: "0", bandStandard1: "1", bandExpress1: "1",
      bandUpTo2: "", bandFee2: "300", bandStandard2: "3", bandExpress2: "2",
    }));

    expect(result).toEqual({ status: "ok", message: "2 distance bands take effect 2027-01-01." });
    expect(gatewayFetchMock.mock.calls[0][0]).toBe("/api/v1/admin/pricing/distance-bands");
    expect(sentBody()).toEqual({
      effectiveFrom: "2027-01-01",
      bands: [
        { minKm: 0, maxKm: 30, lineHaulFee: 0, standardTransitDays: 1, expressTransitDays: 1 },
        { minKm: 30, maxKm: null, lineHaulFee: 300, standardTransitDays: 3, expressTransitDays: 2 },
      ],
    });
  });

  it("refuses without a date or with a broken set", async () => {
    expect((await publishDistanceBands(IDLE, form({ bandFee1: "0" }))).message).toContain("date");
    expect((await publishDistanceBands(IDLE, form({ effectiveFrom: "2027-01-01" }))).message).toContain("at least one");
    expect(gatewayFetchMock).not.toHaveBeenCalled();
  });
});

describe("special handling", () => {
  it("adds an option under an upper-case code", async () => {
    const result = await addSpecialHandling(IDLE, form({ code: "oversize", label: " Oversize item ", fee: "500", sortOrder: "" }));

    expect(result).toEqual({ status: "ok", message: "Oversize item is now offered on new quotes." });
    expect(sentBody()).toEqual({ code: "OVERSIZE", label: "Oversize item", fee: 500, sortOrder: 0 });
  });

  it.each([
    ["a code starting with a digit", { code: "1X" }, "code"],
    ["no label", { label: " " }, "label"],
    ["a negative fee", { fee: "-1" }, "fee"],
    ["a fractional order", { sortOrder: "1.5" }, "Display order"],
  ])("refuses to add %s", async (_name, overrides, message) => {
    const result = await addSpecialHandling(IDLE, form({ code: "OVERSIZE", label: "Oversize", fee: "500", sortOrder: "5", ...overrides }));

    expect(result.status).toBe("error");
    expect(result.message).toContain(message);
  });

  it("retires an option when Offered is unticked", async () => {
    const result = await updateSpecialHandling(IDLE, form({ code: "LIQUID", label: "Contains liquid", fee: "200", sortOrder: "30" }));

    expect(result.message).toContain("retired");
    expect(gatewayFetchMock.mock.calls[0][0]).toBe("/api/v1/admin/pricing/special-handling/LIQUID");
    expect(gatewayFetchMock.mock.calls[0][1].method).toBe("PUT");
    expect(sentBody()).toEqual({ label: "Contains liquid", active: false, fee: 200, sortOrder: 30 });
  });

  it("keeps an option offered when Offered is ticked, and refuses a bad edit", async () => {
    const saved = await updateSpecialHandling(IDLE, form({ code: "LIQUID", label: "Liquid", fee: "200", active: "on" }));
    expect(saved).toEqual({ status: "ok", message: "Liquid saved." });

    expect((await updateSpecialHandling(IDLE, form({ code: "LIQUID", label: "", fee: "200" }))).status).toBe("error");
    expect((await updateSpecialHandling(IDLE, form({ code: "LIQUID", label: "Liquid", fee: "x" }))).status).toBe("error");
  });
});

describe("updateSameDayVehicle", () => {
  const vehicle = (overrides: Record<string, string> = {}) =>
    form({ code: "van", label: "Van", maxWeightKg: "90", maxDistanceKm: "120", rateMultiplier: "1.8", active: "on", ...overrides });

  it("saves a vehicle's limits", async () => {
    const result = await updateSameDayVehicle(IDLE, vehicle());

    expect(result).toEqual({ status: "ok", message: "Van saved." });
    expect(gatewayFetchMock.mock.calls[0][0]).toBe("/api/v1/admin/pricing/vehicles/VAN");
    expect(sentBody()).toEqual({ label: "Van", maxWeightKg: 90, maxDistanceKm: 120, rateMultiplier: 1.8, active: true });
  });

  it.each([
    ["an unknown vehicle", { code: "TRUCK" }, "Unknown"],
    ["no label", { label: "" }, "label"],
    ["a zero distance", { maxDistanceKm: "0" }, "more than 0"],
    ["a missing multiplier", { rateMultiplier: "" }, "more than 0"],
    ["more than a quote takes", { maxWeightKg: "150" }, "100 kg"],
  ])("refuses %s", async (_name, overrides, message) => {
    const result = await updateSameDayVehicle(IDLE, vehicle(overrides));

    expect(result.status).toBe("error");
    expect(result.message).toContain(message);
    expect(gatewayFetchMock).not.toHaveBeenCalled();
  });
});
