export default function QuoteLoading() {
  return (
    <div className="space-y-6 animate-pulse" aria-busy="true" aria-label="Loading delivery price calculator">
      <div className="h-3 w-40 rounded bg-slate-200" />

      <div className="flex items-start gap-4">
        <div className="h-12 w-12 shrink-0 rounded-2xl bg-slate-200" />
        <div className="space-y-2">
          <div className="h-7 w-64 rounded bg-slate-200" />
          <div className="h-4 w-80 max-w-full rounded bg-slate-100" />
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1.8fr)_minmax(0,1.2fr)] items-start">
        <div className="space-y-5">
          <div className="card h-44 bg-slate-100/80" />
          <div className="card h-36 bg-slate-100/80" />
          <div className="card h-48 bg-slate-100/80" />
        </div>
        <div className="card h-80 bg-slate-100/80" />
      </div>
    </div>
  );
}
