import { describe, expect, it } from "vitest";
import {
  addDays,
  buildInsights,
  businessToday,
  countDelta,
  dailyRate,
  formatMetric,
  formatRange,
  initials,
  isValidRange,
  metricDelta,
  metricValue,
  rateTicks,
  referenceValue,
  smoothPath,
  areaPath,
  toCsv,
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
    expect(resolveFilter({ range: "mtd" }, "2026-10-14").filter).toMatchObject({ from: "2026-10-01", to: "2026-10-14" });
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
    expect(reportsHref({ preset: "7d", zone: "CP" })).toBe("?range=7d&zone=CP");
    expect(reportsHref({ preset: "custom", from: "2026-09-01", to: "2026-09-30" })).toBe(
      "?from=2026-09-01&to=2026-09-30",
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

describe("range and labels", () => {
  it("validates custom ranges like reporting-service does", () => {
    expect(isValidRange("2026-10-01", "2026-10-31")).toBe(true);
    expect(isValidRange("2026-10-31", "2026-10-01")).toBe(false);
    expect(isValidRange("2025-01-01", "2026-06-01")).toBe(false);
    expect(isValidRange(undefined, "2026-10-01")).toBe(false);
  });

  it("formats a period compactly", () => {
    expect(formatRange("2026-10-02", "2026-10-31")).toBe("2 Oct \u2013 31 Oct 2026");
    expect(formatRange("2025-12-20", "2026-01-05")).toBe("20 Dec 2025 \u2013 5 Jan 2026");
  });

  it("makes avatar initials from ids", () => {
    expect(initials("demo-courier-3")).toBe("D3");
    expect(initials("a1b2c3d4-0000-4000-8000-00000000c001")).toBe("A0");
    expect(initials("solo")).toBe("SO");
    expect(initials("--")).toBe("?");
  });
});

describe("metrics", () => {
  const day = { date: "2026-10-05", created: 10, delivered: 9, failed: 1, cancelled: 0 };

  it("derives the daily success rate", () => {
    expect(dailyRate(day)).toBe(0.9);
    expect(dailyRate({ delivered: 0, failed: 0 })).toBeNull();
    expect(metricValue(day, "successRate")).toBe(0.9);
    expect(metricValue(day, "created")).toBe(10);
  });

  it("formats each metric in its own unit", () => {
    expect(formatMetric(0.9, "successRate")).toBe("90.0%");
    expect(formatMetric(1284, "delivered")).toBe("1,284");
    expect(formatMetric(null, "failed")).toBe("\u2014");
  });

  it("uses the period rate or the daily average as the reference line", () => {
    const totals = { created: 60, delivered: 50, failed: 10, cancelled: 2, successRate: 50 / 60 };
    expect(referenceValue(totals, 10, "delivered")).toBe(5);
    expect(referenceValue(totals, 10, "successRate")).toBeCloseTo(0.8333);
    expect(referenceValue(totals, 0, "created")).toBeNull();
  });

  it("compares metrics in their own terms", () => {
    expect(metricDelta(12, 10, "failed")).toMatchObject({ direction: "up", tone: "bad" });
    expect(metricDelta(0.9, 0.8, "successRate")).toMatchObject({ text: "+10.0 pts", tone: "good" });
    expect(metricDelta(null, 3, "created")).toBeNull();
  });
});

describe("buildInsights", () => {
  const days = [
    { date: "2026-10-01", created: 10, delivered: 8, failed: 2, cancelled: 0 },
    { date: "2026-10-02", created: 12, delivered: 16, failed: 1, cancelled: 1 },
  ];
  const daily = { from: "2026-10-01", to: "2026-10-02", zone: null, days,
    totals: { created: 22, delivered: 24, failed: 3, cancelled: 1, successRate: 24 / 27 } };
  const zones = [
    { zoneCode: "CP", created: 10, delivered: 12, failed: 3, cancelled: 0, successRate: 0.8 },
    { zoneCode: "WP", created: 12, delivered: 19, failed: 1, cancelled: 1, successRate: 0.95 },
    { zoneCode: "NP", created: 1, delivered: 1, failed: 1, cancelled: 0, successRate: 0.5 },
    { zoneCode: "UNKNOWN", created: 0, delivered: 1, failed: 0, cancelled: 0, successRate: 1 },
  ];
  const couriers = [{ courierId: "demo-courier-1", delivered: 20, failed: 2, successRate: 20 / 22 }];

  it("calls out the busiest day, the weakest and strongest zones and the top courier", () => {
    const insights = buildInsights(daily, zones, couriers, { CP: "Central" });
    expect(insights.map((i) => i.id)).toEqual(["busiest-day", "lowest-zone", "best-zone", "top-courier", "unknown-zone"]);
    expect(insights[0].title).toBe("Busiest day: Fri 2 Oct 2026");
    expect(insights[0].detail).toContain("33% above the daily average");
    // NP has too few attempts to be judged, so CP is the weakest zone.
    expect(insights[1]).toMatchObject({ title: "Lowest success rate: Central (CP)", zone: "CP" });
    expect(insights[2].zone).toBe("WP");
  });

  it("compares a focused zone with all zones instead of ranking zones", () => {
    const insights = buildInsights(daily, zones, couriers, { CP: "Central" }, "CP");
    expect(insights.map((i) => i.id)).toEqual(["busiest-day", "zone-vs-all", "top-courier"]);
    expect(insights[1]).toMatchObject({ title: "Central (CP) vs all zones", kind: "best" });
    expect(insights[2].detail).toContain("across all zones");
  });

  it("says nothing about an empty period or missing sections", () => {
    const empty = { ...daily, days: [{ ...days[0], created: 0, delivered: 0, failed: 0 }],
      totals: { created: 0, delivered: 0, failed: 0, cancelled: 0, successRate: null } };
    expect(buildInsights(empty, zones, couriers)).toEqual([]);
    expect(buildInsights(daily, null, null).map((i) => i.id)).toEqual(["busiest-day"]);
  });
});

describe("toCsv", () => {
  it("quotes where needed and defuses spreadsheet formulas", () => {
    expect(toCsv(["Zone", "Delivered"], [["Central (CP)", 12], ["A, \"B\"", null], ["=HYPERLINK()", 1]])).toBe(
      'Zone,Delivered\r\nCentral (CP),12\r\n"A, ""B""",\r\n\'=HYPERLINK(),1',
    );
  });
});

describe("chart geometry", () => {
  it("starts a rate axis just below the lowest rate", () => {
    expect(rateTicks([0.82, 0.95, null])).toEqual([0.8, 0.9, 1]);
    expect(rateTicks([0.3, 0.9])).toEqual([0.2, 0.4, 0.6, 0.8, 1]);
    expect(rateTicks([null])).toEqual([0, 0.2, 0.4, 0.6, 0.8, 1]);
  });

  it("draws a monotone curve that never overshoots and breaks at gaps", () => {
    expect(smoothPath([[0, 10]])).toBe("M0,10h0");
    expect(smoothPath([[0, 10], [10, 20]])).toBe("M0,10L10,20");
    const path = smoothPath([[0, 0], [10, 10], [20, 10], null, [40, 5], [50, 0]]);
    expect(path.match(/M/g)).toHaveLength(2);
    // A flat stretch stays flat: every y between the two 10s is exactly 10.
    const flat = path.split("C")[2].split("M")[0];
    expect(flat.split(",").filter((_, i) => i % 2 === 1).map(Number)).toEqual([10, 10, 10]);
  });

  it("closes each run of the area down to the baseline and skips lone points", () => {
    expect(areaPath([[0, 10], [10, 20]], 30)).toBe("M0,10L10,20L10,30L0,30Z");
    expect(areaPath([[0, 10], null, [20, 5], [30, 8]], 30).match(/Z/g)).toHaveLength(1);
  });
});
