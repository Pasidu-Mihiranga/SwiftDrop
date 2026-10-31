"use client";

import { type KeyboardEvent, type PointerEvent, useEffect, useRef, useState } from "react";
import { type Day, formatCount, formatDay, labelIndexes, niceTicks } from "@/lib/reports";

const HEIGHT = 240;
const MARGIN = { top: 12, bottom: 28, left: 44 };
/** Room for the end labels; kept small when they are hidden. */
const RIGHT_WITH_LABELS = 104;
const RIGHT_WITHOUT_LABELS = 16;

/** The story is deliveries; parcels created is the context it is read against. */
const SERIES = [
  { key: "delivered", label: "Delivered", colour: "var(--viz-delivered)" },
  { key: "created", label: "Created", colour: "var(--viz-context)" },
] as const;

interface Props {
  days: Day[];
}

/**
 * Deliveries per day against parcels created, on one axis (same unit). A crosshair snaps to the
 * nearest day and reads out every value for it; arrow keys do the same from the keyboard.
 */
export function DailyTrendChart({ days }: Props) {
  const wrapper = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(720);
  const [active, setActive] = useState<number | null>(null);

  useEffect(() => {
    const element = wrapper.current;
    if (!element) return;
    const observer = new ResizeObserver(([entry]) => setWidth(Math.max(320, Math.floor(entry.contentRect.width))));
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  if (days.length === 0) return null;

  const plotHeight = HEIGHT - MARGIN.top - MARGIN.bottom;
  const ticks = niceTicks(Math.max(0, ...days.flatMap((d) => [d.created, d.delivered])));
  const top = ticks[ticks.length - 1];
  const y = (value: number) => MARGIN.top + plotHeight - (value / top) * plotHeight;

  const last = days.length - 1;
  const endY = { delivered: y(days[last].delivered), created: y(days[last].created) };
  // End labels only when they cannot collide; otherwise the legend and the readout carry identity.
  const showEndLabels = Math.abs(endY.delivered - endY.created) >= 16;

  const plotWidth = width - MARGIN.left - (showEndLabels ? RIGHT_WITH_LABELS : RIGHT_WITHOUT_LABELS);
  const x = (i: number) => MARGIN.left + (days.length === 1 ? plotWidth / 2 : (i * plotWidth) / (days.length - 1));
  const path = (key: "delivered" | "created") =>
    days.map((d, i) => `${i === 0 ? "M" : "L"}${x(i).toFixed(1)},${y(d[key]).toFixed(1)}`).join("");

  function nearest(event: PointerEvent<SVGRectElement>) {
    const box = event.currentTarget.getBoundingClientRect();
    const ratio = (event.clientX - box.left) / box.width;
    setActive(Math.min(last, Math.max(0, Math.round(ratio * last))));
  }

  function onKey(event: KeyboardEvent<SVGSVGElement>) {
    const moves: Record<string, number> = { ArrowLeft: -1, ArrowRight: 1 };
    if (event.key in moves) {
      setActive((current) => Math.min(last, Math.max(0, (current ?? last) + moves[event.key])));
      event.preventDefault();
    } else if (event.key === "Home" || event.key === "End") {
      setActive(event.key === "Home" ? 0 : last);
      event.preventDefault();
    } else if (event.key === "Escape") {
      setActive(null);
    }
  }

  const day = active === null ? null : days[active];
  // Beside the crosshair, not over it, so the highlighted points stay visible.
  const tooltipLeft = active === null ? 0 : x(active) + 188 <= width ? x(active) + 12 : Math.max(0, x(active) - 188);

  return (
    <div className="viz-root">
      <Legend />
      <div ref={wrapper} className="relative mt-2">
        <svg
          width={width}
          height={HEIGHT}
          role="img"
          aria-label={`Deliveries per day from ${formatDay(days[0].date, "long")} to ${formatDay(days[last].date, "long")}. Use the left and right arrow keys to read each day.`}
          tabIndex={0}
          onKeyDown={onKey}
          onFocus={() => setActive((current) => current ?? last)}
          onBlur={() => setActive(null)}
          className="block max-w-full rounded focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500"
        >
          {ticks.map((tick) => (
            <g key={tick}>
              <line
                x1={MARGIN.left}
                x2={MARGIN.left + plotWidth}
                y1={y(tick)}
                y2={y(tick)}
                stroke={tick === 0 ? "var(--viz-axis)" : "var(--viz-grid)"}
                strokeWidth={1}
                shapeRendering="crispEdges"
              />
              <text x={MARGIN.left - 8} y={y(tick)} dy="0.32em" textAnchor="end" className="fill-slate-500 text-[11px] tabular-nums">
                {formatCount(tick)}
              </text>
            </g>
          ))}

          {labelIndexes(days.length, Math.max(2, Math.floor(plotWidth / 72))).map((i) => (
            <text
              key={days[i].date}
              x={x(i)}
              y={HEIGHT - 8}
              // A label centred on the last tick would run past the edge when there are no end labels.
              textAnchor={x(i) + 24 > width ? "end" : "middle"}
              className="fill-slate-500 text-[11px]"
            >
              {formatDay(days[i].date)}
            </text>
          ))}

          {SERIES.slice()
            .reverse()
            .map((series) => (
              <path
                key={series.key}
                d={path(series.key)}
                fill="none"
                stroke={series.colour}
                strokeWidth={2}
                strokeLinejoin="round"
                strokeLinecap="round"
              />
            ))}

          {SERIES.map((series) => (
            <g key={series.key}>
              <circle cx={x(last)} cy={endY[series.key]} r={4} fill={series.colour} stroke="var(--viz-surface)" strokeWidth={2} />
              {showEndLabels && (
                <text x={x(last) + 10} y={endY[series.key]} dy="0.32em" className="fill-slate-700 text-xs">
                  {series.label} {formatCount(days[last][series.key])}
                </text>
              )}
            </g>
          ))}

          {active !== null && (
            <g pointerEvents="none">
              <line x1={x(active)} x2={x(active)} y1={MARGIN.top} y2={MARGIN.top + plotHeight} stroke="var(--viz-axis)" strokeWidth={1} />
              {SERIES.map((series) => (
                <circle
                  key={series.key}
                  cx={x(active)}
                  cy={y(days[active][series.key])}
                  r={4}
                  fill={series.colour}
                  stroke="var(--viz-surface)"
                  strokeWidth={2}
                />
              ))}
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
            className="pointer-events-none absolute top-0 w-44 rounded-lg border border-slate-200 bg-white p-3 text-xs shadow-md"
            style={{ left: tooltipLeft }}
          >
            <p className="font-medium text-slate-600">{formatDay(day.date, "long")}</p>
            <ul className="mt-2 space-y-1">
              {SERIES.map((series) => (
                <li key={series.key} className="flex items-center gap-2">
                  <span aria-hidden="true" className="h-0.5 w-3 rounded" style={{ background: series.colour }} />
                  <span className="font-semibold text-slate-900 tabular-nums">{formatCount(day[series.key])}</span>
                  <span className="text-slate-500">{series.label.toLowerCase()}</span>
                </li>
              ))}
              <li className="flex items-center gap-2 pl-5">
                <span className="font-semibold text-slate-900 tabular-nums">{formatCount(day.failed)}</span>
                <span className="text-slate-500">failed attempts</span>
              </li>
              <li className="flex items-center gap-2 pl-5">
                <span className="font-semibold text-slate-900 tabular-nums">{formatCount(day.cancelled)}</span>
                <span className="text-slate-500">cancelled</span>
              </li>
            </ul>
          </div>
        )}
      </div>
    </div>
  );
}

function Legend() {
  return (
    <ul className="flex flex-wrap gap-4 text-xs text-slate-600" aria-label="Legend">
      {SERIES.map((series) => (
        <li key={series.key} className="flex items-center gap-1.5">
          <span aria-hidden="true" className="h-0.5 w-4 rounded" style={{ background: series.colour }} />
          {series.label}
        </li>
      ))}
    </ul>
  );
}
