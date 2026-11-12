/**
 * Reports dashboard helpers: the filter in the URL, period arithmetic, metrics, insights, number
 * formatting, CSV export and chart geometry. Pure functions, so they run on the server, in the
 * browser and in tests alike. Response types mirror contracts/openapi/reporting-service.v1.yaml.
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

export const PRESETS = {
  "7d": "Last 7 days",
  "30d": "Last 30 days",
  "90d": "Last 90 days",
  mtd: "Month to date",
} as const;
export type PresetKey = keyof typeof PRESETS;
export type Preset = PresetKey | "custom";
const PRESET_DAYS: Record<Exclude<PresetKey, "mtd">, number> = { "7d": 7, "30d": 30, "90d": 90 };
const DEFAULT_PRESET: PresetKey = "30d";

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

/** Whether a custom range is acceptable to reporting-service. */
export function isValidRange(from: string | undefined, to: string | undefined): boolean {
  return isIsoDate(from) && isIsoDate(to) && from <= to && daysInclusive(from, to) <= MAX_DAYS;
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
    if (isValidRange(from, to)) return { filter: { from: from!, to: to!, preset: "custom", zone } };
    return {
      filter: presetFilter(DEFAULT_PRESET, today, zone),
      notice: `That date range is not valid (the start must be on or before the end, at most ${MAX_DAYS} days). Showing the last 30 days instead.`,
    };
  }

  const range = first(params.range);
  const preset: PresetKey = range && range in PRESETS ? (range as PresetKey) : DEFAULT_PRESET;
  return { filter: presetFilter(preset, today, zone) };
}

function presetFilter(preset: PresetKey, today: string, zone?: string): ReportFilter {
  const from = preset === "mtd" ? `${today.slice(0, 8)}01` : addDays(today, -(PRESET_DAYS[preset] - 1));
  return { from, to: today, preset, zone };
}

/** The period of the same length that ends the day before `filter` starts; the baseline for deltas. */
export function previousPeriod(filter: Pick<ReportFilter, "from" | "to">): { from: string; to: string } {
  const length = daysInclusive(filter.from, filter.to);
  return { from: addDays(filter.from, -length), to: addDays(filter.from, -1) };
}

/** Link to a preset or a custom range: a query-only URL, resolved against the current reports page. */
export function reportsHref(target: { preset: Preset; from?: string; to?: string; zone?: string }): string {
  const query = new URLSearchParams();
  if (target.preset === "custom") {
    if (target.from) query.set("from", target.from);
    if (target.to) query.set("to", target.to);
  } else {
    query.set("range", target.preset);
  }
  if (target.zone) query.set("zone", target.zone);
  return `?${query.toString()}`;
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
export function formatPercent(rate: number | null, digits = 1): string {
  return rate === null ? "—" : `${(rate * 100).toFixed(digits)}%`;
}

/** "5 Oct" for axes, "Mon 5 Oct 2026" for tooltips and tables. */
export function formatDay(date: string, style: "short" | "long" = "short"): string {
  const options: Intl.DateTimeFormatOptions =
    style === "short"
      ? { day: "numeric", month: "short", timeZone: "UTC" }
      : { weekday: "short", day: "numeric", month: "short", year: "numeric", timeZone: "UTC" };
  return new Intl.DateTimeFormat("en-GB", options).format(new Date(`${date}T00:00:00Z`)).replace(",", "");
}

/** "2 Oct - 31 Oct 2026", or with both years when the range crosses a year. */
export function formatRange(from: string, to: string): string {
  const sameYear = from.slice(0, 4) === to.slice(0, 4);
  return `${formatDay(from)}${sameYear ? "" : ` ${from.slice(0, 4)}`} – ${formatDay(to)} ${to.slice(0, 4)}`;
}

export function zoneLabel(code: string, names: Record<string, string> = {}): string {
  if (code === UNKNOWN_ZONE) return "Zone not known yet";
  return names[code] ? `${names[code]} (${code})` : code;
}

/** Two-letter badge for an id such as "demo-courier-3" -> "D3". */
export function initials(id: string): string {
  const parts = id.split(/[^A-Za-z0-9]+/).filter(Boolean);
  if (parts.length === 0) return "?";
  const head = parts[0][0];
  const tail = parts.length > 1 ? parts[parts.length - 1][0] : (parts[0][1] ?? "");
  return `${head}${tail}`.toUpperCase();
}

// ---- metrics ---------------------------------------------------------------------------------

export type Metric = "delivered" | "successRate" | "failed" | "created";

export const METRICS: Record<Metric, { label: string; noun: string; colour: string; rate: boolean; upIsGood: boolean }> = {
  delivered: { label: "Deliveries", noun: "delivered", colour: "var(--viz-delivered)", rate: false, upIsGood: true },
  successRate: { label: "Success rate", noun: "success rate", colour: "var(--viz-delivered)", rate: true, upIsGood: true },
  failed: { label: "Failed attempts", noun: "failed attempts", colour: "var(--viz-failed)", rate: false, upIsGood: false },
  created: { label: "New parcels", noun: "created", colour: "var(--viz-created)", rate: false, upIsGood: true },
};

/** Delivered out of all delivery attempts that day; null when there were none. */
export function dailyRate(day: Pick<Day, "delivered" | "failed">): number | null {
  const attempts = day.delivered + day.failed;
  return attempts === 0 ? null : day.delivered / attempts;
}

export function metricValue(day: Day, metric: Metric): number | null {
  return metric === "successRate" ? dailyRate(day) : day[metric];
}

export function formatMetric(value: number | null, metric: Metric): string {
  if (METRICS[metric].rate) return formatPercent(value);
  return value === null ? "—" : formatCount(value);
}

/** The reference line: the period's overall rate, or the average per day for counts. */
export function referenceValue(totals: Totals, days: number, metric: Metric): number | null {
  if (metric === "successRate") return totals.successRate;
  return days > 0 ? totals[metric] / days : null;
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

/** Delta between two values of a metric, in the metric's own terms. */
export function metricDelta(current: number | null, previous: number | null, metric: Metric): Delta | null {
  if (METRICS[metric].rate) return rateDelta(current, previous);
  if (current === null || previous === null) return null;
  return countDelta(current, previous, METRICS[metric].upIsGood);
}

function toDelta(up: boolean, upIsGood: boolean, text: string): Delta {
  return { direction: up ? "up" : "down", tone: up === upIsGood ? "good" : "bad", text };
}

// ---- insights --------------------------------------------------------------------------------

export interface Insight {
  id: string;
  kind: "peak" | "risk" | "best" | "courier" | "info";
  title: string;
  detail: string;
  /** A zone the reader can drill into. */
  zone?: string;
}

/** A zone or courier needs this many attempts before its rate is called out, so one bad day is not news. */
export const MIN_ATTEMPTS = 10;

/** A few plain-language findings for the period, most useful first. Empty when there is no activity. */
export function buildInsights(
  daily: DailyReport,
  zones: ZoneReport[] | null,
  couriers: CourierReport[] | null,
  names: Record<string, string> = {},
  /** The zone the page is focused on; zone findings then compare it with all zones. */
  focusZone?: string,
): Insight[] {
  const insights: Insight[] = [];
  const { totals, days } = daily;
  if (totals.created + totals.delivered + totals.failed + totals.cancelled === 0) return insights;

  const busiest = days.reduce((best, day) => (day.delivered > best.delivered ? day : best), days[0]);
  if (busiest && busiest.delivered > 0) {
    const average = totals.delivered / days.length;
    const above = average > 0 ? Math.round(((busiest.delivered - average) / average) * 100) : 0;
    insights.push({
      id: "busiest-day",
      kind: "peak",
      title: `Busiest day: ${formatDay(busiest.date, "long")}`,
      detail: `${formatCount(busiest.delivered)} parcels delivered${above > 0 ? `, ${above}% above the daily average` : ""}.`,
    });
  }

  if (focusZone && zones) {
    const all = zones.reduce(
      (sum, zone) => ({ delivered: sum.delivered + zone.delivered, failed: sum.failed + zone.failed }),
      { delivered: 0, failed: 0 },
    );
    const overall = all.delivered + all.failed === 0 ? null : all.delivered / (all.delivered + all.failed);
    if (totals.successRate !== null && overall !== null) {
      const better = totals.successRate >= overall;
      insights.push({
        id: "zone-vs-all",
        kind: better ? "best" : "risk",
        title: `${zoneLabel(focusZone, names)} vs all zones`,
        detail: `${formatPercent(totals.successRate)} success here against ${formatPercent(overall)} across all zones.`,
      });
    }
  }

  const rated = (focusZone ? [] : (zones ?? [])).filter(
    (zone) => zone.zoneCode !== UNKNOWN_ZONE && zone.successRate !== null && zone.delivered + zone.failed >= MIN_ATTEMPTS,
  );
  if (rated.length >= 2) {
    const sorted = [...rated].sort((a, b) => a.successRate! - b.successRate!);
    const worst = sorted[0];
    const best = sorted[sorted.length - 1];
    insights.push({
      id: "lowest-zone",
      kind: "risk",
      title: `Lowest success rate: ${zoneLabel(worst.zoneCode, names)}`,
      detail: `${formatPercent(worst.successRate)} of ${formatCount(worst.delivered + worst.failed)} attempts delivered; ${formatCount(worst.failed)} failed.`,
      zone: worst.zoneCode,
    });
    if (best.successRate! > worst.successRate!) {
      insights.push({
        id: "best-zone",
        kind: "best",
        title: `Most reliable zone: ${zoneLabel(best.zoneCode, names)}`,
        detail: `${formatPercent(best.successRate)} of ${formatCount(best.delivered + best.failed)} attempts delivered.`,
        zone: best.zoneCode,
      });
    }
  }

  const top = (couriers ?? [])[0];
  if (top && top.delivered > 0) {
    insights.push({
      id: "top-courier",
      kind: "courier",
      title: `Top courier: ${top.courierId}`,
      detail: `${formatCount(top.delivered)} deliveries at ${formatPercent(top.successRate)} success${focusZone ? ", across all zones" : ""}.`,
    });
  }

  const unknown = focusZone ? undefined : (zones ?? []).find((zone) => zone.zoneCode === UNKNOWN_ZONE);
  if (unknown) {
    insights.push({
      id: "unknown-zone",
      kind: "info",
      title: "Some activity has no zone yet",
      detail: "Its parcel-created event has not arrived; it moves to the right zone when it does.",
    });
  }
  return insights;
}

// ---- CSV export ------------------------------------------------------------------------------

/**
 * RFC 4180 CSV. Text that a spreadsheet would run as a formula (= + - @ at the start) is prefixed
 * with a quote, since zone and courier ids are data from other systems.
 */
export function toCsv(header: string[], rows: (string | number | null)[][]): string {
  const cell = (value: string | number | null): string => {
    if (value === null) return "";
    if (typeof value === "number") return String(value);
    const safe = /^[=+\-@\t\r]/.test(value) ? `'${value}` : value;
    return /[",\r\n]/.test(safe) ? `"${safe.replaceAll('"', '""')}"` : safe;
  };
  return [header, ...rows].map((row) => row.map(cell).join(",")).join("\r\n");
}

// ---- chart geometry --------------------------------------------------------------------------

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

/**
 * Ticks for a rate axis. Rates cluster near the top, so the axis starts at a round value below the
 * lowest rate (never below 0) instead of 0, and the labels make that start explicit.
 */
export function rateTicks(values: (number | null)[]): number[] {
  const present = values.filter((v): v is number => v !== null);
  // The small epsilon keeps floating-point error (0.82 - 0.02 = 0.7999...) from dropping a whole step.
  const low = present.length === 0 ? 0 : Math.max(0, Math.floor((Math.min(...present) - 0.02) * 10 + 1e-9) / 10);
  const step = 1 - low > 0.4 ? 0.2 : 0.1;
  const ticks: number[] = [];
  for (let tick = low; tick <= 1.0001; tick += step) ticks.push(Math.round(tick * 100) / 100);
  if (ticks[ticks.length - 1] < 1) ticks.push(1);
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

export type Point = readonly [x: number, y: number];

/**
 * SVG path through the points as a monotone cubic (Fritsch-Carlson, as d3's curveMonotoneX): smooth,
 * but never overshooting a value, so peaks and dips on the line are real. `null` points break the
 * line into separate runs, e.g. days with no delivery attempts on a rate chart.
 */
export function smoothPath(points: (Point | null)[]): string {
  return splitRuns(points).map(monotoneRun).join("");
}

/** The same curve closed down to `baseline`, for the soft area wash under a single series. */
export function areaPath(points: (Point | null)[], baseline: number): string {
  const f = (n: number) => Number(n.toFixed(2));
  return splitRuns(points)
    .filter((run) => run.length > 1)
    .map((run) => `${monotoneRun(run)}L${f(run[run.length - 1][0])},${f(baseline)}L${f(run[0][0])},${f(baseline)}Z`)
    .join("");
}

function splitRuns(points: (Point | null)[]): Point[][] {
  const runs: Point[][] = [];
  let run: Point[] = [];
  for (const point of points) {
    if (point) run.push(point);
    else if (run.length) {
      runs.push(run);
      run = [];
    }
  }
  if (run.length) runs.push(run);
  return runs;
}

function monotoneRun(points: Point[]): string {
  const f = (n: number) => Number(n.toFixed(2));
  if (points.length === 1) return `M${f(points[0][0])},${f(points[0][1])}h0`;
  if (points.length === 2) return `M${f(points[0][0])},${f(points[0][1])}L${f(points[1][0])},${f(points[1][1])}`;

  const n = points.length;
  const tangents = new Array<number>(n);
  for (let i = 1; i < n - 1; i++) tangents[i] = slope3(points[i - 1], points[i], points[i + 1]);
  tangents[0] = slope2(points[0], points[1], tangents[1]);
  tangents[n - 1] = slope2(points[n - 1], points[n - 2], tangents[n - 2]);

  let d = `M${f(points[0][0])},${f(points[0][1])}`;
  for (let i = 0; i < n - 1; i++) {
    const [x0, y0] = points[i];
    const [x1, y1] = points[i + 1];
    const h = (x1 - x0) / 3;
    d += `C${f(x0 + h)},${f(y0 + h * tangents[i])},${f(x1 - h)},${f(y1 - h * tangents[i + 1])},${f(x1)},${f(y1)}`;
  }
  return d;
}

function slope3([x0, y0]: Point, [x1, y1]: Point, [x2, y2]: Point): number {
  const h0 = x1 - x0;
  const h1 = x2 - x1;
  const s0 = (y1 - y0) / h0;
  const s1 = (y2 - y1) / h1;
  const p = (s0 * h1 + s1 * h0) / (h0 + h1);
  return (Math.sign(s0) + Math.sign(s1)) * Math.min(Math.abs(s0), Math.abs(s1), 0.5 * Math.abs(p)) || 0;
}

function slope2([x0, y0]: Point, [x1, y1]: Point, tangent: number): number {
  const h = x1 - x0;
  return h ? ((3 * (y1 - y0)) / h - tangent) / 2 : tangent;
}

function first(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}
