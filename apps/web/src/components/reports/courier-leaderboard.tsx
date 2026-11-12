"use client";

import { useMemo, useState } from "react";
import { type CourierReport, formatCount, initials } from "@/lib/reports";
import { Icon } from "./icons";
import { SuccessPill } from "./zone-breakdown";

type Key = "delivered" | "failed" | "successRate";
const COLUMNS: { key: Key; label: string }[] = [
  { key: "delivered", label: "Delivered" },
  { key: "failed", label: "Failed" },
  { key: "successRate", label: "Success" },
];
const PAGE = 8;

/** Couriers ranked by deliveries; searchable and sortable, with the full list one click away. */
export function CourierLeaderboard({ couriers }: { couriers: CourierReport[] }) {
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState<{ key: Key; descending: boolean }>({ key: "delivered", descending: true });
  const [showAll, setShowAll] = useState(false);

  const ranked = useMemo(
    () =>
      [...couriers]
        .sort((a, b) => b.delivered - a.delivered || a.courierId.localeCompare(b.courierId))
        .map((courier, index) => ({ ...courier, rank: index + 1 })),
    [couriers],
  );
  const rows = useMemo(() => {
    const q = query.trim().toLowerCase();
    const value = (c: CourierReport) => c[sort.key] ?? -1;
    return ranked
      .filter((c) => !q || c.courierId.toLowerCase().includes(q))
      .sort((a, b) => (sort.descending ? value(b) - value(a) : value(a) - value(b)) || a.rank - b.rank);
  }, [ranked, query, sort]);
  const visible = showAll || query ? rows : rows.slice(0, PAGE);
  const maxDelivered = Math.max(1, ...couriers.map((c) => c.delivered));

  function toggle(key: Key) {
    setSort((s) => (s.key === key ? { key, descending: !s.descending } : { key, descending: key !== "failed" }));
  }

  return (
    <section className="viz-root flex min-w-0 flex-col rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200 sm:p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="font-semibold text-slate-900">Couriers</h2>
          <p className="text-sm text-slate-500">
            {formatCount(couriers.length)} active in this period &middot; all zones
          </p>
        </div>
        <label className="flex items-center gap-2 rounded-lg px-2.5 py-1.5 ring-1 ring-slate-200 focus-within:ring-2 focus-within:ring-brand-500">
          <Icon name="search" className="h-4 w-4 text-slate-400" />
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Find a courier"
            aria-label="Find a courier"
            className="w-32 text-sm outline-none placeholder:text-slate-400"
          />
        </label>
      </div>

      <div className="mt-3 overflow-x-auto">
        <table className="w-full text-sm">
          <caption className="sr-only">Couriers by deliveries</caption>
          <thead>
            <tr className="border-b border-slate-100 text-xs text-slate-500">
              <th scope="col" className="w-7 py-2 pr-1 text-left font-medium">#</th>
              <th scope="col" className="py-2 text-left font-medium">Courier</th>
              {COLUMNS.map((column) => (
                <th
                  key={column.key}
                  scope="col"
                  aria-sort={sort.key === column.key ? (sort.descending ? "descending" : "ascending") : "none"}
                  className={`py-2 text-right font-medium ${column.key === "failed" ? "hidden sm:table-cell" : ""}`}
                >
                  <button
                    type="button"
                    onClick={() => toggle(column.key)}
                    className={`inline-flex items-center gap-1 rounded px-1 hover:text-slate-900 ${sort.key === column.key ? "text-slate-900" : ""}`}
                  >
                    {column.label}
                    <Icon name={sort.key === column.key ? (sort.descending ? "down" : "up") : "sort"} className="h-3 w-3" />
                  </button>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {visible.map((courier) => (
              <tr key={courier.courierId} className="border-b border-slate-50 last:border-0 hover:bg-slate-50">
                <td className="py-2 pr-1 text-xs tabular-nums text-slate-400">{courier.rank}</td>
                <td className="py-2">
                  <span className="flex items-center gap-2">
                    <span
                      aria-hidden="true"
                      className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-[11px] font-semibold ${
                        courier.rank <= 3 ? "bg-brand-600 text-white" : "bg-slate-100 text-slate-600"
                      }`}
                    >
                      {initials(courier.courierId)}
                    </span>
                    <span className="block max-w-[6.5rem] truncate font-medium text-slate-800 sm:max-w-[12rem]" title={courier.courierId}>
                      {courier.courierId}
                    </span>
                  </span>
                </td>
                <td className="py-2">
                  <span className="flex items-center justify-end gap-2">
                    <span aria-hidden="true" className="hidden h-1.5 w-16 overflow-hidden rounded-full bg-slate-100 sm:block">
                      <span
                        className="block h-full rounded-full bg-[var(--viz-delivered)]"
                        style={{ width: `${(courier.delivered / maxDelivered) * 100}%` }}
                      />
                    </span>
                    <span className="w-10 text-right font-semibold tabular-nums text-slate-900">{formatCount(courier.delivered)}</span>
                  </span>
                </td>
                {/* On phones the success rate stands in for the failed count, which returns from the sm breakpoint. */}
                <td className="hidden py-2 pl-2 text-right tabular-nums text-slate-600 sm:table-cell">{formatCount(courier.failed)}</td>
                <td className="py-2 pl-2">
                  <span className="flex justify-end">
                    <SuccessPill rate={courier.successRate} />
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {rows.length === 0 && <p className="py-6 text-center text-sm text-slate-500">No courier matches &quot;{query}&quot;.</p>}
      </div>

      <div className="mt-auto flex items-center justify-between pt-3 text-xs text-slate-400">
        <span>Names appear once user-service shares them.</span>
        {!query && rows.length > PAGE && (
          <button type="button" onClick={() => setShowAll((s) => !s)} className="font-medium text-brand-700 hover:underline">
            {showAll ? "Show top 8" : `Show all ${formatCount(rows.length)}`}
          </button>
        )}
      </div>
    </section>
  );
}
