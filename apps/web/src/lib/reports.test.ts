import { describe, expect, it } from "vitest";
import {
  addDays,
  businessToday,
  countDelta,
  daysInclusive,
  formatCount,
  formatDay,
  formatPercent,
  isIsoDate,
  labelIndexes,
  niceTicks,
  periodQuery,
  previousPeriod,
  rateDelta,
  reportsHref,
  resolveFilter,
  zoneLabel,
} from "./reports";

const TODAY = "2026-10-31";

describe("businessToday", () => {
  it("uses the Colombo date, not the UTC date", () => {
    // 19:30 UTC on the 30th is 01:00 on the 31st in Colombo.
    expect(businessToday(new Date("2026-10-30T19:30:00Z"))).toBe("2026-10-31");
    expect(businessToday(new Date("2026-10-30T18:00:00Z"))).toBe("2026-10-30");
  });
});

describe("date arithmetic", () => {
  it("validates real calendar dates only", () => {
    expect(isIsoDate("2026-02-28")).toBe(true);
    expect(isIsoDate("2026-02-30")).toBe(false);
    expect(isIsoDate("31/10/2026")).toBe(false);
    expect(isIsoDate(undefined)).toBe(false);
  });

  it("adds days across month ends and counts inclusively", () => {
    expect(addDays("2026-10-31", 1)).toBe("2026-11-01");
    expect(addDays("2026-03-01", -1)).toBe("2026-02-28");
    expect(daysInclusive("2026-10-01", "2026-10-31")).toBe(31);
    expect(daysInclusive("2026-10-05", "2026-10-05")).toBe(1);
  });

  it("compares with the period of the same length just before", () => {
    expect(previousPeriod({ from: "2026-10-02", to: "2026-10-31" })).toEqual({ from: "2026-09-02", to: "2026-10-01" });
  });
});

describe("resolveFilter", () => {
  it("defaults to the last 30 days ending today", () => {
    expect(resolveFilter({}, TODAY)).toEqual({ filter: { from: "2026-10-02", to: TODAY, preset: "30d", zone: undefined } });
  });

  it("supports the presets", () => {
    expect(resolveFilter({ range: "7d" }, TODAY).filter).toMatchObject({ from: "2026-10-25", to: TODAY, preset: "7d" });
    expect(resolveFilter({ range: "90d" }, TODAY).filter.from).toBe("2026-08-03");
    expect(resolveFilter({ range: "1y" }, TODAY).filter.preset).toBe("30d");
  });

  it("accepts a valid custom range and zone", () => {
    expect(resolveFilter({ from: "2026-09-01", to: "2026-09-30", zone: "CP" }, TODAY)).toEqual({
      filter: { from: "2026-09-01", to: "2026-09-30", preset: "custom", zone: "CP" },
    });
  });

  it("falls back with a notice for reversed, too long or malformed ranges", () => {
    for (const params of [
      { from: "2026-10-05", to: "2026-10-01" },
      { from: "2025-01-01", to: "2026-06-01" },
      { from: "yesterday", to: TODAY },
      { from: "2026-10-01" },
    ]) {
      const result = resolveFilter(params, TODAY);
      expect(result.filter.preset).toBe("30d");
      expect(result.notice).toContain("not valid");
    }
  });

  it("drops a zone that is not a zone code and reads the first of repeated params", () => {
    expect(resolveFilter({ zone: "drop table;" }, TODAY).filter.zone).toBeUndefined();
    expect(resolveFilter({ range: ["7d", "90d"], zone: ["WP"] }, TODAY).filter).toMatchObject({ preset: "7d", zone: "WP" });
  });
});

describe("links and queries", () => {
  it("builds preset and custom links", () => {
    expect(reportsHref({ preset: "7d", zone: "CP" })).toBe("/admin/reports?range=7d&zone=CP");
    expect(reportsHref({ preset: "custom", from: "2026-09-01", to: "2026-09-30" })).toBe(
      "/admin/reports?from=2026-09-01&to=2026-09-30",
    );
  });

  it("builds the report API query", () => {
    expect(periodQuery({ from: "2026-10-01", to: "2026-10-31" })).toBe("from=2026-10-01&to=2026-10-31");
    expect(periodQuery({ from: "2026-10-01", to: "2026-10-31" }, "SP")).toBe("from=2026-10-01&to=2026-10-31&zone=SP");
  });
});

describe("formatting", () => {
  it("compacts only large counts", () => {
    expect(formatCount(1284)).toBe("1,284");
    expect(formatCount(12_900)).toBe("12.9K");
    expect(formatCount(0)).toBe("0");
  });

  it("formats rates and the no-attempts case", () => {
    expect(formatPercent(0.869625)).toBe("87.0%");
    expect(formatPercent(1)).toBe("100.0%");
    expect(formatPercent(null)).toBe("—");
  });

  it("formats days", () => {
    expect(formatDay("2026-10-05")).toBe("5 Oct");
    expect(formatDay("2026-10-05", "long")).toBe("Mon 5 Oct 2026");
  });

  it("names zones when pricing-service supplied a name", () => {
    expect(zoneLabel("CP", { CP: "Central" })).toBe("Central (CP)");
    expect(zoneLabel("NWP")).toBe("NWP");
    expect(zoneLabel("UNKNOWN", { CP: "Central" })).toBe("Zone not known yet");
  });
});

describe("deltas", () => {
  it("colours count changes by whether up is good", () => {
    expect(countDelta(110, 100)).toEqual({ direction: "up", tone: "good", text: "+10%" });
    expect(countDelta(95, 100)).toEqual({ direction: "down", tone: "bad", text: "-5.0%" });
    expect(countDelta(12, 10, false)).toEqual({ direction: "up", tone: "bad", text: "+20%" });
    expect(countDelta(100, 100)).toEqual({ direction: "flat", tone: "neutral", text: "No change" });
    expect(countDelta(5, 0)).toBeNull();
  });

  it("reports rate changes in percentage points", () => {
    expect(rateDelta(0.9, 0.85)).toEqual({ direction: "up", tone: "good", text: "+5.0 pts" });
    expect(rateDelta(0.8, 0.85)).toMatchObject({ direction: "down", tone: "bad" });
    expect(rateDelta(0.9, 0.9)).toMatchObject({ direction: "flat" });
    expect(rateDelta(null, 0.9)).toBeNull();
  });
});

describe("chart scales", () => {
  it("picks round ticks from zero", () => {
    expect(niceTicks(28)).toEqual([0, 10, 20, 30]);
    expect(niceTicks(7)).toEqual([0, 2, 4, 6, 8]);
    expect(niceTicks(3)).toEqual([0, 1, 2, 3]);
    expect(niceTicks(0)).toEqual([0, 1]);
    expect(niceTicks(1840)).toEqual([0, 500, 1000, 1500, 2000]);
  });

  it("thins x-axis labels but keeps the last day", () => {
    expect(labelIndexes(5, 8)).toEqual([0, 1, 2, 3, 4]);
    expect(labelIndexes(30, 6)).toEqual([4, 9, 14, 19, 24, 29]);
    expect(labelIndexes(0, 6)).toEqual([]);
  });
});
