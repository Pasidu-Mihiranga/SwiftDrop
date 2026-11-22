"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { businessDate, dispatchRequest, type DispatchParcel } from "@/lib/dispatch";
import { CourierOperations } from "@/components/courier/courier-operations";

export function RunSheet() {
  const [date, setDate] = useState(businessDate);
  const [parcels, setParcels] = useState<DispatchParcel[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const revision = useRef(0);

  const load = useCallback(async () => {
    const id = ++revision.current;
    try {
      const list = await dispatchRequest<DispatchParcel[]>(`courier/run-sheet?date=${date}`);
      if (id === revision.current) { setParcels(list); setError(""); }
    } catch (e) {
      if (id === revision.current) setError(e instanceof Error ? e.message : "Cannot load run sheet");
    } finally {
      if (id === revision.current) setLoading(false);
    }
  }, [date]);

  useEffect(() => {
    setLoading(true);
    setParcels([]);
    void load();
    const timer = setInterval(() => void load(), 5000);
    return () => { clearInterval(timer); revision.current++; };
  }, [load]);

  return <div className="space-y-5">
    <header className="flex flex-wrap items-end justify-between gap-4">
      <div><p className="text-xs text-slate-500">Courier operations</p><h1 className="mt-1 text-3xl font-semibold text-slate-900">Your daily run sheet</h1><p className="mt-1 text-sm text-slate-600">Your assigned parcels, refreshed automatically. Dates use Sri Lanka time.</p></div>
      <button className="btn-secondary" onClick={() => void load()}>Refresh run sheet</button>
    </header>
    <label className="block text-sm">Run date <input aria-label="Run date" type="date" className="ml-2 rounded-lg border border-slate-200 p-2" value={date} onChange={e => { if (e.target.value) setDate(e.target.value); }} /></label>
    {error && <p role="alert" className="rounded-xl bg-red-50 p-4 text-red-700">{error}</p>}
    <section aria-label="Assigned parcels" aria-busy={loading} className="rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
      <h2 className="font-semibold">{parcels.length} assigned parcels</h2>
      {loading ? <p className="py-10 text-center text-slate-500">Loading run sheet...</p> : parcels.length === 0 ? <p className="py-10 text-center text-slate-500">No parcels assigned for this day.</p> : <CourierOperations parcels={parcels} onRefresh={() => void load()} />}
    </section>
  </div>;
}
