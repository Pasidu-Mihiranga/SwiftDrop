import Link from "next/link";
import { CourierLeaderboard } from "@/components/reports/courier-leaderboard";
import { FilterBar } from "@/components/reports/filter-bar";
import { Icon } from "@/components/reports/icons";
import { InsightsCard } from "@/components/reports/insights-card";
import { KpiCards } from "@/components/reports/kpi-cards";
import { PendingFrame, ReportsNavigation, RetryButton } from "@/components/reports/reports-navigation";
import { TrendPanel } from "@/components/reports/trend-panel";
import { ZoneBreakdown } from "@/components/reports/zone-breakdown";
import {
  REPORT_TIME_ZONE,
  type ReportFilter,
  buildInsights,
  daysInclusive,
  formatRange,
  reportsHref,
  zoneLabel,
} from "@/lib/reports";
import type { ReportsData, Section } from "./data";

interface Props {
  filter: ReportFilter;
  notice?: string;
  data: ReportsData;
}

/** The dashboard for one filter; data loading lives in the page so this stays presentational. */
export function ReportsView({ filter, notice, data }: Props) {
  const length = daysInclusive(filter.from, filter.to);
  const zoneName = filter.zone ? zoneLabel(filter.zone, data.zoneNames) : "All zones";
  const loadedAt = new Intl.DateTimeFormat("en-GB", { timeZone: REPORT_TIME_ZONE, hour: "2-digit", minute: "2-digit" }).format(new Date());

  const activity = new Map<string, number | null>();
  for (const code of Object.keys(data.zoneNames)) activity.set(code, data.zones.ok ? 0 : null);
  if (data.zones.ok) {
    for (const zone of data.zones.data) activity.set(zone.zoneCode, zone.created + zone.delivered + zone.failed + zone.cancelled);
  }
  if (filter.zone && !activity.has(filter.zone)) activity.set(filter.zone, null);
  const zoneOptions = [...activity.entries()]
    .filter(([code]) => code !== "UNKNOWN")
    .map(([code, count]) => ({ code, activity: count }))
    .sort((a, b) => (b.activity ?? 0) - (a.activity ?? 0) || a.code.localeCompare(b.code));

  return (
    <ReportsNavigation>
      <div className="space-y-5">
        <header className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <nav aria-label="Breadcrumb" className="text-sm text-slate-500">
              <Link href="/admin" className="hover:text-slate-800 hover:underline">Operations</Link>
              <span className="mx-1.5 text-slate-300">/</span>
              <span className="text-slate-700">Reports</span>
            </nav>
            <h1 className="mt-1 text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">Delivery reports</h1>
            <p className="mt-1 text-sm text-slate-600">
              {formatRange(filter.from, filter.to)} &middot; {zoneName} &middot; Sri Lanka time
            </p>
          </div>
        </header>

        <FilterBar filter={filter} zones={zoneOptions} zoneNames={data.zoneNames} loadedAt={loadedAt} />

        {notice && (
          <p role="status" className="flex items-center gap-2 rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-800 ring-1 ring-amber-200">
            <Icon name="alert" />
            {notice}
          </p>
        )}

        <PendingFrame>
          {!data.daily.ok ? (
            <Problem section={data.daily} />
          ) : isEmpty(data.daily.data.totals) ? (
            <Empty filter={filter} />
          ) : (
            <div className="space-y-5">
              <KpiCards
                totals={data.daily.data.totals}
                previous={data.previous.ok ? data.previous.data.totals : null}
                days={data.daily.data.days}
                comparisonLabel={`vs previous ${length} ${length === 1 ? "day" : "days"}`}
              />

              <div className="grid gap-5 lg:grid-cols-3">
                <div className="min-w-0 lg:col-span-2">
                  <TrendPanel
                    days={data.daily.data.days}
                    previousDays={data.previous.ok ? data.previous.data.days : null}
                    totals={data.daily.data.totals}
                    previousTotals={data.previous.ok ? data.previous.data.totals : null}
                    fileStem={`${filter.from}_${filter.to}${filter.zone ? `_${filter.zone}` : ""}`}
                  />
                </div>
                <InsightsCard
                  filter={filter}
                  insights={buildInsights(
                    data.daily.data,
                    data.zones.ok ? data.zones.data : null,
                    data.couriers.ok ? data.couriers.data : null,
                    data.zoneNames,
                    filter.zone,
                  )}
                />
              </div>

              <div className="grid gap-5 lg:grid-cols-2">
                {data.zones.ok ? (
                  <ZoneBreakdown zones={data.zones.data} zoneNames={data.zoneNames} filter={filter} />
                ) : (
                  <Problem section={data.zones} title="Zones" />
                )}
                {data.couriers.ok ? (
                  <CourierLeaderboard couriers={data.couriers.data} />
                ) : (
                  <Problem section={data.couriers} title="Couriers" />
                )}
              </div>
            </div>
          )}
        </PendingFrame>
      </div>
    </ReportsNavigation>
  );
}

function isEmpty(totals: { created: number; delivered: number; failed: number; cancelled: number }): boolean {
  return totals.created + totals.delivered + totals.failed + totals.cancelled === 0;
}

function Problem({ section, title }: { section: Section<unknown>; title?: string }) {
  return (
    <section className="flex flex-col items-center justify-center gap-3 rounded-2xl bg-white px-6 py-12 text-center shadow-sm ring-1 ring-slate-200">
      <span className="flex h-11 w-11 items-center justify-center rounded-full bg-amber-50 text-amber-600">
        <Icon name="alert" className="h-5 w-5" />
      </span>
      <div>
        <p className="font-semibold text-slate-900">{title ? `${title} could not be loaded` : "Reports could not be loaded"}</p>
        <p className="mt-1 text-sm text-slate-500">{section.ok ? "" : section.message}</p>
      </div>
      <RetryButton />
    </section>
  );
}

function Empty({ filter }: { filter: ReportFilter }) {
  return (
    <section className="flex flex-col items-center justify-center gap-3 rounded-2xl bg-white px-6 py-16 text-center shadow-sm ring-1 ring-slate-200">
      <span className="flex h-12 w-12 items-center justify-center rounded-full bg-slate-100 text-slate-500">
        <Icon name="empty" className="h-6 w-6" />
      </span>
      <div>
        <p className="font-semibold text-slate-900">No parcel activity in this period</p>
        <p className="mt-1 text-sm text-slate-500">
          Nothing was created, delivered, failed or cancelled{filter.zone ? " in this zone" : ""} between these dates.
        </p>
      </div>
      <div className="flex gap-2">
        {filter.preset !== "90d" && (
          <Link href={reportsHref({ preset: "90d", zone: filter.zone })} className="btn-secondary py-1.5 text-sm">
            Try the last 90 days
          </Link>
        )}
        {filter.zone && (
          <Link href={reportsHref({ ...filter, zone: undefined })} className="btn-secondary py-1.5 text-sm">
            Show all zones
          </Link>
        )}
      </div>
    </section>
  );
}
