import { type Delta, type Totals, countDelta, formatCount, formatPercent, rateDelta } from "@/lib/reports";

interface Props {
  totals: Totals;
  /** Totals of the previous period of the same length, or null when they could not be loaded. */
  previous: Totals | null;
  /** e.g. "previous 30 days" */
  comparisonLabel: string;
}

/** The headline numbers for the selected period, each compared with the period just before it. */
export function StatTiles({ totals, previous, comparisonLabel }: Props) {
  const tiles: { label: string; value: string; delta: Delta | null; hint?: string }[] = [
    {
      label: "Parcels created",
      value: formatCount(totals.created),
      delta: previous && countDelta(totals.created, previous.created),
    },
    {
      label: "Delivered",
      value: formatCount(totals.delivered),
      delta: previous && countDelta(totals.delivered, previous.delivered),
    },
    {
      label: "Success rate",
      value: formatPercent(totals.successRate),
      delta: previous && rateDelta(totals.successRate, previous.successRate),
      hint: "Delivered out of all delivery attempts",
    },
    {
      label: "Failed attempts",
      value: formatCount(totals.failed),
      delta: previous && countDelta(totals.failed, previous.failed, false),
    },
    {
      label: "Cancelled",
      value: formatCount(totals.cancelled),
      delta: previous && countDelta(totals.cancelled, previous.cancelled, false),
    },
  ];

  return (
    <dl className="grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-5">
      {tiles.map((tile) => (
        <div key={tile.label} className="min-w-0 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm" title={tile.hint}>
          <dt className="text-sm text-slate-600">{tile.label}</dt>
          <dd className="mt-1 text-3xl font-semibold text-slate-900">{tile.value}</dd>
          <dd className="mt-1 text-xs">
            <DeltaText delta={tile.delta} comparisonLabel={comparisonLabel} />
          </dd>
        </div>
      ))}
    </dl>
  );
}

function DeltaText({ delta, comparisonLabel }: { delta: Delta | null; comparisonLabel: string }) {
  if (!delta) return <span className="text-slate-500">No earlier data to compare</span>;
  const colour =
    delta.tone === "good" ? "text-[var(--viz-good)]" : delta.tone === "bad" ? "text-[var(--viz-bad)]" : "text-slate-500";
  return (
    <span className="flex flex-wrap items-center gap-x-1">
      <span className={`inline-flex items-center gap-0.5 font-semibold ${colour}`}>
        <Arrow direction={delta.direction} />
        {delta.text}
      </span>
      <span className="text-slate-500">vs {comparisonLabel}</span>
    </span>
  );
}

function Arrow({ direction }: { direction: Delta["direction"] }) {
  const path = direction === "up" ? "M6 2.5v7M3 5.5l3-3 3 3" : direction === "down" ? "M6 9.5v-7M3 6.5l3 3 3-3" : "M2.5 6h7";
  return (
    <svg aria-hidden="true" width="12" height="12" viewBox="0 0 12 12" fill="none">
      <path d={path} stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
