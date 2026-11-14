import Link from "next/link";
import type { RateMatrix, RateMatrixCell } from "@/lib/pricing";

/** Rates at a glance. A cell links to its lane, where the price is changed by the usual versioning. */
export function RateMatrixGrid({ matrix }: { matrix: RateMatrix }) {
  if (matrix.zones.length === 0) {
    return <p className="notice-info">No active zones yet, so there are no lanes to price.</p>;
  }

  return (
    <div className="overflow-x-auto">
      <table className="min-w-full border-collapse text-xs">
        <caption className="sr-only">Base fee and per-kilogram rate in LKR for each lane</caption>
        <thead>
          <tr>
            <th scope="col" className="p-2 text-left font-medium text-slate-500">
              From / To
            </th>
            {matrix.zones.map((zone) => (
              <th key={zone} scope="col" className="p-2 text-center font-semibold">
                {zone}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {matrix.rows.map((row) => (
            <tr key={row.fromZone} className="border-t border-slate-100">
              <th scope="row" className="p-2 text-left font-semibold">
                {row.fromZone}
              </th>
              {row.cells.map((cell) => (
                <td key={cell.toZone} className="p-1 text-center">
                  <LaneLink cell={cell} />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
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
      className="block rounded-md px-2 py-1 tabular-nums hover:bg-slate-100"
    >
      {card ? (
        <>
          <span className="block font-medium text-slate-700">{card.baseFee}</span>
          <span className="block text-slate-500">{card.tiers?.length ? "bands" : `+${card.perKgFee}/kg`}</span>
        </>
      ) : (
        <span className="block font-medium text-slate-400">None</span>
      )}
    </Link>
  );
}
