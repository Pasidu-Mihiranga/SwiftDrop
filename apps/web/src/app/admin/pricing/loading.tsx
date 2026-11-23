export default function PricingAdminLoading() {
  return (
    <div className="space-y-6 animate-pulse" aria-busy="true" aria-label="Loading pricing administration">
      <div className="space-y-2 border-b border-slate-200 pb-5">
        <div className="h-3 w-36 rounded bg-slate-200" />
        <div className="h-8 w-56 rounded bg-slate-200" />
        <div className="h-4 w-96 max-w-full rounded bg-slate-100" />
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <div className="card h-24 bg-slate-100/80" />
        <div className="card h-24 bg-slate-100/80" />
        <div className="card h-24 bg-slate-100/80" />
      </div>

      <div className="card space-y-3">
        <div className="h-5 w-44 rounded bg-slate-200" />
        <div className="h-48 w-full rounded-lg bg-slate-100" />
      </div>
    </div>
  );
}
