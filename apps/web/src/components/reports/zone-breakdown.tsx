"use client";

import { useMemo, useState } from "react";
import { type ReportFilter, UNKNOWN_ZONE, type ZoneReport, formatCount, formatPercent, reportsHref, zoneLabel } from "@/lib/reports";
import { useReportsNavigation } from "./reports-navigation";

const OUTCOMES = [
  { key: "delivered", label: "Delivered", colour: "var(--viz-delivered)" },
  { key: "failed", label: "Failed", colour: "var(--viz-failed)" },
  { key: "cancelled", label: "Cancelled", colour: "var(--viz-cancelled)" },
] as const;

type Sort = "volume" | "success";

/**
 * Outcomes per dropoff zone. Bars are scaled to the busiest zone; selecting a zone focuses the
 * whole page on it (and selecting it again clears the focus).
 */
export function ZoneBreakdown({ zones, zoneNames, filter }: { zones: ZoneReport[]; zoneNames: Record<string, string>; filter: ReportFilter }) {
  const { navigate } = useReportsNavigation();
  const [sort, setSort] = useState<Sort>("volume");
  const [hovered, setHovered] = useState<string | null>(null);

  const rows = useMemo(() => {
    const active = zones.filter((z) => z.delivered + z.failed + z.cancelled > 0);
    const volume = (z: ZoneReport) => z.delivered + z.failed + z.cancelled;
    return [...active].sort((a, b) => {
      if (a.zoneCode === UNKNOWN_ZONE) return 1;
      if (b.zoneCode === UNKNOWN_ZONE) return -1;
      return sort === "volume" ? volume(b) - volume(a) : (b.successRate ?? -1) - (a.successRate ?? -1);
    });
  }, [zones, sort]);
  const max = Math.max(1, ...rows.map((z) => z.delivered + z.failed + z.cancelled));

  return (
    <section className="viz-root min-w-0 rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200 sm:p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="font-semibold text-slate-900">Zones</h2>
          <p className="text-sm text-slate-500">
            Outcomes by dropoff zone{filter.zone ? " · all zones, for comparison" : ""}
          </p>
        </div>
        <div role="group" aria-label="Sort zones" className="flex rounded-lg bg-slate-100 p-0.5 text-xs font-medium">
          {(["volume", "success"] as const).map((key) => (
            <button
              key={key}
              type="button"
              aria-pressed={sort === key}
              onClick={() => setSort(key)}
              className={`rounded-md px-2.5 py-1 transition ${sort === key ? "bg-white text-slate-900 shadow-sm" : "text-slate-500 hover:text-slate-800"}`}
            >
              {key === "volume" ? "Volume" : "Success rate"}
            </button>
          ))}
        </div>
      </div>

      <ul className="mt-3 flex flex-wrap gap-4 text-xs text-slate-600" aria-label="Legend">
        {OUTCOMES.map((o) => (
          <li key={o.key} className="flex items-center gap-1.5">
            <span aria-hidden="true" className="h-2.5 w-2.5 rounded-sm" style={{ background: o.colour }} />
            {o.label}
          </li>
        ))}
      </ul>

      <ul className="mt-3 space-y-1">
        {rows.map((zone) => {
          const total = zone.delivered + zone.failed + zone.cancelled;
          const selected = zone.zoneCode === filter.zone;
          const label = zoneLabel(zone.zoneCode, zoneNames);
          const drillable = zone.zoneCode !== UNKNOWN_ZONE;
          return (
            <li key={zone.zoneCode} className="relative">
              <button
                type="button"
                disabled={!drillable}
                onClick={() => navigate(reportsHref({ ...filter, zone: selected ? undefined : zone.zoneCode }))}
                onPointerEnter={() => setHovered(zone.zoneCode)}
                onPointerLeave={() => setHovered(null)}
                onFocus={() => setHovered(zone.zoneCode)}
                onBlur={() => setHovered(null)}
                aria-pressed={selected}
                aria-label={`${label}: ${formatCount(zone.delivered)} delivered, ${formatCount(zone.failed)} failed, ${formatCount(zone.cancelled)} cancelled, ${formatPercent(zone.successRate)} success.${drillable ? (selected ? " Select to show all zones." : " Select to focus on this zone.") : ""}`}
                className={`grid w-full grid-cols-[minmax(0,7rem)_1fr_auto] sm:grid-cols-[minmax(0,9rem)_1fr_auto] items-center gap-3 rounded-xl px-2 py-1.5 text-left transition ${
                  selected ? "bg-brand-50 ring-1 ring-brand-500" : drillable ? "hover:bg-slate-50" : ""
                }`}
              >
                <span className="flex min-w-0 items-baseline gap-1.5">
                  <span className={`truncate text-sm ${selected ? "font-semibold text-brand-700" : "text-slate-800"}`}>
                    {zoneNames[zone.zoneCode] ?? (zone.zoneCode === UNKNOWN_ZONE ? "Not known yet" : zone.zoneCode)}
                  </span>
                  {zoneNames[zone.zoneCode] && <span className="shrink-0 text-[11px] text-slate-400">{zone.zoneCode}</span>}
                </span>
                <span className="flex h-3 overflow-hidden rounded-r" style={{ width: `${(total / max) * 100}%`, minWidth: 6 }}>
                  <span className="viz-grow flex h-full w-full gap-[2px]">
                    {OUTCOMES.map((o) =>
                      zone[o.key] > 0 ? (
                        <span
                          key={o.key}
                          className="h-full min-w-[2px] transition-opacity"
                          style={{ flexGrow: zone[o.key], flexBasis: 0, background: o.colour, opacity: hovered && hovered !== zone.zoneCode ? 0.45 : 1 }}
                        />
                      ) : null,
                    )}
                  </span>
                </span>
                <span className="flex items-center justify-end gap-2 sm:w-28">
                  <span className="text-sm font-semibold tabular-nums text-slate-900">{formatCount(total)}</span>
                  <SuccessPill rate={zone.successRate} />
                </span>
              </button>

              {hovered === zone.zoneCode && (
                <div role="status" className="viz-fade pointer-events-none absolute -top-2 left-36 z-10 -translate-y-full rounded-xl bg-white px-3 py-2 text-xs shadow-lg ring-1 ring-slate-200">
                  <p className="font-semibold text-slate-900">{label}</p>
                  <dl className="mt-1 grid grid-cols-[auto_auto] gap-x-4 gap-y-0.5">
                    {OUTCOMES.map((o) => (
                      <div key={o.key} className="contents">
                        <dt className="flex items-center gap-1.5 text-slate-500">
                          <span aria-hidden="true" className="h-2 w-2 rounded-sm" style={{ background: o.colour }} />
                          {o.label}
                        </dt>
                        <dd className="text-right font-semibold tabular-nums text-slate-900">{formatCount(zone[o.key])}</dd>
                      </div>
                    ))}
                    <dt className="text-slate-500">New parcels</dt>
                    <dd className="text-right font-semibold tabular-nums text-slate-900">{formatCount(zone.created)}</dd>
                  </dl>
                </div>
              )}
            </li>
          );
        })}
      </ul>
      <p className="mt-3 text-xs text-slate-400">Select a zone to focus the whole page on it.</p>
    </section>
  );
}

/** Success rate as a tiny meter on a lighter step of the same blue, plus the number. */
export function SuccessPill({ rate }: { rate: number | null }) {
  return (
    <span className="flex items-center gap-1.5" title="Success rate">
      <span aria-hidden="true" className="hidden h-1.5 w-8 overflow-hidden rounded-full bg-[var(--viz-track)] sm:block">
        <span className="block h-full rounded-full bg-[var(--viz-delivered)]" style={{ width: `${(rate ?? 0) * 100}%` }} />
      </span>
      <span className="w-11 text-right text-xs tabular-nums text-slate-600">{formatPercent(rate, rate === 1 ? 0 : 1)}</span>
    </span>
  );
}
