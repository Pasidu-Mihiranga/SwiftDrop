import Link from "next/link";
import type { ReactNode } from "react";
import { DailyTrendChart } from "@/components/reports/daily-trend-chart";
import { type OutcomeRow, OutcomeBars } from "@/components/reports/outcome-bars";
import { ReportFilters } from "@/components/reports/report-filters";
import { StatTiles } from "@/components/reports/stat-tiles";
import { TableView } from "@/components/reports/table-view";
import {
  type ReportFilter,
  UNKNOWN_ZONE,
  daysInclusive,
  formatCount,
  formatDay,
  formatPercent,
  zoneLabel,
} from "@/lib/reports";
import type { ReportsData, Section } from "./data";

/** Couriers beyond this many are left to the table, so the chart stays readable. */
const COURIER_ROWS = 8;

interface Props {
  filter: ReportFilter;
  notice?: string;
  data: ReportsData;
}

/** The dashboard for one filter; data loading lives in the page so this stays presentational. */
export function ReportsView({ filter, notice, data }: Props) {
  const length = daysInclusive(filter.from, filter.to);
  const zoneName = filter.zone ? zoneLabel(filter.zone, data.zoneNames) : "All zones";

  const zoneCodes = new Set<string>(Object.keys(data.zoneNames));
  if (data.zones.ok) data.zones.data.forEach((zone) => zoneCodes.add(zone.zoneCode));
  if (filter.zone) zoneCodes.add(filter.zone);

  return (
    <div className="space-y-6">
      <div>
        <p className="text-sm text-slate-500">
          <Link href="/admin" className="hover:underline">Operations</Link> / Reports
        </p>
        <h1 className="mt-1 text-2xl font-bold">Delivery reports</h1>
        <p className="text-slate-600">
          {formatDay(filter.from, "long")} to {formatDay(filter.to, "long")} &middot; {zoneName}. Days follow Sri Lanka time.
        </p>
      </div>

      <ReportFilters filter={filter} zones={[...zoneCodes].sort()} zoneNames={data.zoneNames} />

      {notice && <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-800">{notice}</p>}

      {data.daily.ok ? (
        <StatTiles
          totals={data.daily.data.totals}
          previous={data.previous.ok ? data.previous.data.totals : null}
          comparisonLabel={`previous ${length} ${length === 1 ? "day" : "days"}`}
        />
      ) : (
        <Unavailable section={data.daily} />
      )}

      <Card title="Deliveries per day" subtitle={`Parcels delivered against parcels created · ${zoneName}`}>
        {withData(data.daily, (daily) =>
          daily.totals.created + daily.totals.delivered + daily.totals.failed + daily.totals.cancelled === 0 ? (
            <Empty />
          ) : (
            <>
              <DailyTrendChart days={daily.days} />
              <TableView
                caption="Parcels per day"
                columns={[
                  { label: "Day" },
                  { label: "Created", numeric: true },
                  { label: "Delivered", numeric: true },
                  { label: "Failed attempts", numeric: true },
                  { label: "Cancelled", numeric: true },
                ]}
                rows={daily.days.map((d) => [formatDay(d.date, "long"), d.created, d.delivered, d.failed, d.cancelled])}
              />
            </>
          ),
        )}
      </Card>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card
          title="Outcomes by zone"
          subtitle={`Delivered, failed attempts and cancellations per dropoff zone${filter.zone ? " · all zones (not filtered by zone)" : ""}`}
        >
          {withData(data.zones, (zones) => {
            const rows: OutcomeRow[] = zones
              .map((zone) => ({
                key: zone.zoneCode,
                label: zoneLabel(zone.zoneCode, data.zoneNames),
                counts: { delivered: zone.delivered, failed: zone.failed, cancelled: zone.cancelled },
                successRate: zone.successRate,
              }))
              .filter((row) => row.counts.delivered + row.counts.failed + row.counts.cancelled > 0)
              .sort((a, b) => b.counts.delivered + b.counts.failed - (a.counts.delivered + a.counts.failed));
            if (rows.length === 0) return <Empty />;
            return (
              <>
                <OutcomeBars rows={rows} outcomes={["delivered", "failed", "cancelled"]} noun="zone" />
                {zones.some((zone) => zone.zoneCode === UNKNOWN_ZONE) && (
                  <p className="mt-3 text-xs text-slate-500">
                    &quot;Zone not known yet&quot; holds activity whose parcel-created event has not arrived; it moves to the right zone when it does.
                  </p>
                )}
                <TableView
                  caption="Outcomes by zone"
                  columns={[
                    { label: "Zone" },
                    { label: "Created", numeric: true },
                    { label: "Delivered", numeric: true },
                    { label: "Failed attempts", numeric: true },
                    { label: "Cancelled", numeric: true },
                    { label: "Success rate", numeric: true },
                  ]}
                  rows={zones.map((zone) => [
                    zoneLabel(zone.zoneCode, data.zoneNames),
                    zone.created,
                    zone.delivered,
                    zone.failed,
                    zone.cancelled,
                    formatPercent(zone.successRate),
                  ])}
                />
              </>
            );
          })}
        </Card>

        <Card
          title="Outcomes by courier"
          subtitle={`Delivered and failed attempts per courier, most deliveries first${filter.zone ? " · all zones (not filtered by zone)" : ""}`}
        >
          {withData(data.couriers, (couriers) => {
            if (couriers.length === 0) return <Empty />;
            const rows: OutcomeRow[] = couriers.slice(0, COURIER_ROWS).map((courier) => ({
              key: courier.courierId,
              label: courier.courierId,
              counts: { delivered: courier.delivered, failed: courier.failed },
              successRate: courier.successRate,
            }));
            return (
              <>
                <OutcomeBars rows={rows} outcomes={["delivered", "failed"]} noun="courier" />
                {couriers.length > COURIER_ROWS && (
                  <p className="mt-3 text-xs text-slate-500">
                    Showing the top {COURIER_ROWS} of {formatCount(couriers.length)} couriers; all of them are in the table.
                  </p>
                )}
                <TableView
                  caption="Outcomes by courier"
                  columns={[
                    { label: "Courier" },
                    { label: "Delivered", numeric: true },
                    { label: "Failed attempts", numeric: true },
                    { label: "Success rate", numeric: true },
                  ]}
                  rows={couriers.map((c) => [c.courierId, c.delivered, c.failed, formatPercent(c.successRate)])}
                />
              </>
            );
          })}
        </Card>
      </div>
    </div>
  );
}

function Card({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <section className="card">
      <h2 className="font-semibold">{title}</h2>
      <p className="mb-4 text-sm text-slate-600">{subtitle}</p>
      {children}
    </section>
  );
}

function withData<T>(section: Section<T>, render: (data: T) => ReactNode): ReactNode {
  return section.ok ? render(section.data) : <Unavailable section={section} />;
}

function Unavailable({ section }: { section: Section<unknown> }) {
  return <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-800">{section.ok ? "" : section.message}</p>;
}

function Empty() {
  return <p className="py-8 text-center text-sm text-slate-500">No parcel activity in this period.</p>;
}
