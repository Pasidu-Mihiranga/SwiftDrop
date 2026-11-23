import { describe, expect, it } from "vitest";
import {
  bandSets,
  describeBandRange,
  formatCutoff,
  fractionToPercentText,
  parseDistanceBands,
  percentToFraction,
  pricingErrorMessage,
  settingsInForce,
  type DistanceBandVersion,
  type PricingSettingsVersion,
} from "./pricing";

const row = (upToKm: string, lineHaulFee = "0", standardDays = "2", expressDays = "1") => ({
  upToKm,
  lineHaulFee,
  standardDays,
  expressDays,
});
const blank = row("", "", "", "");

describe("percentages on the fee settings form", () => {
  it("turns a typed percentage into the fraction the API takes, without float noise", () => {
    expect(percentToFraction("18")).toBe(0.18);
    expect(percentToFraction(" 7.25 ")).toBe(0.0725);
    expect(percentToFraction("0")).toBe(0);
  });

  it("refuses anything that is not a percentage below 100", () => {
    for (const text of ["", "  ", "100", "-1", "abc"]) {
      expect(percentToFraction(text)).toBeUndefined();
    }
  });

  it("shows a stored fraction back as the percentage typed", () => {
    expect(fractionToPercentText(0.18)).toBe("18");
    expect(fractionToPercentText(0.0725)).toBe("7.25");
  });
});

describe("the same-day cut-off", () => {
  it("is shown as hours and minutes, or as no cut-off", () => {
    expect(formatCutoff("14:00:00")).toBe("14:00");
    expect(formatCutoff(null)).toBe("No cut-off");
    expect(formatCutoff(undefined)).toBe("No cut-off");
  });

  it("has a plain message when a same-day quote is refused for it", () => {
    expect(pricingErrorMessage("SAME_DAY_CUTOFF_PASSED")).toContain("closed");
  });
});

describe("settingsInForce", () => {
  const version = (effectiveFrom: string, effectiveTo: string | null): PricingSettingsVersion => ({
    id: effectiveFrom,
    fuelSurchargePct: 0.05,
    taxPct: 0.18,
    minimumCharge: 350,
    sameDayCutoff: null,
    effectiveFrom,
    effectiveTo,
  });

  it("picks the version covering the day, end date exclusive", () => {
    const versions = [version("2027-01-01", null), version("2026-01-01", "2027-01-01")];

    expect(settingsInForce(versions, "2026-12-31")?.id).toBe("2026-01-01");
    expect(settingsInForce(versions, "2027-01-01")?.id).toBe("2027-01-01");
    expect(settingsInForce(versions, "2025-06-01")).toBeUndefined();
  });
});

describe("bandSets", () => {
  const band = (minKm: number, maxKm: number | null, effectiveFrom: string, effectiveTo: string | null = null): DistanceBandVersion => ({
    id: `${effectiveFrom}-${minKm}`,
    minKm,
    maxKm,
    lineHaulFee: 0,
    standardTransitDays: 1,
    expressTransitDays: 1,
    effectiveFrom,
    effectiveTo,
  });

  it("groups bands by the set they were published in, newest first and each by distance", () => {
    const sets = bandSets([
      band(30, null, "2026-01-01", "2027-01-01"),
      band(0, 30, "2026-01-01", "2027-01-01"),
      band(50, null, "2027-01-01"),
      band(0, 50, "2027-01-01"),
      band(0, null, "2026-06-01", "2026-06-02"),
    ]);

    expect(sets.map((set) => set.effectiveFrom)).toEqual(["2027-01-01", "2026-06-01", "2026-01-01"]);
    expect(sets[0].bands.map((member) => member.minKm)).toEqual([0, 50]);
    expect(sets[2].effectiveTo).toBe("2027-01-01");
    expect(sets[0].effectiveTo).toBeNull();
    expect(bandSets([])).toEqual([]);
  });

  it("describes a range, including the open end", () => {
    expect(describeBandRange({ minKm: 0, maxKm: 30 })).toBe("0 to 30 km");
    expect(describeBandRange({ minKm: 300, maxKm: null })).toBe("300 km and over");
  });
});

describe("parseDistanceBands", () => {
  it("chains each band from the end of the previous one and leaves the last open", () => {
    const result = parseDistanceBands([row("30"), blank, row("100", "150"), row("", "300", "3", "2"), blank]);

    expect(result).toEqual({
      ok: true,
      bands: [
        { minKm: 0, maxKm: 30, lineHaulFee: 0, standardTransitDays: 2, expressTransitDays: 1 },
        { minKm: 30, maxKm: 100, lineHaulFee: 150, standardTransitDays: 2, expressTransitDays: 1 },
        { minKm: 100, maxKm: null, lineHaulFee: 300, standardTransitDays: 3, expressTransitDays: 2 },
      ],
    });
  });

  it("accepts a single open band", () => {
    expect(parseDistanceBands([row("")])).toEqual({
      ok: true,
      bands: [{ minKm: 0, maxKm: null, lineHaulFee: 0, standardTransitDays: 2, expressTransitDays: 1 }],
    });
  });

  it.each([
    ["no bands", [blank, blank], "at least one"],
    ["a last band with an end", [row("30")], "Leave the last band"],
    ["an earlier band without an end", [row(""), row("")], "Only the last band"],
    ["ends that go down", [row("50"), row("40"), row("")], "go up"],
    ["an end that is not a number", [row("abc"), row("")], "go up"],
    ["a missing fee", [row("", "")], "line-haul fee"],
    ["a negative fee", [row("", "-5")], "line-haul fee"],
    ["fractional days", [row("", "0", "1.5", "1")], "whole numbers"],
    ["too many days", [row("", "0", "31", "1")], "whole numbers"],
    ["express slower than standard", [row("", "0", "1", "2")], "Express cannot"],
  ])("refuses %s", (_name, rows, message) => {
    const result = parseDistanceBands(rows);

    expect(result.ok).toBe(false);
    expect(!result.ok && result.message).toContain(message);
  });
});
