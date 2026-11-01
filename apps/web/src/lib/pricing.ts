/**
 * Pricing types and pure helpers shared by the public quote page and the admin pricing screens.
 *
 * Everything here is a pure function so it can be unit tested without a browser. Nothing in this
 * file knows a zone code, a district or a fee: that data always comes from pricing-service, which
 * reads it from the pricing database.
 */

export const SIZE_CLASSES = ["S", "M", "L", "XL"] as const;
export type SizeClass = (typeof SIZE_CLASSES)[number];

export const SERVICE_LEVELS = ["STANDARD", "EXPRESS", "SAME_DAY"] as const;
export type ServiceLevel = (typeof SERVICE_LEVELS)[number];

export const PRICING_RULES = ["BASE", "DISTANCE", "WEIGHT", "SIZE", "EXPRESS", "REMOTE_AREA", "MERCHANT_DISCOUNT", "INSURANCE", "COD"] as const;
export type PricingRule = (typeof PRICING_RULES)[number];

/** Upper bound published by the quote contract. */
export const MAX_WEIGHT_KG = 100;

/** Upper bound for one side of a parcel, published by the quote contract. */
export const MAX_SIDE_CM = 500;

export interface Zone {
  id?: string;
  code: string;
  name: string;
  districts: string[];
  active: boolean;
}

export interface DistrictAssignment {
  district: string;
  zoneCode: string;
  zoneName: string;
}

export interface Money {
  amount: number;
  currency: string;
}

export interface BreakdownLine {
  rule: PricingRule;
  amount: number;
}

export const WEIGHT_BASES = ["ACTUAL", "VOLUMETRIC"] as const;
export type WeightBasis = (typeof WEIGHT_BASES)[number];

export interface Quote {
  quoteId: string;
  fee: Money;
  breakdown: BreakdownLine[];
  fromZone: string;
  toZone: string;
  fromDistrict?: string;
  toDistrict?: string;
  weightKg: number;
  /** What the parcel was billed as. Equals weightKg unless it is bulky. */
  chargeableWeightKg?: number;
  /** What the parcel's volume came to. Absent when no measurements were given. */
  volumetricWeightKg?: number;
  weightBasis?: WeightBasis;
  /** The class charged for, which may not be the one selected. */
  sizeClass: SizeClass;
  /** Present only when measurements moved the parcel out of the class selected. */
  declaredSizeClass?: SizeClass;
  serviceLevel: ServiceLevel;
  codAmount?: number;
  /** What the parcel was insured for. Absent when sent without cover. */
  declaredValue?: number;
  /** The negotiated terms applied. Absent for a quote at the counter rate. */
  agreementId?: string;
  /** How far a same-day job travels. Absent for a zone-priced quote. */
  distanceKm?: number;
  /** ESTIMATED means no routing engine answered, so the figure is approximate. */
  distanceSource?: "ROUTED" | "ESTIMATED";
  issuedAt: string;
  expiresAt: string;
}

export interface RateCard {
  id: string;
  fromZone: string;
  toZone: string;
  baseFee: number;
  perKgFee: number;
  expressMultiplier: number;
  /** Fraction of the cash collected, not a percentage. */
  codFeePct: number;
  effectiveFrom: string;
  effectiveTo?: string | null;
  /** Marginal weight bands. Empty means perKgFee applies to every kilogram. */
  tiers?: WeightBand[];
}

/** One weight band. A null limit is the open-ended last band. */
export interface WeightBand {
  upToKg: number | null;
  perKgFee: number;
}

/** A charge for serving a hard-to-reach district, versioned like a rate card. */
export interface RemoteAreaCharge {
  district: string;
  flatFee: number;
  effectiveFrom: string;
  effectiveTo?: string | null;
}

export interface LaneOverride {
  fromZone: string;
  toZone: string;
  /** Replaces the agreement's discount on this lane. A fraction, not a percentage. */
  discountPct?: number | null;
  /** Replaces the rate card's base fee on this lane. */
  flatBaseFee?: number | null;
}

/** A merchant's negotiated terms, versioned like a rate card. */
export interface MerchantAgreement {
  id: string;
  merchantId: string;
  /** A fraction, not a percentage. */
  discountPct: number;
  discountFlat: number;
  waivesRemoteArea: boolean;
  waivesInsurance: boolean;
  waivesCodFee: boolean;
  laneOverrides: LaneOverride[];
  effectiveFrom: string;
  effectiveTo?: string | null;
}

export interface SizeClassRate {
  code: SizeClass;
  maxWeightKg: number;
  /** Display label, rendered from the three measurements below. */
  maxDimensionsCm: string;
  maxLengthCm: number;
  maxWidthCm: number;
  maxHeightCm: number;
  multiplier: number;
}

export interface QuoteFormValues {
  fromDistrict: string;
  toDistrict: string;
  weightKg: string;
  sizeClass: string;
  serviceLevel: string;
  codAmount: string;
  declaredValue: string;
  /** Optional, and all three or none. Kept as strings so a half-typed value is not lost. */
  lengthCm: string;
  widthCm: string;
  heightCm: string;
}

export const EMPTY_QUOTE_FORM: QuoteFormValues = {
  fromDistrict: "",
  toDistrict: "",
  weightKg: "",
  sizeClass: "M",
  serviceLevel: "STANDARD",
  codAmount: "",
  declaredValue: "",
  lengthCm: "",
  widthCm: "",
  heightCm: "",
};

export interface QuoteRequestBody {
  fromDistrict: string;
  toDistrict: string;
  weightKg: number;
  sizeClass: SizeClass;
  serviceLevel: ServiceLevel;
  codAmount?: number;
  /** What the parcel is worth. Omit to send without cover. */
  declaredValue?: number;
  /** All three together or none. The API rejects a partial set. */
  lengthCm?: number;
  widthCm?: number;
  heightCm?: number;
}

export type QuoteFieldErrors = Partial<Record<keyof QuoteFormValues, string>>;

export type QuoteValidation =
  | { ok: true; body: QuoteRequestBody }
  | { ok: false; errors: QuoteFieldErrors };

/** Every serviced district, from the zones the API advertises, sorted and de-duplicated. */
export function districtOptions(zones: readonly Zone[]): string[] {
  const districts = new Set<string>();
  for (const zone of zones) {
    if (!zone.active) continue;
    for (const district of zone.districts) districts.add(district);
  }
  return [...districts].sort((left, right) => left.localeCompare(right));
}

/** Which zone serves a district, or undefined when it is not covered. */
export function zoneForDistrict(zones: readonly Zone[], district: string): Zone | undefined {
  return zones.find((zone) => zone.districts.includes(district));
}

/**
 * Checks the form the same way the API does, so an obviously bad request never leaves the browser.
 * The server remains the authority; this only saves a round trip and gives field-level messages.
 */
export function validateQuoteForm(values: QuoteFormValues, districts: readonly string[]): QuoteValidation {
  const errors: QuoteFieldErrors = {};
  const from = values.fromDistrict.trim();
  const to = values.toDistrict.trim();

  if (!from) errors.fromDistrict = "Choose a collection district.";
  else if (districts.length > 0 && !districts.includes(from)) errors.fromDistrict = "We do not deliver from there yet.";

  if (!to) errors.toDistrict = "Choose a delivery district.";
  else if (districts.length > 0 && !districts.includes(to)) errors.toDistrict = "We do not deliver there yet.";

  const weightKg = Number(values.weightKg);
  if (values.weightKg.trim() === "") errors.weightKg = "Enter the parcel weight.";
  else if (!Number.isFinite(weightKg) || weightKg <= 0) errors.weightKg = "Weight must be more than 0 kg.";
  else if (weightKg > MAX_WEIGHT_KG) errors.weightKg = `Weight must be ${MAX_WEIGHT_KG} kg or less.`;

  if (!isSizeClass(values.sizeClass)) errors.sizeClass = "Choose a parcel size.";
  if (!isServiceLevel(values.serviceLevel)) errors.serviceLevel = "Choose a service level.";

  const hasCod = values.codAmount.trim() !== "";
  const codAmount = Number(values.codAmount);
  if (hasCod && (!Number.isFinite(codAmount) || codAmount < 0)) {
    errors.codAmount = "Cash to collect cannot be negative.";
  }

  const hasValue = values.declaredValue.trim() !== "";
  const declaredValue = Number(values.declaredValue);
  if (hasValue && (!Number.isFinite(declaredValue) || declaredValue < 0)) {
    errors.declaredValue = "Declared value cannot be negative.";
  }

  // All three sides or none, the same rule the API applies. Two sides would mean guessing
  // the third, and a guess that lowers a price is not one worth making.
  const sides = [
    ["lengthCm", values.lengthCm],
    ["widthCm", values.widthCm],
    ["heightCm", values.heightCm],
  ] as const;
  const given = sides.filter(([, value]) => value.trim() !== "");
  if (given.length > 0 && given.length < 3) {
    for (const [field, value] of sides) {
      if (value.trim() === "") errors[field] = "Give all three measurements, or leave all blank.";
    }
  }
  for (const [field, value] of given) {
    const side = Number(value);
    if (!Number.isFinite(side) || side <= 0) errors[field] = "Must be more than 0 cm.";
    else if (side > MAX_SIDE_CM) errors[field] = `Must be ${MAX_SIDE_CM} cm or less.`;
  }
  const hasDimensions = given.length === 3 && !sides.some(([field]) => errors[field]);

  if (Object.keys(errors).length > 0) return { ok: false, errors };

  return {
    ok: true,
    body: {
      fromDistrict: from,
      toDistrict: to,
      weightKg,
      sizeClass: values.sizeClass as SizeClass,
      serviceLevel: values.serviceLevel as ServiceLevel,
      ...(hasCod ? { codAmount } : {}),
      ...(hasValue ? { declaredValue } : {}),
      ...(hasDimensions
        ? {
            lengthCm: Number(values.lengthCm),
            widthCm: Number(values.widthCm),
            heightCm: Number(values.heightCm),
          }
        : {}),
    },
  };
}

/**
 * How the billed weight came about, or undefined when there is nothing to explain.
 *
 * A customer who sends three kilograms and is charged for fifteen deserves a sentence saying
 * why, rather than being left to work it out from the total.
 */
export function weightExplanation(quote: Quote): string | undefined {
  if (quote.weightBasis !== "VOLUMETRIC" || quote.volumetricWeightKg === undefined) {
    return undefined;
  }
  return `Billed as ${quote.volumetricWeightKg} kg. A parcel this size takes up more room in the van than its ${quote.weightKg} kg suggests, so it is charged on the space it occupies.`;
}

/**
 * How a same-day fee was measured, when it is worth saying.
 *
 * Only shown for an estimate: a customer about to pay should know the distance is a straight
 * line scaled by a road factor rather than a real route, because the real road may be longer.
 */
export function distanceNotice(quote: Quote): string | undefined {
  if (quote.distanceSource !== "ESTIMATED" || quote.distanceKm === undefined) {
    return undefined;
  }
  return `Priced over about ${quote.distanceKm} km. We could not measure the road route, so this is an estimate from the straight-line distance and the final fee may differ.`;
}

/** The notice shown when measurements put the parcel in a class the customer did not pick. */
export function sizeClassNotice(quote: Quote): string | undefined {
  if (!quote.declaredSizeClass || quote.declaredSizeClass === quote.sizeClass) {
    return undefined;
  }
  return `Priced as ${sizeClassLabel(quote.sizeClass)} (${quote.sizeClass}). The measurements you gave do not fit ${sizeClassLabel(quote.declaredSizeClass)} (${quote.declaredSizeClass}).`;
}

export function isSizeClass(value: string): value is SizeClass {
  return (SIZE_CLASSES as readonly string[]).includes(value);
}

export function isServiceLevel(value: string): value is ServiceLevel {
  return (SERVICE_LEVELS as readonly string[]).includes(value);
}

const RULE_LABELS: Record<PricingRule, string> = {
  BASE: "Base rate",
  DISTANCE: "Distance travelled",
  WEIGHT: "Weight",
  SIZE: "Parcel size",
  EXPRESS: "Express delivery",
  REMOTE_AREA: "Hard-to-reach area",
  MERCHANT_DISCOUNT: "Your agreed discount",
  INSURANCE: "Cover",
  COD: "Cash on delivery",
};

export function breakdownLabel(rule: PricingRule | string): string {
  return RULE_LABELS[rule as PricingRule] ?? rule;
}

const SERVICE_LEVEL_LABELS: Record<ServiceLevel, string> = {
  STANDARD: "Standard",
  EXPRESS: "Express",
  SAME_DAY: "Same day",
};

export function serviceLevelLabel(level: ServiceLevel): string {
  return SERVICE_LEVEL_LABELS[level] ?? level;
}

const SIZE_CLASS_LABELS: Record<SizeClass, string> = {
  S: "Small",
  M: "Medium",
  L: "Large",
  XL: "Extra large",
};

export function sizeClassLabel(code: SizeClass | string): string {
  return SIZE_CLASS_LABELS[code as SizeClass] ?? code;
}

/** Money as the customer should read it. Amounts are always LKR with two decimals. */
export function formatMoney(amount: number, currency = "LKR"): string {
  if (!Number.isFinite(amount)) return "";
  return `${currency} ${amount.toLocaleString("en-LK", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
}

/** A rate card's cash-on-delivery fraction shown as a percentage. */
export function formatPercent(fraction: number): string {
  if (!Number.isFinite(fraction)) return "";
  return `${(fraction * 100).toLocaleString("en-LK", { maximumFractionDigits: 2 })}%`;
}

export interface QuoteLifetime {
  expired: boolean;
  secondsRemaining: number;
  label: string;
}

/**
 * How long a quote has left. The server is the authority on expiry, so this only decides what the
 * page shows; a stale quote is rejected on lookup regardless of what the browser believes.
 */
export function quoteLifetime(expiresAt: string, now: Date = new Date()): QuoteLifetime {
  const expiry = Date.parse(expiresAt);
  if (Number.isNaN(expiry)) return { expired: true, secondsRemaining: 0, label: "expired" };

  const secondsRemaining = Math.max(0, Math.floor((expiry - now.getTime()) / 1000));
  if (secondsRemaining === 0) return { expired: true, secondsRemaining: 0, label: "expired" };

  const minutes = Math.floor(secondsRemaining / 60);
  const seconds = secondsRemaining % 60;
  const label = minutes > 0 ? `${minutes}m ${String(seconds).padStart(2, "0")}s` : `${seconds}s`;
  return { expired: false, secondsRemaining, label };
}

/** A rate card's effective window, with the open-ended case spelled out. */
export function formatEffectivePeriod(effectiveFrom: string, effectiveTo?: string | null): string {
  return effectiveTo ? `${effectiveFrom} to ${effectiveTo}` : `${effectiveFrom} onwards`;
}

/** Whether a versioned row governs quotes on the given day. The end date is an exclusive bound. */
export function isInForceOn(row: { effectiveFrom: string; effectiveTo?: string | null }, on: string): boolean {
  if (row.effectiveFrom > on) return false;
  return !row.effectiveTo || row.effectiveTo > on;
}

/** Where a versioned row stands today, in the words the admin screens use. */
export function periodStatus(row: { effectiveFrom: string; effectiveTo?: string | null }, on: string): string {
  if (isInForceOn(row, on)) return "In force";
  return row.effectiveFrom > on ? "Scheduled" : "Superseded";
}

/** A card's weight charge in a sentence, or undefined when it has no bands. */
export function describeWeightBands(tiers: readonly WeightBand[] | undefined): string | undefined {
  if (!tiers || tiers.length === 0) return undefined;
  return tiers
    .map((band, index) => {
      const rate = `${formatMoney(band.perKgFee)} per kg`;
      if (band.upToKg === null) return index === 0 ? rate : `then ${rate}`;
      return index === 0 ? `${rate} up to ${band.upToKg} kg` : `then ${rate} up to ${band.upToKg} kg`;
    })
    .join(", ");
}

/**
 * Turns the band rows of the rate-card form into what the API takes, or says why it cannot.
 *
 * Blank rows are skipped, so an administrator fills in as many as the lane needs. The rate for
 * everything above the last limit is asked for separately, because the API requires the last
 * band to be open ended and a form should not leave that to be remembered.
 */
export function parseWeightBands(
  rows: readonly { upToKg: string; perKgFee: string }[],
  rateAboveLastBand: string,
): { ok: true; tiers: WeightBand[] } | { ok: false; message: string } {
  const filled = rows.filter((row) => row.upToKg.trim() !== "" || row.perKgFee.trim() !== "");
  const rest = rateAboveLastBand.trim();
  if (filled.length === 0) {
    return rest === ""
      ? { ok: true, tiers: [] }
      : { ok: false, message: "Add at least one band limit, or clear the rate above the last band." };
  }
  if (filled.some((row) => row.upToKg.trim() === "" || row.perKgFee.trim() === "")) {
    return { ok: false, message: "Each weight band needs both a limit and a rate." };
  }
  if (rest === "") return { ok: false, message: "Give the rate for weight above the last band." };

  const tiers: WeightBand[] = filled.map((row) => ({ upToKg: Number(row.upToKg), perKgFee: Number(row.perKgFee) }));
  tiers.push({ upToKg: null, perKgFee: Number(rest) });
  if (tiers.some((band) => !Number.isFinite(band.perKgFee) || band.perKgFee < 0)) {
    return { ok: false, message: "Band rates must be numbers of 0 or more." };
  }
  const limits = filled.map((row) => Number(row.upToKg));
  if (limits.some((limit, index) => !Number.isFinite(limit) || limit <= 0 || (index > 0 && limit <= limits[index - 1]))) {
    return { ok: false, message: "Band limits must be more than 0 and go up from one band to the next." };
  }
  return { ok: true, tiers };
}

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Whether a merchant identifier is worth sending to the API at all. */
export function isUuid(value: string): boolean {
  return UUID_PATTERN.test(value.trim());
}

/** What an agreement changes, in the words the admin list uses. */
export function describeAgreement(agreement: MerchantAgreement): string {
  const parts: string[] = [];
  if (agreement.discountPct > 0) parts.push(`${formatPercent(agreement.discountPct)} off`);
  if (agreement.discountFlat > 0) parts.push(`${formatMoney(agreement.discountFlat)} off each parcel`);
  if (agreement.waivesRemoteArea) parts.push("no remote-area charge");
  if (agreement.waivesInsurance) parts.push("no cover charge");
  if (agreement.waivesCodFee) parts.push("no cash-on-delivery fee");
  if (agreement.laneOverrides.length > 0) {
    parts.push(`${agreement.laneOverrides.length} negotiated ${agreement.laneOverrides.length === 1 ? "lane" : "lanes"}`);
  }
  return parts.length > 0 ? parts.join(", ") : "Standard rates";
}

export function laneLabel(fromZone: string, toZone: string): string {
  return fromZone === toZone ? `${fromZone} (within zone)` : `${fromZone} to ${toZone}`;
}

/** Today as the date-only string the rate-card API expects. */
export function isoDate(date: Date = new Date()): string {
  return date.toISOString().slice(0, 10);
}
