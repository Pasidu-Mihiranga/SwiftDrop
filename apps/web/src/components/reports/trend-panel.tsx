"use client";

import { type KeyboardEvent, type PointerEvent, useEffect, useId, useRef, useState } from "react";
import {
  type Day,
  METRICS,
  type Metric,
  type Totals,
  formatCount,
  formatDay,
  formatMetric,
  formatPercent,
  labelIndexes,
  metricDelta,
  metricValue,
  niceTicks,
  rateTicks,
  referenceValue,
  smoothPath,
  toCsv,
  areaPath,
} from "@/lib/reports";
import { DeltaBadge } from "./delta-badge";
import { Icon } from "./icons";

const HEIGHT = 280;
/** The right gutter holds the reference label, so it never sits on the line. */
const MARGIN = { top: 20, right: 64, bottom: 32, left: 48 };
const ORDER: Metric[] = ["delivered", "successRate", "failed", "created"];

interface Props {
  days: Day[];
  /** The previous period of the same length, day by day, or null when it could not be loaded. */
  previousDays: Day[] | null;
  totals: Totals;
  previousTotals: Totals | null;
  /** Used in the CSV file name, e.g. "2026-10-02_2026-10-31". */
  fileStem: string;
}

/**
 * The main trend: one metric at a time (tabs), optionally against the previous period in grey.
 * Crosshair and keyboard readout, chart/table switch, and CSV export of what is on screen.
 */
export function TrendPanel({ days, previousDays, totals, previousTotals, fileStem }: Props) {
  const [metric, setMetric] = useState<Metric>("delivered");
  const [compare, setCompare] = useState(previousDays !== null);
  const [view, setView] = useState<"chart" | "table">("chart");
  const canCompare = previousDays !== null && previousDays.length === days.length;
  const showPrevious = compare && canCompare;

  const summary = METRICS[metric].rate ? totals.successRate : totals[metric as Exclude<Metric, "successRate">];
  const previousSummary = previousTotals
    ? METRICS[metric].rate
      ? previousTotals.successRate
      : previousTotals[metric as Exclude<Metric, "successRate">]
    : null;

  function exportCsv() {
    const csv = toCsv(
      ["Day", "Created", "Delivered", "Failed attempts", "Cancelled", "Success rate"],
      days.map((d) => [d.date, d.created, d.delivered, d.failed, d.cancelled, metricValue(d, "successRate")]),
    );
    const url = URL.createObjectURL(new Blob([csv], { type: "text/csv;charset=utf-8" }));
    const link = Object.assign(document.createElement("a"), { href: url, download: `swiftdrop-deliveries_${fileStem}.csv` });
    link.click();
    URL.revokeObjectURL(url);
  }

  return (
    <section className="viz-root rounded-2xl bg-white shadow-sm ring-1 ring-slate-200">
      <div className="flex flex-wrap items-center justify-between gap-x-3 border-b border-slate-100 px-4 pt-4 sm:px-5">
        <div role="tablist" aria-label="Metric" className="-mb-px flex gap-1 overflow-x-auto">
          {ORDER.map((key) => {
            const selected = key === metric;
            return (
              <button
                key={key}
                type="button"
                role="tab"
                aria-selected={selected}
                onClick={() => setMetric(key)}
                className={`whitespace-nowrap border-b-2 px-3 pb-3 text-sm font-medium transition ${
                  selected ? "border-brand-600 text-slate-900" : "border-transparent text-slate-500 hover:text-slate-800"
                }`}
              >
                {METRICS[key].label}
              </button>
            );
          })}
        </div>
        <div className="flex items-center gap-2 pb-3">
          <label className={`flex items-center gap-2 text-xs font-medium ${canCompare ? "text-slate-600" : "text-slate-400"}`}>
            <button
              type="button"
              role="switch"
              aria-checked={showPrevious}
              disabled={!canCompare}
              onClick={() => setCompare((c) => !c)}
              className={`relative h-5 w-9 rounded-full transition ${showPrevious ? "bg-brand-600" : "bg-slate-200"} disabled:opacity-50`}
            >
              <span
                className={`absolute top-0.5 h-4 w-4 rounded-full bg-white shadow transition-all ${showPrevious ? "left-[18px]" : "left-0.5"}`}
              />
            </button>
            Compare
          </label>
          <div className="flex rounded-lg p-0.5 ring-1 ring-slate-200" role="group" aria-label="View">
            {(["chart", "table"] as const).map((v) => (
              <button
                key={v}
                type="button"
                aria-pressed={view === v}
                aria-label={v === "chart" ? "Show chart" : "Show table"}
                onClick={() => setView(v)}
                className={`rounded-md p-1.5 transition ${view === v ? "bg-slate-100 text-slate-900" : "text-slate-400 hover:text-slate-700"}`}
              >
                <Icon name={v} />
              </button>
            ))}
          </div>
          <button type="button" onClick={exportCsv} className="btn-secondary gap-1.5 px-2.5 py-1.5 text-xs">
            <Icon name="download" className="h-3.5 w-3.5" />
            CSV
          </button>
        </div>
      </div>

      <div className="px-4 pb-5 pt-4 sm:px-5">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <p className="text-sm text-slate-500">
              {METRICS[metric].rate ? "Success rate for the period" : `${METRICS[metric].label} in the period`}
            </p>
            <p className="mt-0.5 flex items-center gap-3">
              <span className="text-3xl font-semibold tracking-tight text-slate-900">{formatMetric(summary, metric)}</span>
              <DeltaBadge delta={previousTotals ? metricDelta(summary, previousSummary, metric) : null} label="vs previous period" />
            </p>
          </div>
          <ul className="flex gap-4 text-xs text-slate-600" aria-label="Legend">
            <li className="flex items-center gap-1.5">
              <span aria-hidden="true" className="h-0.5 w-4 rounded" style={{ background: METRICS[metric].colour }} />
              This period
            </li>
            {showPrevious && (
              <li className="flex items-center gap-1.5">
                <span aria-hidden="true" className="h-0.5 w-4 rounded bg-[var(--viz-context)]" />
                Previous period
              </li>
            )}
          </ul>
        </div>

        {view === "chart" ? (
          <TrendChart
            key={`${metric}-${days[0]?.date}`}
            days={days}
            previousDays={showPrevious ? previousDays : null}
            metric={metric}
            reference={referenceValue(totals, days.length, metric)}
          />
        ) : (
          <TrendTable days={days} />
        )}
      </div>
    </section>
  );
}

function TrendChart({
  days,
  previousDays,
  metric,
  reference,
}: {
  days: Day[];
  previousDays: Day[] | null;
  metric: Metric;
  reference: number | null;
}) {
  const wrapper = useRef<HTMLDivElement>(null);
  const gradient = useId();
  const [width, setWidth] = useState(760);
  const [active, setActive] = useState<number | null>(null);

  useEffect(() => {
    const element = wrapper.current;
    if (!element) return;
    const observer = new ResizeObserver(([entry]) => setWidth(Math.max(280, Math.floor(entry.contentRect.width))));
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  if (days.length === 0) return null;
  const { colour, rate } = METRICS[metric];
  const current = days.map((d) => metricValue(d, metric));
  const previous = previousDays?.map((d) => metricValue(d, metric)) ?? [];
  const all = [...current, ...previous];

  const ticks = rate ? rateTicks(all) : niceTicks(Math.max(0, ...all.map((v) => v ?? 0)));
  const low = ticks[0];
  const high = ticks[ticks.length - 1];
  const plotWidth = width - MARGIN.left - MARGIN.right;
  const plotHeight = HEIGHT - MARGIN.top - MARGIN.bottom;
  const last = days.length - 1;
  const x = (i: number) => MARGIN.left + (last === 0 ? plotWidth / 2 : (i * plotWidth) / last);
  const y = (v: number) => MARGIN.top + plotHeight - ((v - low) / (high - low)) * plotHeight;
  const points = (values: (number | null)[]) => values.map((v, i) => (v === null ? null : ([x(i), y(v)] as const)));

  const line = smoothPath(points(current));
  const area = areaPath(points(current), y(low));

  const peak = current.reduce<number | null>((best, v, i) => (v !== null && (best === null || v > current[best]!) ? i : best), null);
  const format = (v: number) => (rate ? formatPercent(v, 0) : formatCount(v));

  function nearest(event: PointerEvent<SVGRectElement>) {
    const box = event.currentTarget.getBoundingClientRect();
    setActive(Math.min(last, Math.max(0, Math.round(((event.clientX - box.left) / box.width) * last))));
  }

  function onKey(event: KeyboardEvent<SVGSVGElement>) {
    const step = event.key === "ArrowLeft" ? -1 : event.key === "ArrowRight" ? 1 : 0;
    if (step) setActive((a) => Math.min(last, Math.max(0, (a ?? last) + step)));
    else if (event.key === "Home") setActive(0);
    else if (event.key === "End") setActive(last);
    else if (event.key === "Escape") setActive(null);
    else return;
    event.preventDefault();
  }

  const day = active === null ? null : days[active];
  const previousDay = active === null || !previousDays ? null : previousDays[active];
  const tooltipLeft = active === null ? 0 : x(active) + 236 <= width ? x(active) + 14 : Math.max(0, x(active) - 236);

  return (
    <div ref={wrapper} className="relative mt-4">
      <svg
        width={width}
        height={HEIGHT}
        role="img"
        tabIndex={0}
        aria-label={`${METRICS[metric].label} per day, ${formatDay(days[0].date, "long")} to ${formatDay(days[last].date, "long")}. Use the arrow keys to read each day.`}
        onKeyDown={onKey}
        onFocus={() => setActive((a) => a ?? last)}
        onBlur={() => setActive(null)}
        className="block max-w-full rounded-lg focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
      >
        <defs>
          <linearGradient id={gradient} x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stopColor={colour} stopOpacity={0.16} />
            <stop offset="100%" stopColor={colour} stopOpacity={0} />
          </linearGradient>
        </defs>

        {ticks.map((tick) => (
          <g key={tick}>
            <line
              x1={MARGIN.left}
              x2={MARGIN.left + plotWidth}
              y1={y(tick)}
              y2={y(tick)}
              stroke={tick === low ? "var(--viz-axis)" : "var(--viz-grid)"}
              shapeRendering="crispEdges"
            />
            <text x={MARGIN.left - 10} y={y(tick)} dy="0.32em" textAnchor="end" className="fill-slate-400 text-[11px] tabular-nums">
              {format(tick)}
            </text>
          </g>
        ))}

        {labelIndexes(days.length, Math.max(2, Math.floor(plotWidth / 76))).map((i) => (
          <text
            key={days[i].date}
            x={x(i)}
            y={HEIGHT - 10}
            textAnchor={i === last && last > 0 ? "end" : i === 0 && last > 0 ? "start" : "middle"}
            className="fill-slate-400 text-[11px]"
          >
            {formatDay(days[i].date)}
          </text>
        ))}

        {reference !== null && reference >= low && (
          <g className="viz-fade">
            <line
              x1={MARGIN.left}
              x2={MARGIN.left + plotWidth}
              y1={y(reference)}
              y2={y(reference)}
              stroke="var(--viz-muted)"
              strokeWidth={1}
              strokeOpacity={0.6}
              shapeRendering="crispEdges"
            />
            <text x={MARGIN.left + plotWidth + 8} y={y(reference)} className="fill-slate-500 text-[11px]">
              <tspan x={MARGIN.left + plotWidth + 8} dy="-0.2em">{rate ? "period" : "avg/day"}</tspan>
              <tspan x={MARGIN.left + plotWidth + 8} dy="1.15em" className="fill-slate-700 font-semibold">
                {rate ? formatPercent(reference) : reference.toFixed(1)}
              </tspan>
            </text>
          </g>
        )}

        <path d={area} fill={`url(#${gradient})`} className="viz-fade" />
        {previousDays && (
          <path d={smoothPath(points(previous))} fill="none" stroke="var(--viz-context)" strokeWidth={2} strokeLinecap="round" className="viz-fade" />
        )}
        <path d={line} fill="none" stroke={colour} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" pathLength={1} className="viz-draw" />

        {peak !== null && active === null && current[peak] !== null && (
          <g className="viz-fade">
            <circle cx={x(peak)} cy={y(current[peak]!)} r={4} fill={colour} stroke="var(--viz-surface)" strokeWidth={2} />
            <text
              x={x(peak)}
              y={y(current[peak]!) - 10}
              textAnchor={x(peak) > width - 60 ? "end" : x(peak) < MARGIN.left + 40 ? "start" : "middle"}
              className="fill-slate-700 text-[11px] font-semibold"
            >
              Peak {format(current[peak]!)}
            </text>
          </g>
        )}

        {active !== null && (
          <g pointerEvents="none">
            <line x1={x(active)} x2={x(active)} y1={MARGIN.top} y2={MARGIN.top + plotHeight} stroke="var(--viz-axis)" />
            {previousDays && previous[active] !== null && (
              <circle cx={x(active)} cy={y(previous[active]!)} r={4} fill="var(--viz-context)" stroke="var(--viz-surface)" strokeWidth={2} />
            )}
            {current[active] !== null && (
              <circle cx={x(active)} cy={y(current[active]!)} r={5} fill={colour} stroke="var(--viz-surface)" strokeWidth={2} />
            )}
          </g>
        )}

        <rect
          x={MARGIN.left}
          y={MARGIN.top}
          width={plotWidth}
          height={plotHeight}
          fill="transparent"
          onPointerMove={nearest}
          onPointerDown={nearest}
          onPointerLeave={() => setActive(null)}
        />
      </svg>

      {day && (
        <div
          role="status"
          className="pointer-events-none absolute top-0 z-10 w-56 rounded-xl bg-white/95 p-3 text-xs shadow-lg ring-1 ring-slate-200 backdrop-blur"
          style={{ left: tooltipLeft }}
        >
          <p className="font-semibold text-slate-900">{formatDay(day.date, "long")}</p>
          <div className="mt-2 flex items-center gap-2">
            <span aria-hidden="true" className="h-0.5 w-3 rounded" style={{ background: colour }} />
            <span className="text-base font-semibold tabular-nums text-slate-900">{formatMetric(current[active!], metric)}</span>
            <span className="text-slate-500">{METRICS[metric].noun}</span>
          </div>
          {previousDay && (
            <div className="mt-1 flex items-center gap-2">
              <span aria-hidden="true" className="h-0.5 w-3 rounded bg-[var(--viz-context)]" />
              <span className="font-semibold tabular-nums text-slate-700">{formatMetric(previous[active!], metric)}</span>
              <span className="text-slate-500">on {formatDay(previousDay.date)}</span>
              <span className="ml-auto">
                <DeltaBadge delta={metricDelta(current[active!], previous[active!], metric)} />
              </span>
            </div>
          )}
          <dl className="mt-2 grid grid-cols-2 gap-x-3 gap-y-0.5 border-t border-slate-100 pt-2 text-slate-500">
            <dt>Delivered</dt>
            <dd className="text-right font-medium tabular-nums text-slate-700">{formatCount(day.delivered)}</dd>
            <dt>Failed</dt>
            <dd className="text-right font-medium tabular-nums text-slate-700">{formatCount(day.failed)}</dd>
            <dt>Cancelled</dt>
            <dd className="text-right font-medium tabular-nums text-slate-700">{formatCount(day.cancelled)}</dd>
            <dt>New parcels</dt>
            <dd className="text-right font-medium tabular-nums text-slate-700">{formatCount(day.created)}</dd>
          </dl>
        </div>
      )}
    </div>
  );
}

function TrendTable({ days }: { days: Day[] }) {
  return (
    <div className="mt-4 max-h-[280px] overflow-auto rounded-xl ring-1 ring-slate-200">
      <table className="w-full text-sm">
        <caption className="sr-only">Parcels per day</caption>
        <thead className="sticky top-0 bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
          <tr>
            {["Day", "New parcels", "Delivered", "Failed", "Cancelled", "Success"].map((h, i) => (
              <th key={h} scope="col" className={`px-3 py-2 font-medium ${i === 0 ? "text-left" : "text-right"}`}>
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {days.map((d) => (
            <tr key={d.date} className="hover:bg-slate-50">
              <td className="px-3 py-1.5 text-slate-700">{formatDay(d.date, "long")}</td>
              <td className="px-3 py-1.5 text-right tabular-nums">{formatCount(d.created)}</td>
              <td className="px-3 py-1.5 text-right tabular-nums">{formatCount(d.delivered)}</td>
              <td className="px-3 py-1.5 text-right tabular-nums">{formatCount(d.failed)}</td>
              <td className="px-3 py-1.5 text-right tabular-nums">{formatCount(d.cancelled)}</td>
              <td className="px-3 py-1.5 text-right tabular-nums">{formatMetric(metricValue(d, "successRate"), "successRate")}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
