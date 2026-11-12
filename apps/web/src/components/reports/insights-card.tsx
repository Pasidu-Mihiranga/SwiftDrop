"use client";

import { type Insight, type ReportFilter, reportsHref } from "@/lib/reports";
import { Icon } from "./icons";
import { useReportsNavigation } from "./reports-navigation";

const STYLE: Record<Insight["kind"], { icon: "peak" | "risk" | "best" | "courier" | "info"; tone: string }> = {
  peak: { icon: "peak", tone: "bg-brand-50 text-brand-700" },
  risk: { icon: "risk", tone: "bg-amber-50 text-amber-700" },
  best: { icon: "best", tone: "bg-emerald-50 text-emerald-700" },
  courier: { icon: "courier", tone: "bg-violet-50 text-violet-700" },
  info: { icon: "info", tone: "bg-slate-100 text-slate-600" },
};

/** Plain-language findings for the period; a zone finding can be opened to focus the page on that zone. */
export function InsightsCard({ insights, filter }: { insights: Insight[]; filter: ReportFilter }) {
  const { navigate } = useReportsNavigation();

  return (
    <section className="flex min-w-0 flex-col rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200 sm:p-5">
      <h2 className="font-semibold text-slate-900">Insights</h2>
      <p className="text-sm text-slate-500">What stands out in this period</p>
      {insights.length === 0 ? (
        <p className="mt-6 text-sm text-slate-500">Nothing stands out yet. Insights appear once there is activity.</p>
      ) : (
        <ul className="mt-4 space-y-3">
          {insights.map((insight) => (
            <li key={insight.id} className="viz-fade flex gap-3">
              <span className={`mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${STYLE[insight.kind].tone}`}>
                <Icon name={STYLE[insight.kind].icon} />
              </span>
              <div className="min-w-0">
                <p className="text-sm font-semibold text-slate-900">{insight.title}</p>
                <p className="text-xs text-slate-500">{insight.detail}</p>
                {insight.zone && insight.zone !== filter.zone && (
                  <button
                    type="button"
                    onClick={() => navigate(reportsHref({ ...filter, zone: insight.zone }))}
                    className="mt-1 text-xs font-medium text-brand-700 hover:underline"
                  >
                    Focus on this zone
                  </button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
