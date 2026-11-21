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

export const PRICING_RULES = [
  "BASE",
  "DISTANCE",
  "LINE_HAUL",
  "WEIGHT",
  "SIZE",
  "EXPRESS",
  "MINIMUM_CHARGE",
  "FUEL",
  "SPECIAL_HANDLING",
  "REMOTE_AREA",
  "MERCHANT_DISCOUNT",
  "INSURANCE",
  "COD",
  "TAX",
] as const;
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

export const VEHICLE_TYPES = ["BIKE", "VAN"] as const;
export type VehicleType = (typeof VEHICLE_TYPES)[number];

/** One handling option from GET /api/v1/pricing/special-handling. The fee is never sent to the browser. */
export interface SpecialHandlingOption {
  code: string;
  label: string;
}

/** Most handling options a single quote may carry, published by the quote contract. */
export const MAX_SPECIAL_HANDLING = 5;

export const WEIGHT_BASES = ["ACTUAL", "VOLUMETRIC"] as const;
export type WeightBasis = (typeof WEIGHT_BASES)[number];

export interface Quote {
  quoteId: string;
  fee: Money;
  breakdown: BreakdownLine[];
  fromZone: string;
  toZone: string;
  fromAddress?: string;
  fromDistrict?: string;
  toAddress?: string;
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
  /** The distance the fee was priced over. Absent when no distance applied. */
  distanceKm?: number;
  /** ESTIMATED means no routing engine answered, so the figure is approximate. */
  distanceSource?: "ROUTED" | "ESTIMATED";
  /** From the distance band. Absent for same-day, which is not scheduled by band. */
  estimatedTransitDays?: number;
  /** Same-day only: the vehicle the fee was priced for. */
  vehicleType?: VehicleType;
  /** Handling options charged for, as requested. */
  specialHandling?: string[];
  /** Short fingerprint of the configuration that produced this quote. */
  pricingVersion?: string;
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
  fromAddress: string;
  fromDistrict: string;
  toAddress: string;
  toDistrict: string;
  weightKg: string;
  sizeClass: string;
  numberOfParcels: number;
  serviceLevel: string;
  codAmount: string;
  declaredValue: string;
  /** Optional, and all three or none. Kept as strings so a half-typed value is not lost. */
  lengthCm: string;
  widthCm: string;
  heightCm: string;
  /** Same-day only. Ignored for other services. */
  vehicleType: string;
  specialHandling: string[];
  /** Exact points picked on the map. Optional; each must lie inside its district. */
  fromPoint?: GeoPick;
  toPoint?: GeoPick;
}

/** A point picked on the map, in decimal degrees. */
export interface GeoPick {
  latitude: number;
  longitude: number;
}

/** GET /api/v1/pricing/locate: the district and zone a map point is in. */
export interface LocatedPoint {
  district: string;
  zoneCode: string;
}

/** Six decimals is about 11 cm, the precision the quote contract accepts. */
export function roundCoordinate(value: number): number {
  return Math.round(value * 1e6) / 1e6;
}

/** The BFF path that resolves a map point to its district. */
export function locatePath(point: GeoPick): string {
  const query = new URLSearchParams({
    latitude: String(roundCoordinate(point.latitude)),
    longitude: String(roundCoordinate(point.longitude)),
  });
  return `api/v1/pricing/locate?${query.toString()}`;
}

/**
 * Applies a map pick: the district comes from the point, so the two always agree.
 * The server checks the same thing, so this is what keeps the form from sending a mismatch.
 */
export function withPickedPoint(
  values: QuoteFormValues,
  side: "from" | "to",
  point: GeoPick,
  located: LocatedPoint,
): QuoteFormValues {
  return side === "from"
    ? { ...values, fromDistrict: located.district, fromPoint: point }
    : { ...values, toDistrict: located.district, toPoint: point };
}

/**
 * Applies a district chosen from the list. A point picked earlier is dropped when it belongs
 * to another district, because it would no longer describe where the parcel is.
 */
export function withDistrict(
  values: QuoteFormValues,
  side: "from" | "to",
  district: string,
  pointDistrict?: string,
): QuoteFormValues {
  const keepPoint = pointDistrict !== undefined && pointDistrict === district;
  return side === "from"
    ? { ...values, fromDistrict: district, fromPoint: keepPoint ? values.fromPoint : undefined }
    : { ...values, toDistrict: district, toPoint: keepPoint ? values.toPoint : undefined };
}

export const EMPTY_QUOTE_FORM: QuoteFormValues = {
  fromAddress: "",
  fromDistrict: "",
  toAddress: "",
  toDistrict: "",
  weightKg: "",
  sizeClass: "M",
  numberOfParcels: 1,
  serviceLevel: "STANDARD",
  codAmount: "",
  declaredValue: "",
  lengthCm: "",
  widthCm: "",
  heightCm: "",
  vehicleType: "BIKE",
  specialHandling: [],
};

export interface QuoteRequestBody {
  fromAddress?: string;
  fromDistrict: string;
  toAddress?: string;
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
  /** Same-day only. */
  vehicleType?: VehicleType;
  specialHandling?: string[];
  /** Exact points, each pair together or not at all. */
  fromLatitude?: number;
  fromLongitude?: number;
  toLatitude?: number;
  toLongitude?: number;
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

  const sameDay = values.serviceLevel === "SAME_DAY";
  if (sameDay && !isVehicleType(values.vehicleType)) errors.vehicleType = "Choose a vehicle.";
  if (values.specialHandling.length > MAX_SPECIAL_HANDLING) {
    errors.specialHandling = `Choose at most ${MAX_SPECIAL_HANDLING} handling options.`;
  }

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
      ...(values.fromAddress.trim() ? { fromAddress: values.fromAddress.trim() } : {}),
      fromDistrict: from,
      ...(values.toAddress.trim() ? { toAddress: values.toAddress.trim() } : {}),
      toDistrict: to,
      weightKg,
      sizeClass: values.sizeClass as SizeClass,
      serviceLevel: values.serviceLevel as ServiceLevel,
      ...(sameDay ? { vehicleType: values.vehicleType as VehicleType } : {}),
      ...(values.specialHandling.length > 0 ? { specialHandling: [...values.specialHandling] } : {}),
      ...(hasCod ? { codAmount } : {}),
      ...(hasValue ? { declaredValue } : {}),
      ...(values.fromPoint
        ? {
            fromLatitude: roundCoordinate(values.fromPoint.latitude),
            fromLongitude: roundCoordinate(values.fromPoint.longitude),
          }
        : {}),
      ...(values.toPoint
        ? {
            toLatitude: roundCoordinate(values.toPoint.latitude),
            toLongitude: roundCoordinate(values.toPoint.longitude),
          }
        : {}),
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

/** The plain label for a distance source, shown as a badge only when the figure is approximate. */
export function distanceBadge(quote: Quote): string | undefined {
  return quote.distanceSource === "ESTIMATED" ? "estimated" : undefined;
}

/** Transit time in words, or undefined when the quote carries none. */
export function transitDaysLabel(days: number | undefined): string | undefined {
  if (days === undefined || !Number.isFinite(days) || days <= 0) return undefined;
  return days === 1 ? "1 day" : `${days} days`;
}

/** Breakdown lines worth showing. A zero line tells the customer nothing, so it is left out. */
export function visibleBreakdown(breakdown: readonly BreakdownLine[]): BreakdownLine[] {
  return breakdown.filter((line) => line.amount !== 0);
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

export function isVehicleType(value: string): value is VehicleType {
  return (VEHICLE_TYPES as readonly string[]).includes(value);
}

export function isServiceLevel(value: string): value is ServiceLevel {
  return (SERVICE_LEVELS as readonly string[]).includes(value);
}

const RULE_LABELS: Record<PricingRule, string> = {
  BASE: "Base rate",
  DISTANCE: "Distance travelled",
  LINE_HAUL: "Line haul (distance band)",
  WEIGHT: "Weight",
  SIZE: "Parcel size",
  EXPRESS: "Express delivery",
  MINIMUM_CHARGE: "Minimum charge top-up",
  FUEL: "Fuel surcharge",
  SPECIAL_HANDLING: "Special handling",
  REMOTE_AREA: "Hard-to-reach area",
  MERCHANT_DISCOUNT: "Your agreed discount",
  INSURANCE: "Cover",
  COD: "Cash on delivery",
  TAX: "Tax",
};

export function breakdownLabel(rule: PricingRule | string): string {
  return RULE_LABELS[rule as PricingRule] ?? rule;
}

const PRICING_ERROR_MESSAGES: Record<string, string> = {
  OUTSIDE_COVERAGE: "That location is outside our service area.",
  LOCATION_DISTRICT_MISMATCH: "The location you chose is not in the district you selected.",
  UNKNOWN_SPECIAL_HANDLING: "One of the handling options is not available. Please untick it and try again.",
  SAME_DAY_OUT_OF_RANGE: "That trip is too far for same-day delivery with this vehicle. Try a van or another service.",
  NO_DISTANCE_BAND: "We cannot price that distance yet. Please try another route.",
  SAME_DAY_TOO_HEAVY: "That parcel is too heavy or bulky for this vehicle. Try a van or another service.",
  VEHICLE_NOT_OFFERED: "That vehicle is not available for same-day delivery just now.",
  TOO_MANY_SPECIAL_HANDLING: "Choose fewer handling options and try again.",
  DISTANCE_UNAVAILABLE: "We could not measure that route just now. Please try again shortly.",
};

/** A customer-safe message for a pricing error code, or undefined when the code is not one of these. */
export function pricingErrorMessage(code: string | undefined): string | undefined {
  return code ? PRICING_ERROR_MESSAGES[code] : undefined;
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

export interface RateMatrixCell {
  fromZone: string;
  toZone: string;
  /** The card in force. Absent means the lane cannot be quoted. */
  card?: RateCard;
}

export interface RateMatrix {
  zones: string[];
  rows: { fromZone: string; cells: RateMatrixCell[] }[];
  /** Lanes with no card in force, which is every lane a customer would be refused on. */
  unpriced: number;
}

/**
 * Lays the cards in force out as a grid of every ordered lane between active zones.
 *
 * Should two cards overlap, the newest wins, which is the rule pricing-service applies when it
 * selects a card, so the grid shows what a quote would actually use.
 */
export function buildRateMatrix(zones: readonly Zone[], cards: readonly RateCard[], on: string): RateMatrix {
  const codes = zones.filter((zone) => zone.active).map((zone) => zone.code);
  const inForce = new Map<string, RateCard>();
  for (const card of cards) {
    if (!isInForceOn(card, on)) continue;
    const lane = `${card.fromZone}>${card.toZone}`;
    const current = inForce.get(lane);
    if (!current || card.effectiveFrom > current.effectiveFrom) inForce.set(lane, card);
  }

  const rows = codes.map((fromZone) => ({
    fromZone,
    cells: codes.map((toZone) => ({ fromZone, toZone, card: inForce.get(`${fromZone}>${toZone}`) })),
  }));
  const unpriced = rows.flatMap((row) => row.cells).filter((cell) => !cell.card).length;
  return { zones: codes, rows, unpriced };
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

/**
 * Today as the date-only string the rate-card API expects, on the Sri Lankan calendar.
 *
 * pricing-service decides which card is in force in this zone, so the screens have to agree with
 * it. UTC would show yesterday's rates between midnight and 05:30.
 */
export function isoDate(date: Date = new Date()): string {
  return date.toLocaleDateString("en-CA", { timeZone: "Asia/Colombo" });
}

/**
 * Serializes the pricing calculator form and optional quote reference into a `/customer/book?...`
 * URL so a customer moving from "Get a price" to "Book a Delivery" keeps every pricing choice.
 */
export function quoteToBookingHref(values: QuoteFormValues, quote?: Quote): string {
  const params = new URLSearchParams();
  const fromDistrict = values.fromDistrict.trim() || quote?.fromDistrict || "";
  const toDistrict = values.toDistrict.trim() || quote?.toDistrict || "";
  const weightKg = values.weightKg.trim() || (quote?.weightKg ? String(quote.weightKg) : "");

  if (quote?.quoteId) params.set("quoteId", quote.quoteId);
  if (fromDistrict) params.set("fromDistrict", fromDistrict);
  if (toDistrict) params.set("toDistrict", toDistrict);
  if (values.fromAddress.trim()) params.set("fromAddress", values.fromAddress.trim());
  if (values.toAddress.trim()) params.set("toAddress", values.toAddress.trim());
  if (weightKg) params.set("weightKg", weightKg);
  if (values.sizeClass && isSizeClass(values.sizeClass)) params.set("sizeClass", values.sizeClass);
  if (values.serviceLevel && isServiceLevel(values.serviceLevel)) params.set("serviceLevel", values.serviceLevel);
  if (values.serviceLevel === "SAME_DAY" && values.vehicleType && isVehicleType(values.vehicleType)) {
    params.set("vehicleType", values.vehicleType);
  }
  if (values.codAmount.trim()) params.set("codAmount", values.codAmount.trim());
  if (values.declaredValue.trim()) params.set("declaredValue", values.declaredValue.trim());
  if (values.lengthCm.trim()) params.set("lengthCm", values.lengthCm.trim());
  if (values.widthCm.trim()) params.set("widthCm", values.widthCm.trim());
  if (values.heightCm.trim()) params.set("heightCm", values.heightCm.trim());
  if (values.specialHandling.length > 0) params.set("specialHandling", values.specialHandling.join(","));

  if (values.fromPoint) {
    params.set("fromLat", String(roundCoordinate(values.fromPoint.latitude)));
    params.set("fromLng", String(roundCoordinate(values.fromPoint.longitude)));
  }
  if (values.toPoint) {
    params.set("toLat", String(roundCoordinate(values.toPoint.latitude)));
    params.set("toLng", String(roundCoordinate(values.toPoint.longitude)));
  }

  const query = params.toString();
  return query ? `/customer/book?${query}` : "/customer/book";
}

function firstParam(value: string | string[] | undefined): string | undefined {
  const raw = Array.isArray(value) ? value[0] : value;
  const trimmed = raw?.trim();
  return trimmed ? trimmed : undefined;
}

function parseFiniteNumberString(raw: string | undefined, allowZero: boolean): string | undefined {
  if (!raw) return undefined;
  const num = Number(raw);
  if (!Number.isFinite(num)) return undefined;
  if (allowZero ? num < 0 : num <= 0) return undefined;
  return raw;
}

function parseGeoPick(latRaw: string | undefined, lngRaw: string | undefined): GeoPick | undefined {
  if (!latRaw || !lngRaw) return undefined;
  const lat = Number(latRaw);
  const lng = Number(lngRaw);
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return undefined;
  if (lat < -90 || lat > 90 || lng < -180 || lng > 180) return undefined;
  return { latitude: roundCoordinate(lat), longitude: roundCoordinate(lng) };
}

/**
 * Restores initial pricing values passed from `/quote` query params so `/customer/book` opens with
 * consistent districts, pinned map coordinates, dimensions, and add-on options.
 */
export function quoteFromSearchParams(
  params: Record<string, string | string[] | undefined>,
): Partial<QuoteFormValues> {
  const result: Partial<QuoteFormValues> = {};

  const fromDistrict = firstParam(params.fromDistrict);
  const toDistrict = firstParam(params.toDistrict);
  const fromAddress = firstParam(params.fromAddress);
  const toAddress = firstParam(params.toAddress);
  const weightKg = parseFiniteNumberString(firstParam(params.weightKg), false);
  const sizeClass = firstParam(params.sizeClass);
  const serviceLevel = firstParam(params.serviceLevel);
  const vehicleType = firstParam(params.vehicleType);
  const codAmount = parseFiniteNumberString(firstParam(params.codAmount), true);
  const declaredValue = parseFiniteNumberString(firstParam(params.declaredValue), true);
  const lengthCm = parseFiniteNumberString(firstParam(params.lengthCm), false);
  const widthCm = parseFiniteNumberString(firstParam(params.widthCm), false);
  const heightCm = parseFiniteNumberString(firstParam(params.heightCm), false);
  const handlingRaw = firstParam(params.specialHandling);
  const fromPoint = parseGeoPick(firstParam(params.fromLat), firstParam(params.fromLng));
  const toPoint = parseGeoPick(firstParam(params.toLat), firstParam(params.toLng));

  if (fromDistrict) result.fromDistrict = fromDistrict;
  if (toDistrict) result.toDistrict = toDistrict;
  if (fromAddress) result.fromAddress = fromAddress;
  if (toAddress) result.toAddress = toAddress;
  if (weightKg) result.weightKg = weightKg;
  if (sizeClass && isSizeClass(sizeClass)) result.sizeClass = sizeClass;
  if (serviceLevel && isServiceLevel(serviceLevel)) result.serviceLevel = serviceLevel;
  if (vehicleType && isVehicleType(vehicleType)) result.vehicleType = vehicleType;
  if (codAmount) result.codAmount = codAmount;
  if (declaredValue) result.declaredValue = declaredValue;
  if (lengthCm) result.lengthCm = lengthCm;
  if (widthCm) result.widthCm = widthCm;
  if (heightCm) result.heightCm = heightCm;
  if (handlingRaw) {
    const items = handlingRaw
      .split(",")
      .map((item) => item.trim())
      .filter(Boolean)
      .slice(0, MAX_SPECIAL_HANDLING);
    if (items.length > 0) result.specialHandling = items;
  }
  if (fromPoint) result.fromPoint = fromPoint;
  if (toPoint) result.toPoint = toPoint;

  return result;
}

/** The quote id `/quote` hands to `/customer/book`, if it is a well-formed UUID. */
export function quoteIdFromSearchParams(params: Record<string, string | string[] | undefined>): string | undefined {
  const value = firstParam(params.quoteId)?.trim();
  return value && isUuid(value) ? value : undefined;
}

/**
 * Whether a carried quote can stand for the current form: it must still be valid and the form must
 * ask for exactly the same pricing request it was issued for. Anything else needs a fresh quote.
 */
export function canReuseQuote(
  carried: { quote: Quote; request: QuoteRequestBody } | null | undefined,
  request: QuoteRequestBody,
  now: Date,
): boolean {
  if (!carried) return false;
  if (quoteLifetime(carried.quote.expiresAt, now).expired) return false;
  return JSON.stringify(carried.request) === JSON.stringify(request);
}
