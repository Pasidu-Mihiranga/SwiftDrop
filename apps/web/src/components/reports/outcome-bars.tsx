"use client";

import { useState } from "react";
import { formatCount, formatPercent } from "@/lib/reports";

/** Fixed series identities: the same outcome has the same colour in every chart. */
const OUTCOMES = {
  delivered: { label: "Delivered", colour: "var(--viz-delivered)" },
  failed: { label: "Failed attempts", colour: "var(--viz-failed)" },
  cancelled: { label: "Cancelled", colour: "var(--viz-cancelled)" },
} as const;

export type Outcome = keyof typeof OUTCOMES;

export interface OutcomeRow {
  key: string;
  label: string;
  counts: Partial<Record<Outcome, number>>;
  successRate: number | null;
}

interface Props {
  rows: OutcomeRow[];
  outcomes: Outcome[];
  /** What a row is, for screen readers, e.g. "zone". */
  noun: string;
}

/**
 * Horizontal stacked bars, one row per zone or courier, scaled to the busiest row. Each segment
 * is focusable and reads out its value; the total and success rate sit at the bar end.
 */
export function OutcomeBars({ rows, outcomes, noun }: Props) {
  const [active, setActive] = useState<{ row: string; outcome: Outcome } | null>(null);
  const total = (row: OutcomeRow) => outcomes.reduce((sum, o) => sum + (row.counts[o] ?? 0), 0);
  const max = Math.max(1, ...rows.map(total));

  return (
    <div className="viz-root">
      <ul className="flex flex-wrap gap-4 text-xs text-slate-600" aria-label="Legend">
        {outcomes.map((outcome) => (
          <li key={outcome} className="flex items-center gap-1.5">
            <span aria-hidden="true" className="h-2.5 w-2.5 rounded-sm" style={{ background: OUTCOMES[outcome].colour }} />
            {OUTCOMES[outcome].label}
          </li>
        ))}
        <li className="text-slate-500">Bar end: total and success rate</li>
      </ul>

      <ul className="mt-3 space-y-2">
        {rows.map((row) => {
          const rowTotal = total(row);
          return (
            <li key={row.key} className="grid grid-cols-[minmax(0,9rem)_1fr] items-center gap-3 text-sm">
              <span className="truncate text-slate-700" title={row.label}>
                {row.label}
              </span>
              <div className="relative flex items-center gap-2">
                <div
                  className="flex h-4 gap-[2px] overflow-hidden rounded-r"
                  style={{ width: `${(rowTotal / max) * 100}%`, minWidth: rowTotal > 0 ? 4 : 0 }}
                >
                  {outcomes.map((outcome) => {
                    const count = row.counts[outcome] ?? 0;
                    if (count === 0) return null;
                    const isActive = active?.row === row.key && active.outcome === outcome;
                    return (
                      <span
                        key={outcome}
                        tabIndex={0}
                        role="img"
                        aria-label={`${row.label}: ${formatCount(count)} ${OUTCOMES[outcome].label.toLowerCase()}`}
                        className="h-full min-w-[2px] outline-none transition-opacity focus-visible:ring-2 focus-visible:ring-brand-500"
                        style={{
                          flexGrow: count,
                          flexBasis: 0,
                          background: OUTCOMES[outcome].colour,
                          opacity: active && !isActive ? 0.55 : 1,
                        }}
                        onPointerEnter={() => setActive({ row: row.key, outcome })}
                        onPointerLeave={() => setActive(null)}
                        onFocus={() => setActive({ row: row.key, outcome })}
                        onBlur={() => setActive(null)}
                      />
                    );
                  })}
                </div>
                <span className="shrink-0 whitespace-nowrap text-xs text-slate-600">
                  <span className="font-semibold text-slate-900 tabular-nums">{formatCount(rowTotal)}</span>
                  {" · "}
                  {formatPercent(row.successRate)} success
                </span>

                {active?.row === row.key && (
                  <div
                    role="status"
                    className="pointer-events-none absolute bottom-6 left-0 z-10 whitespace-nowrap rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs shadow-md"
                  >
                    <span className="flex items-center gap-2">
                      <span aria-hidden="true" className="h-2.5 w-2.5 rounded-sm" style={{ background: OUTCOMES[active.outcome].colour }} />
                      <span className="font-semibold text-slate-900 tabular-nums">{formatCount(row.counts[active.outcome] ?? 0)}</span>
                      <span className="text-slate-500">
                        {OUTCOMES[active.outcome].label.toLowerCase()} · {row.label}
                      </span>
                    </span>
                  </div>
                )}
              </div>
            </li>
          );
        })}
      </ul>
      <p className="sr-only">
        Bars are scaled to the busiest {noun}. Use Tab to move between segments, or open the table below.
      </p>
    </div>
  );
}
