import Link from "next/link";
import type { RateMatrix, RateMatrixCell } from "@/lib/pricing";

/** Rates at a glance. A cell links to its lane, where the price is changed by the usual versioning. */
export function RateMatrixGrid({ matrix }: { matrix: RateMatrix }) {
  if (matrix.zones.length === 0) {
    return <p className="notice-info">No active zones yet, so there are no lanes to price.</p>;
  }

  return (
    <div className="overflow-hidden rounded-xl border border-slate-200 shadow-2xs">
      <div className="overflow-x-auto">
        <table className="min-w-full border-collapse text-xs">
          <caption className="sr-only">Base fee and per-kilogram rate in LKR for each lane</caption>
          <thead>
            <tr className="border-b border-slate-200 bg-slate-50/80">
              <th scope="col" className="sticky left-0 z-10 bg-slate-50 p-2.5 text-left font-semibold text-slate-600">
                From \ To
              </th>
              {matrix.zones.map((zone) => (
                <th key={zone} scope="col" className="p-2.5 text-center font-bold text-slate-800">
                  <span className="inline-block rounded-md bg-white px-2 py-0.5 shadow-2xs ring-1 ring-slate-200/70">
                    {zone}
                  </span>
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100 bg-white">
            {matrix.rows.map((row) => (
              <tr key={row.fromZone} className="hover:bg-slate-50/50 transition">
                <th
                  scope="row"
                  className="sticky left-0 z-10 border-r border-slate-100 bg-slate-50/90 p-2.5 text-left font-bold text-slate-800"
                >
                  <span className="inline-block rounded-md bg-white px-2 py-0.5 shadow-2xs ring-1 ring-slate-200/70">
                    {row.fromZone}
                  </span>
                </th>
                {row.cells.map((cell) => (
                  <td key={cell.toZone} className="p-1.5 text-center">
                    <LaneLink cell={cell} />
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function LaneLink({ cell }: { cell: RateMatrixCell }) {
  const query = new URLSearchParams({ fromZone: cell.fromZone, toZone: cell.toZone });
  const lane = `${cell.fromZone} to ${cell.toZone}`;
  const { card } = cell;

  return (
    <Link
      href={`/admin/pricing/rate-cards?${query}#publish`}
      aria-label={card ? `Change the rate for ${lane}` : `Add a rate for ${lane}`}
      className={`block rounded-lg p-1.5 tabular-nums transition ${
        card
          ? "border border-slate-200/80 bg-white hover:border-brand-300 hover:bg-brand-50/40 hover:shadow-2xs"
          : "border border-dashed border-amber-200 bg-amber-50/40 hover:border-amber-400 hover:bg-amber-100/50"
      }`}
    >
      {card ? (
        <>
          <span className="block font-semibold text-slate-900">{card.baseFee}</span>
          <span className="block text-[11px] text-slate-500 font-medium">
            {card.tiers?.length ? "bands" : `+${card.perKgFee}/kg`}
          </span>
        </>
      ) : (
        <span className="block font-medium text-amber-700 text-xs">None</span>
      )}
    </Link>
  );
}
