/** First-load placeholder in the shape of the dashboard. Filter changes keep the old view instead. */
export default function Loading() {
  const block = "animate-pulse rounded-2xl bg-white ring-1 ring-slate-200";
  return (
    <div className="space-y-5" aria-busy="true" aria-label="Loading reports">
      <div className="space-y-2">
        <div className="h-4 w-32 animate-pulse rounded bg-slate-200" />
        <div className="h-8 w-64 animate-pulse rounded bg-slate-200" />
        <div className="h-4 w-48 animate-pulse rounded bg-slate-200" />
      </div>
      <div className="h-12 w-full animate-pulse rounded-xl bg-slate-200/70" />
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-6">
        <div className={`${block} h-44 sm:col-span-2`} />
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className={`${block} h-44`} />
        ))}
      </div>
      <div className="grid gap-5 lg:grid-cols-3">
        <div className={`${block} h-96 lg:col-span-2`} />
        <div className={`${block} h-96`} />
      </div>
    </div>
  );
}
