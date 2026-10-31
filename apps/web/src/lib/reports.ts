/**
 * Reports dashboard helpers: the filter in the URL, period arithmetic, number formatting and
 * chart scales. Pure functions, so they run on the server and in tests alike.
 * Response types mirror contracts/openapi/reporting-service.v1.yaml.
 */

export interface Totals {
  created: number;
  delivered: number;
  failed: number;
  cancelled: number;
  successRate: number | null;
}

export interface Day {
  date: string;
  created: number;
  delivered: number;
  failed: number;
  cancelled: number;
}

export interface DailyReport {
  from: string;
  to: string;
  zone: string | null;
  totals: Totals;
  days: Day[];
}

export interface ZoneReport extends Totals {
  zoneCode: string;
}

export interface CourierReport {
  courierId: string;
  delivered: number;
  failed: number;
  successRate: number | null;
}

/** Reports count days in the operator's time zone, like reporting-service does. */
export const REPORT_TIME_ZONE = "Asia/Colombo";
export const MAX_DAYS = 366;
export const UNKNOWN_ZONE = "UNKNOWN";

export const PRESETS = { "7d": 7, "30d": 30, "90d": 90 } as const;
export type Preset = keyof typeof PRESETS | "custom";
const DEFAULT_PRESET: keyof typeof PRESETS = "30d";

export interface ReportFilter {
  from: string;
  to: string;
  preset: Preset;
  zone?: string;
}

type SearchParams = Record<string, string | string[] | undefined>;

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
const ZONE_CODE = /^[A-Z0-9-]{1,32}$/;
const DAY_MS = 86_400_000;

/** Today's date (YYYY-MM-DD) in the reporting time zone. */
export function businessToday(now: Date = new Date(), timeZone: string = REPORT_TIME_ZONE): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone, year: "numeric", month: "2-digit", day: "2-digit" }).format(now);
}

export function isIsoDate(value: string | undefined): value is string {
  if (!value || !ISO_DATE.test(value)) return false;
  return new Date(`${value}T00:00:00Z`).toISOString().startsWith(value);
}

export function addDays(date: string, days: number): string {
  return new Date(Date.parse(`${date}T00:00:00Z`) + days * DAY_MS).toISOString().slice(0, 10);
}

/** Number of days from `from` to `to`, both included. */
export function daysInclusive(from: string, to: string): number {
  return Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / DAY_MS) + 1;
}

/**
 * Reads the filter from the page's search params. Anything invalid falls back to the default
 * period with a notice, so a hand-edited URL never produces an error page.
 */
export function resolveFilter(params: SearchParams, today: string): { filter: ReportFilter; notice?: string } {
  const zoneParam = first(params.zone);
  const zone = zoneParam && ZONE_CODE.test(zoneParam) ? zoneParam : undefined;
  const from = first(params.from);
  const to = first(params.to);

  if (from !== undefined || to !== undefined) {
    if (isIsoDate(from) && isIsoDate(to) && from <= to && daysInclusive(from, to) <= MAX_DAYS) {
      return { filter: { from, to, preset: "custom", zone } };
    }
    return {
      filter: presetFilter(DEFAULT_PRESET, today, zone),
      notice: `That date range is not valid (the start must be on or before the end, at most ${MAX_DAYS} days). Showing the last 30 days instead.`,
    };
  }

  const range = first(params.range);
  const preset = range && range in PRESETS ? (range as Preset) : DEFAULT_PRESET;
  return { filter: presetFilter(preset, today, zone) };
}

function presetFilter(preset: Preset, today: string, zone?: string): ReportFilter {
  const days = preset === "custom" ? PRESETS[DEFAULT_PRESET] : PRESETS[preset];
  return { from: addDays(today, -(days - 1)), to: today, preset, zone };
}

/** The period of the same length that ends the day before `filter` starts; the baseline for deltas. */
export function previousPeriod(filter: Pick<ReportFilter, "from" | "to">): { from: string; to: string } {
  const length = daysInclusive(filter.from, filter.to);
  return { from: addDays(filter.from, -length), to: addDays(filter.from, -1) };
}

/** Link to the reports page for a preset or a custom range, keeping the zone unless told otherwise. */
export function reportsHref(target: { preset: Preset; from?: string; to?: string; zone?: string }): string {
  const query = new URLSearchParams();
  if (target.preset === "custom") {
    if (target.from) query.set("from", target.from);
    if (target.to) query.set("to", target.to);
  } else {
    query.set("range", target.preset);
  }
  if (target.zone) query.set("zone", target.zone);
  return `/admin/reports?${query.toString()}`;
}

/** Report API query string for a period, e.g. `from=2026-10-01&to=2026-10-31&zone=CP`. */
export function periodQuery(period: { from: string; to: string }, zone?: string): string {
  const query = new URLSearchParams({ from: period.from, to: period.to });
  if (zone) query.set("zone", zone);
  return query.toString();
}

// ---- formatting ------------------------------------------------------------------------------

const FULL = new Intl.NumberFormat("en-US");
const COMPACT = new Intl.NumberFormat("en-US", { notation: "compact", maximumFractionDigits: 1 });

/** 1,284 below ten thousand, then 12.9K / 1.2M. */
export function formatCount(value: number): string {
  return Math.abs(value) < 10_000 ? FULL.format(value) : COMPACT.format(value);
}

/** 0.8696 -> "87.0%"; null (nothing attempted) -> an em dash. */
export function formatPercent(rate: number | null): string {
  return rate === null ? "—" : `${(rate * 100).toFixed(1)}%`;
}

/** "5 Oct" for axes, "Mon 5 Oct 2026" for tooltips and tables. */
export function formatDay(date: string, style: "short" | "long" = "short"): string {
  const options: Intl.DateTimeFormatOptions =
    style === "short"
      ? { day: "numeric", month: "short", timeZone: "UTC" }
      : { weekday: "short", day: "numeric", month: "short", year: "numeric", timeZone: "UTC" };
  return new Intl.DateTimeFormat("en-GB", options).format(new Date(`${date}T00:00:00Z`)).replace(",", "");
}

export function zoneLabel(code: string, names: Record<string, string> = {}): string {
  if (code === UNKNOWN_ZONE) return "Zone not known yet";
  return names[code] ? `${names[code]} (${code})` : code;
}

// ---- deltas against the previous period ------------------------------------------------------

export interface Delta {
  direction: "up" | "down" | "flat";
  /** Whether the change is good news, given whether "up" is good for this measure. */
  tone: "good" | "bad" | "neutral";
  text: string;
}

/** Percentage change of a count; null when the previous period had nothing to compare with. */
export function countDelta(current: number, previous: number, upIsGood = true): Delta | null {
  if (previous === 0) return null;
  const change = ((current - previous) / previous) * 100;
  if (Math.abs(change) < 0.5) return { direction: "flat", tone: "neutral", text: "No change" };
  const magnitude = Math.abs(change) < 10 ? Math.abs(change).toFixed(1) : Math.round(Math.abs(change)).toString();
  return toDelta(change > 0, upIsGood, `${change > 0 ? "+" : "-"}${magnitude}%`);
}

/** Change of a rate in percentage points; null when either period has no rate. */
export function rateDelta(current: number | null, previous: number | null): Delta | null {
  if (current === null || previous === null) return null;
  const points = (current - previous) * 100;
  if (Math.abs(points) < 0.05) return { direction: "flat", tone: "neutral", text: "No change" };
  return toDelta(points > 0, true, `${points > 0 ? "+" : "-"}${Math.abs(points).toFixed(1)} pts`);
}

function toDelta(up: boolean, upIsGood: boolean, text: string): Delta {
  return { direction: up ? "up" : "down", tone: up === upIsGood ? "good" : "bad", text };
}

// ---- chart scales ----------------------------------------------------------------------------

/** Round axis ticks from 0 for counts: about four intervals, steps of 1, 2 or 5 times a power of ten. */
export function niceTicks(max: number, intervals = 4): number[] {
  if (!(max > 0)) return [0, 1];
  const raw = max / intervals;
  const magnitude = 10 ** Math.floor(Math.log10(raw));
  const step = Math.max(1, [1, 2, 5, 10].map((m) => m * magnitude).find((s) => s >= raw) ?? 10 * magnitude);
  const top = Math.ceil(max / step) * step;
  const ticks: number[] = [];
  for (let tick = 0; tick <= top + step / 2; tick += step) ticks.push(tick);
  return ticks;
}

/** Indexes of the x-axis labels to show so that at most `maxLabels` appear, always including the last day. */
export function labelIndexes(count: number, maxLabels: number): number[] {
  if (count <= 0) return [];
  if (count <= maxLabels) return Array.from({ length: count }, (_, i) => i);
  const every = Math.ceil(count / maxLabels);
  const indexes: number[] = [];
  for (let i = count - 1; i >= 0; i -= every) indexes.unshift(i);
  return indexes;
}

function first(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}
