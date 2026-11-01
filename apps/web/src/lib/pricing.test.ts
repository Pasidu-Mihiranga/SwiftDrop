import { describe, expect, it } from "vitest";
import {
  EMPTY_QUOTE_FORM,
  MAX_WEIGHT_KG,
  breakdownLabel,
  districtOptions,
  formatEffectivePeriod,
  formatMoney,
  formatPercent,
  isInForceOn,
  isServiceLevel,
  isSizeClass,
  isoDate,
  laneLabel,
  quoteLifetime,
  distanceNotice,
  serviceLevelLabel,
  sizeClassLabel,
  sizeClassNotice,
  validateQuoteForm,
  weightExplanation,
  zoneForDistrict,
  type RateCard,
  type Zone,
} from "./pricing";

const WESTERN: Zone = { code: "WP", name: "Western", districts: ["Colombo", "Gampaha", "Kalutara"], active: true };
const CENTRAL: Zone = { code: "CP", name: "Central", districts: ["Kandy", "Matale"], active: true };
const RETIRED: Zone = { code: "ZZ", name: "Retired", districts: ["Nowhere"], active: false };

const DISTRICTS = districtOptions([WESTERN, CENTRAL]);

function form(overrides: Partial<typeof EMPTY_QUOTE_FORM> = {}) {
  return { ...EMPTY_QUOTE_FORM, fromDistrict: "Colombo", toDistrict: "Kandy", weightKg: "2", ...overrides };
}

describe("districtOptions", () => {
  it("flattens active zones into one sorted list", () => {
    expect(districtOptions([WESTERN, CENTRAL])).toEqual(["Colombo", "Gampaha", "Kalutara", "Kandy", "Matale"]);
  });

  it("leaves out districts whose zone is switched off", () => {
    expect(districtOptions([WESTERN, RETIRED])).not.toContain("Nowhere");
  });

  it("de-duplicates a district that appears twice", () => {
    const overlapping: Zone = { ...CENTRAL, districts: ["Colombo", "Kandy"] };
    expect(districtOptions([WESTERN, overlapping]).filter((d) => d === "Colombo")).toHaveLength(1);
  });

  it("copes with no coverage at all", () => {
    expect(districtOptions([])).toEqual([]);
  });
});

describe("zoneForDistrict", () => {
  it("finds the zone that serves a district", () => {
    expect(zoneForDistrict([WESTERN, CENTRAL], "Kandy")?.code).toBe("CP");
    expect(zoneForDistrict([WESTERN, CENTRAL], "Jaffna")).toBeUndefined();
  });
});

describe("validateQuoteForm", () => {
  it("accepts a complete form and builds the request body", () => {
    const result = validateQuoteForm(form({ serviceLevel: "EXPRESS", codAmount: "1500" }), DISTRICTS);

    expect(result).toEqual({
      ok: true,
      body: {
        fromDistrict: "Colombo",
        toDistrict: "Kandy",
        weightKg: 2,
        sizeClass: "M",
        serviceLevel: "EXPRESS",
        codAmount: 1500,
      },
    });
  });

  it("omits cash on delivery entirely when it is left blank", () => {
    const result = validateQuoteForm(form(), DISTRICTS);

    expect(result.ok).toBe(true);
    if (result.ok) expect(result.body).not.toHaveProperty("codAmount");
  });

  it("keeps a zero cash-on-delivery amount because that is a real choice", () => {
    const result = validateQuoteForm(form({ codAmount: "0" }), DISTRICTS);

    expect(result.ok).toBe(true);
    if (result.ok) expect(result.body.codAmount).toBe(0);
  });

  it("asks for both districts", () => {
    const result = validateQuoteForm(form({ fromDistrict: "", toDistrict: "  " }), DISTRICTS);

    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.errors.fromDistrict).toMatch(/collection district/i);
      expect(result.errors.toDistrict).toMatch(/delivery district/i);
    }
  });

  it("rejects a district outside the advertised coverage", () => {
    const result = validateQuoteForm(form({ toDistrict: "Jaffna" }), DISTRICTS);

    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.errors.toDistrict).toMatch(/do not deliver/i);
  });

  it("does not second-guess coverage before the zones have loaded", () => {
    expect(validateQuoteForm(form({ toDistrict: "Jaffna" }), []).ok).toBe(true);
  });

  it("enforces the contract's weight bounds", () => {
    const tooLight = validateQuoteForm(form({ weightKg: "0" }), DISTRICTS);
    const negative = validateQuoteForm(form({ weightKg: "-3" }), DISTRICTS);
    const missing = validateQuoteForm(form({ weightKg: "" }), DISTRICTS);
    const nonsense = validateQuoteForm(form({ weightKg: "heavy" }), DISTRICTS);
    const tooHeavy = validateQuoteForm(form({ weightKg: String(MAX_WEIGHT_KG + 0.01) }), DISTRICTS);

    for (const result of [tooLight, negative, missing, nonsense, tooHeavy]) {
      expect(result.ok).toBe(false);
      if (!result.ok) expect(result.errors.weightKg).toBeTruthy();
    }
    expect(validateQuoteForm(form({ weightKg: String(MAX_WEIGHT_KG) }), DISTRICTS).ok).toBe(true);
  });

  it("rejects a size class or service level that is not in the contract", () => {
    const badSize = validateQuoteForm(form({ sizeClass: "HUGE" }), DISTRICTS);
    const badService = validateQuoteForm(form({ serviceLevel: "TELEPORT" }), DISTRICTS);

    expect(badSize.ok).toBe(false);
    expect(badService.ok).toBe(false);
  });

  it("rejects negative cash on delivery", () => {
    const result = validateQuoteForm(form({ codAmount: "-1" }), DISTRICTS);

    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.errors.codAmount).toMatch(/negative/i);
  });

  it("trims district names before sending them", () => {
    const result = validateQuoteForm(form({ fromDistrict: " Colombo " }), DISTRICTS);

    expect(result.ok).toBe(true);
    if (result.ok) expect(result.body.fromDistrict).toBe("Colombo");
  });
});

describe("guards", () => {
  it("recognises only contract values", () => {
    expect(isSizeClass("XL")).toBe(true);
    expect(isSizeClass("xl")).toBe(false);
    expect(isServiceLevel("EXPRESS")).toBe(true);
    expect(isServiceLevel("")).toBe(false);
  });
});

describe("labels and formatting", () => {
  it("names every pricing rule and falls back to the raw code", () => {
    expect(breakdownLabel("BASE")).toBe("Base rate");
    expect(breakdownLabel("COD")).toBe("Cash on delivery");
    expect(breakdownLabel("FUEL_LEVY")).toBe("FUEL_LEVY");
  });

  it("names every size class and falls back to the raw code", () => {
    expect(sizeClassLabel("S")).toBe("Small");
    expect(sizeClassLabel("ZZ")).toBe("ZZ");
  });

  it("always shows money with two decimals", () => {
    expect(formatMoney(1344.4)).toBe("LKR 1,344.40");
    expect(formatMoney(0)).toBe("LKR 0.00");
    expect(formatMoney(475, "USD")).toBe("USD 475.00");
    expect(formatMoney(Number.NaN)).toBe("");
  });

  it("shows a cash-on-delivery fraction as a percentage", () => {
    expect(formatPercent(0.015)).toBe("1.5%");
    expect(formatPercent(0)).toBe("0%");
    expect(formatPercent(Number.NaN)).toBe("");
  });

  it("spells out an open-ended effective period", () => {
    expect(formatEffectivePeriod("2026-01-01")).toBe("2026-01-01 onwards");
    expect(formatEffectivePeriod("2026-01-01", "2026-07-01")).toBe("2026-01-01 to 2026-07-01");
  });

  it("labels a lane inside a single zone differently", () => {
    expect(laneLabel("WP", "CP")).toBe("WP to CP");
    expect(laneLabel("WP", "WP")).toBe("WP (within zone)");
  });

  it("formats a date the rate-card API accepts", () => {
    expect(isoDate(new Date("2026-06-01T12:34:56Z"))).toBe("2026-06-01");
  });
});

describe("isInForceOn", () => {
  const card: RateCard = {
    id: "1",
    fromZone: "WP",
    toZone: "CP",
    baseFee: 475,
    perKgFee: 78,
    expressMultiplier: 1.5,
    codFeePct: 0.015,
    effectiveFrom: "2026-01-01",
    effectiveTo: "2026-07-01",
  };

  it("treats the end date as an exclusive bound", () => {
    expect(isInForceOn(card, "2026-06-30")).toBe(true);
    expect(isInForceOn(card, "2026-07-01")).toBe(false);
  });

  it("excludes a card that has not started", () => {
    expect(isInForceOn(card, "2025-12-31")).toBe(false);
    expect(isInForceOn(card, "2026-01-01")).toBe(true);
  });

  it("keeps an open-ended card in force indefinitely", () => {
    expect(isInForceOn({ ...card, effectiveTo: null }, "2030-01-01")).toBe(true);
    expect(isInForceOn({ ...card, effectiveTo: undefined }, "2030-01-01")).toBe(true);
  });
});

describe("quoteLifetime", () => {
  const now = new Date("2026-06-01T09:00:00Z");

  it("counts down in minutes and seconds", () => {
    expect(quoteLifetime("2026-06-01T09:15:00Z", now)).toMatchObject({
      expired: false,
      secondsRemaining: 900,
      label: "15m 00s",
    });
    expect(quoteLifetime("2026-06-01T09:00:45Z", now).label).toBe("45s");
    expect(quoteLifetime("2026-06-01T09:01:05Z", now).label).toBe("1m 05s");
  });

  it("is expired the instant the quote runs out, matching the server", () => {
    expect(quoteLifetime("2026-06-01T09:00:00Z", now).expired).toBe(true);
    expect(quoteLifetime("2026-06-01T08:59:00Z", now).expired).toBe(true);
  });

  it("treats an unreadable expiry as expired rather than valid forever", () => {
    expect(quoteLifetime("not a date", now)).toEqual({ expired: true, secondsRemaining: 0, label: "expired" });
  });

  describe("parcel measurements", () => {
    const base = {
      ...EMPTY_QUOTE_FORM,
      fromDistrict: "Colombo",
      toDistrict: "Kandy",
      weightKg: "3",
    };
    const districts = ["Colombo", "Kandy"];

    it("insures the parcel when a value is declared", () => {
      const result = validateQuoteForm({ ...base, declaredValue: "30000" }, districts);

      expect(result.ok).toBe(true);
      if (result.ok) expect(result.body).toMatchObject({ declaredValue: 30000 });
    });

    it("sends no declared value when the customer wants no cover", () => {
      const result = validateQuoteForm(base, districts);

      expect(result.ok).toBe(true);
      if (result.ok) expect(result.body).not.toHaveProperty("declaredValue");
    });

    it("refuses a negative declared value", () => {
      const result = validateQuoteForm({ ...base, declaredValue: "-1" }, districts);

      expect(result.ok).toBe(false);
      if (!result.ok) expect(result.errors.declaredValue).toMatch(/cannot be negative/i);
    });

    it("sends the three sides when all are given", () => {
      const result = validateQuoteForm(
        { ...base, lengthCm: "60", widthCm: "40", heightCm: "30" }, districts);

      expect(result.ok).toBe(true);
      if (result.ok) {
        expect(result.body).toMatchObject({ lengthCm: 60, widthCm: 40, heightCm: 30 });
      }
    });

    it("sends no measurement fields at all when none are given", () => {
      const result = validateQuoteForm(base, districts);

      expect(result.ok).toBe(true);
      if (result.ok) {
        expect(result.body).not.toHaveProperty("lengthCm");
        expect(result.body).not.toHaveProperty("heightCm");
      }
    });

    it("refuses two sides rather than guessing the third", () => {
      const result = validateQuoteForm({ ...base, lengthCm: "60", widthCm: "40" }, districts);

      expect(result.ok).toBe(false);
      if (!result.ok) {
        expect(result.errors.heightCm).toMatch(/all three/i);
        expect(result.errors.lengthCm).toBeUndefined();
      }
    });

    it("rejects a side that is not a usable length", () => {
      const zero = validateQuoteForm({ ...base, lengthCm: "0", widthCm: "40", heightCm: "30" }, districts);
      expect(zero.ok).toBe(false);
      if (!zero.ok) expect(zero.errors.lengthCm).toMatch(/more than 0/i);

      const huge = validateQuoteForm({ ...base, lengthCm: "600", widthCm: "40", heightCm: "30" }, districts);
      expect(huge.ok).toBe(false);
      if (!huge.ok) expect(huge.errors.lengthCm).toMatch(/500 cm or less/i);
    });
  });

  describe("explaining a quote", () => {
    const priced = {
      quoteId: "id", fee: { amount: 1, currency: "LKR" }, breakdown: [],
      fromZone: "WP", toZone: "SP", weightKg: 3, sizeClass: "L" as const,
      serviceLevel: "STANDARD" as const, issuedAt: "", expiresAt: "",
    };

    it("explains a parcel billed on its volume", () => {
      expect(weightExplanation({ ...priced, weightBasis: "VOLUMETRIC", volumetricWeightKg: 14.4 }))
        .toMatch(/billed as 14.4 kg/i);
    });

    it("says nothing when the parcel was billed on its weight", () => {
      expect(weightExplanation({ ...priced, weightBasis: "ACTUAL", volumetricWeightKg: 1.8 }))
        .toBeUndefined();
      expect(weightExplanation(priced)).toBeUndefined();
    });

    it("says when the measurements moved the parcel out of the class chosen", () => {
      expect(sizeClassNotice({ ...priced, declaredSizeClass: "S" }))
        .toMatch(/priced as Large \(L\).*do not fit Small \(S\)/i);
    });

    it("warns that a same-day fee was estimated rather than measured", () => {
      expect(distanceNotice({ ...priced, distanceSource: "ESTIMATED", distanceKm: 24.8 }))
        .toMatch(/about 24.8 km.*estimate/is);
    });

    it("says nothing when the route was actually measured", () => {
      expect(distanceNotice({ ...priced, distanceSource: "ROUTED", distanceKm: 24.8 }))
        .toBeUndefined();
      expect(distanceNotice(priced)).toBeUndefined();
    });

    it("names every service level a customer can pick", () => {
      expect(serviceLevelLabel("SAME_DAY")).toBe("Same day");
      expect(serviceLevelLabel("EXPRESS")).toBe("Express");
      expect(serviceLevelLabel("STANDARD")).toBe("Standard");
    });

    it("says nothing when the class charged is the class chosen", () => {
      expect(sizeClassNotice({ ...priced, declaredSizeClass: "L" })).toBeUndefined();
      expect(sizeClassNotice(priced)).toBeUndefined();
    });
  });
});
