import "server-only";
import { gatewayFetch } from "@/lib/gateway";
import {
  type CourierReport,
  type DailyReport,
  type ReportFilter,
  type ZoneReport,
  periodQuery,
  previousPeriod,
} from "@/lib/reports";

export type Section<T> = { ok: true; data: T } | { ok: false; message: string };

export interface ReportsData {
  daily: Section<DailyReport>;
  /** Same length, just before the selected period; only used for the deltas on the summary tiles. */
  previous: Section<DailyReport>;
  zones: Section<ZoneReport[]>;
  couriers: Section<CourierReport[]>;
  /** Zone code to display name from pricing-service; empty when it is unavailable. */
  zoneNames: Record<string, string>;
}

/** Loads every section in parallel. A failing section is reported in its own card; the rest still render. */
export async function loadReports(filter: ReportFilter): Promise<ReportsData> {
  const current = { from: filter.from, to: filter.to };
  const [daily, previous, zones, couriers, zoneNames] = await Promise.all([
    get<DailyReport>(`/api/v1/reports/daily?${periodQuery(current, filter.zone)}`),
    get<DailyReport>(`/api/v1/reports/daily?${periodQuery(previousPeriod(filter), filter.zone)}`),
    get<ZoneReport[]>(`/api/v1/reports/zones?${periodQuery(current)}`),
    get<CourierReport[]>(`/api/v1/reports/couriers?${periodQuery(current)}`),
    loadZoneNames(),
  ]);
  return { daily, previous, zones, couriers, zoneNames };
}

async function get<T>(path: string): Promise<Section<T>> {
  try {
    const response = await gatewayFetch(path);
    if (response.ok) return { ok: true, data: (await response.json()) as T };
    if (response.status === 401) return { ok: false, message: "Your session has expired. Sign in again to see the reports." };
    if (response.status === 403) return { ok: false, message: "Your account is not allowed to see the reports." };
    if (response.status >= 500) return { ok: false, message: "The reporting service is not reachable right now. Try again shortly." };
    return { ok: false, message: `The report could not be loaded (HTTP ${response.status}).` };
  } catch {
    return { ok: false, message: "The reporting service is not reachable right now. Try again shortly." };
  }
}

/** Best effort: zone names make the charts friendlier, but the codes are enough without them. */
async function loadZoneNames(): Promise<Record<string, string>> {
  const zones = await get<{ code?: unknown; name?: unknown }[]>("/api/v1/pricing/zones");
  if (!zones.ok || !Array.isArray(zones.data)) return {};
  const names: Record<string, string> = {};
  for (const zone of zones.data) {
    if (typeof zone.code === "string" && typeof zone.name === "string") names[zone.code] = zone.name;
  }
  return names;
}
