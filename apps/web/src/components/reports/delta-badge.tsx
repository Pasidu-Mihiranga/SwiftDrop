import type { Delta } from "@/lib/reports";
import { Icon } from "./icons";

const TONES: Record<Delta["tone"], string> = {
  good: "bg-emerald-50 text-[var(--viz-good)] ring-emerald-600/15",
  bad: "bg-red-50 text-[var(--viz-bad)] ring-red-600/15",
  neutral: "bg-slate-100 text-slate-600 ring-slate-500/10",
};

/** A change against the previous period: arrow + signed value, coloured by whether it is good news. */
export function DeltaBadge({ delta, label }: { delta: Delta | null; label?: string }) {
  if (!delta) return <span className="text-xs text-slate-400">No earlier data</span>;
  const spoken = `${delta.direction === "flat" ? "" : delta.direction === "up" ? "Up " : "Down "}${delta.text}${label ? ` ${label}` : ""}`;
  return (
    <span className="inline-flex items-center gap-1.5 text-xs" title={spoken}>
      <span className={`inline-flex items-center gap-0.5 rounded-full px-1.5 py-0.5 font-semibold ring-1 ring-inset ${TONES[delta.tone]}`}>
        <Icon name={delta.direction} className="h-3 w-3" />
        {delta.text}
      </span>
      {label && <span className="text-slate-500">{label}</span>}
    </span>
  );
}
