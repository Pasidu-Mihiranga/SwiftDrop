import Link from "next/link";
import { PRESETS, type Preset, type ReportFilter, reportsHref, zoneLabel } from "@/lib/reports";

const PRESET_LABELS: Record<Exclude<Preset, "custom">, string> = {
  "7d": "Last 7 days",
  "30d": "Last 30 days",
  "90d": "Last 90 days",
};

interface Props {
  filter: ReportFilter;
  /** Zone codes to offer in the zone filter. */
  zones: string[];
  zoneNames: Record<string, string>;
}

/**
 * One filter row above everything it scopes. Plain links and GET forms, so it works without
 * client JavaScript and every view has a shareable URL.
 */
export function ReportFilters({ filter, zones, zoneNames }: Props) {
  return (
    <div className="flex flex-wrap items-end gap-x-4 gap-y-3">
      <nav aria-label="Period" className="flex flex-wrap gap-1 rounded-lg border border-slate-200 bg-white p-1">
        {(Object.keys(PRESETS) as (keyof typeof PRESETS)[]).map((preset) => {
          const selected = filter.preset === preset;
          return (
            <Link
              key={preset}
              href={reportsHref({ preset, zone: filter.zone })}
              aria-current={selected ? "true" : undefined}
              className={`inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm ${
                selected ? "bg-brand-50 font-semibold text-brand-700" : "text-slate-600 hover:bg-slate-50"
              }`}
            >
              {selected && <CheckIcon />}
              {PRESET_LABELS[preset]}
            </Link>
          );
        })}
      </nav>

      <form action="/admin/reports" method="get" className="flex flex-wrap items-end gap-2" aria-label="Custom date range">
        <label className="flex flex-col text-xs font-medium text-slate-600">
          From
          <input type="date" name="from" defaultValue={filter.from} required className="input mt-1 w-auto py-1.5" />
        </label>
        <label className="flex flex-col text-xs font-medium text-slate-600">
          To
          <input type="date" name="to" defaultValue={filter.to} required className="input mt-1 w-auto py-1.5" />
        </label>
        {filter.zone && <input type="hidden" name="zone" value={filter.zone} />}
        <button type="submit" className={`btn-secondary py-1.5 ${filter.preset === "custom" ? "ring-2 ring-brand-100" : ""}`}>
          Show range
        </button>
      </form>

      <form action="/admin/reports" method="get" className="flex items-end gap-2" aria-label="Zone">
        {filter.preset === "custom" ? (
          <>
            <input type="hidden" name="from" value={filter.from} />
            <input type="hidden" name="to" value={filter.to} />
          </>
        ) : (
          <input type="hidden" name="range" value={filter.preset} />
        )}
        <label className="flex flex-col text-xs font-medium text-slate-600">
          Zone
          <select name="zone" defaultValue={filter.zone ?? ""} className="input mt-1 w-auto py-1.5">
            <option value="">All zones</option>
            {zones.map((code) => (
              <option key={code} value={code}>
                {zoneLabel(code, zoneNames)}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" className="btn-secondary py-1.5">Apply</button>
      </form>
    </div>
  );
}

function CheckIcon() {
  return (
    <svg aria-hidden="true" width="16" height="16" viewBox="0 0 16 16" fill="none">
      <path d="M3.5 8.5l3 3 6-7" stroke="currentColor" strokeWidth="2.25" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
