import {
  type Day,
  type Totals,
  countDelta,
  formatCount,
  formatPercent,
  rateDelta,
  smoothPath,
} from "@/lib/reports";
import { DeltaBadge } from "./delta-badge";

interface Props {
  totals: Totals;
  previous: Totals | null;
  days: Day[];
  /** e.g. "vs previous 30 days" */
  comparisonLabel: string;
}

type CountKey = "delivered" | "created" | "failed" | "cancelled";

const CARDS: { key: CountKey; label: string; colour: string; upIsGood: boolean }[] = [
  { key: "delivered", label: "Delivered", colour: "var(--viz-delivered)", upIsGood: true },
  { key: "created", label: "New parcels", colour: "var(--viz-created)", upIsGood: true },
  { key: "failed", label: "Failed attempts", colour: "var(--viz-failed)", upIsGood: false },
  { key: "cancelled", label: "Cancelled", colour: "var(--viz-cancelled)", upIsGood: false },
];

/** The headline row: success rate as the one hero figure, then four counts with a trend line each. */
export function KpiCards({ totals, previous, days, comparisonLabel }: Props) {
  const attempts = totals.delivered + totals.failed;
  const rate = totals.successRate;

  return (
    <section aria-label="Summary" className="viz-root grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-6">
      <div className="relative overflow-hidden rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200 col-span-2">
        <div className="pointer-events-none absolute -right-10 -top-10 h-36 w-36 rounded-full bg-brand-50" aria-hidden="true" />
        <p className="relative text-sm font-medium text-slate-600">Success rate</p>
        <p className="relative mt-1 text-5xl font-semibold tracking-tight text-slate-900">{formatPercent(rate)}</p>
        <div className="relative mt-2">
          <DeltaBadge delta={previous ? rateDelta(rate, previous.successRate) : null} label={comparisonLabel} />
        </div>
        <div
          className="relative mt-5 h-2.5 overflow-hidden rounded-full bg-[var(--viz-track)]"
          role="meter"
          aria-label="Success rate"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={rate === null ? undefined : Math.round(rate * 1000) / 10}
        >
          <div className="viz-grow h-full rounded-full bg-[var(--viz-delivered)]" style={{ width: `${(rate ?? 0) * 100}%` }} />
        </div>
        <p className="relative mt-2 text-xs text-slate-500">
          {attempts === 0
            ? "No delivery attempts in this period."
            : `${formatCount(totals.delivered)} of ${formatCount(attempts)} delivery attempts succeeded.`}
        </p>
      </div>

      {CARDS.map((card) => (
        <div key={card.key} className="flex min-w-0 flex-col rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full" style={{ background: card.colour }} />
            {card.label}
          </p>
          <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">{formatCount(totals[card.key])}</p>
          <div className="mt-1 min-h-5">
            <DeltaBadge delta={previous ? countDelta(totals[card.key], previous[card.key], card.upIsGood) : null} />
          </div>
          <Sparkline values={days.map((d) => d[card.key])} accent={card.colour} label={`${card.label} per day`} />
        </div>
      ))}
    </section>
  );
}

/** Trend only (no axes): the history in the quiet de-emphasis grey, the latest day in the card's accent. */
function Sparkline({ values, accent, label }: { values: number[]; accent: string; label: string }) {
  const width = 160;
  const height = 36;
  if (values.length < 2) return <div className="mt-auto h-9" />;
  const max = Math.max(1, ...values);
  const x = (i: number) => 2 + (i * (width - 6)) / (values.length - 1);
  const y = (v: number) => height - 4 - (v / max) * (height - 8);
  const points = values.map((v, i) => [x(i), y(v)] as const);
  const last = points[points.length - 1];
  return (
    <svg viewBox={`0 0 ${width} ${height}`} className="mt-auto h-9 w-full pt-2" preserveAspectRatio="none" role="img" aria-label={label}>
      <path d={smoothPath(points)} fill="none" stroke="var(--viz-context)" strokeWidth={1.5} vectorEffect="non-scaling-stroke" />
      {/* A round-capped zero-length stroke stays a circle although the sparkline stretches to its card. */}
      <path
        d={`M${last[0]},${last[1]}h0`}
        stroke={accent}
        strokeWidth={6}
        strokeLinecap="round"
        vectorEffect="non-scaling-stroke"
      />
    </svg>
  );
}
