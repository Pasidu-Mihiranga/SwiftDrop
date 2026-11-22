"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { useDispatchFeed } from "@/components/tracking/use-dispatch-feed";
import { businessDate, dispatchRequest, parcelAge, rankedCouriers, zoneMatches, type DispatchPage, type DispatchParcel, type DispatchZone, type Workload } from "@/lib/dispatch";

export function DispatchBoard() {
  const [pool, setPool] = useState<DispatchPage>({ content: [], totalElements: 0, page: 0, size: 20 });
  const [assigned, setAssigned] = useState<DispatchParcel[]>([]);
  const [workloads, setWorkloads] = useState<Workload[]>([]);
  const [zones, setZones] = useState<DispatchZone[]>([]);
  const [zone, setZone] = useState("");
  const [page, setPage] = useState(0);
  const [date, setDate] = useState(businessDate);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [selected, setSelected] = useState<DispatchParcel | null>(null);
  const [courierId, setCourierId] = useState("");
  const [saving, setSaving] = useState(false);
  const [modalError, setModalError] = useState("");
  const dialog = useRef<HTMLDialogElement>(null);
  const requestId = useRef(0);
  const feedRevision = JSON.stringify(useDispatchFeed());
  const load = useCallback(async () => {
    const id = ++requestId.current;
    try {
      const [pool, workloads, assigned, zones] = await Promise.all([
        dispatchRequest<DispatchPage>(`dispatch/unassigned?page=${page}&size=20${zone ? `&zone=${encodeURIComponent(zone)}` : ""}`),
        dispatchRequest<Workload[]>("dispatch/workloads"),
        dispatchRequest<DispatchParcel[]>(`dispatch/assignments?date=${date}`),
        dispatchRequest<DispatchZone[]>("admin/pricing/zones"),
      ]);
      if (id !== requestId.current) return;
      setPool(pool); setWorkloads(workloads); setAssigned(assigned); setZones(zones); setError("");
      if (page > 0 && pool.content.length === 0) setPage(page - 1);
    } catch (e) { if (id === requestId.current) setError(e instanceof Error ? e.message : "Cannot load dispatch board"); }
    finally { if (id === requestId.current) setLoading(false); }
  }, [zone, page, date]);
  useEffect(() => {
    setLoading(true); void load();
    // The short refresh also covers projection lag and a disconnected socket.
    const timer = setInterval(() => void load(), 5000);
    return () => { clearInterval(timer); requestId.current++; };
  }, [load]);
  useEffect(() => { const timer = setTimeout(() => void load(), 350); return () => clearTimeout(timer); }, [feedRevision, load]);
  useEffect(() => { if (selected) dialog.current?.showModal(); else dialog.current?.close(); }, [selected]);
  function open(parcel: DispatchParcel) {
    setSelected(parcel); setCourierId(rankedCouriers(workloads, parcel, zones)[0]?.courierId ?? ""); setModalError("");
  }
  async function save() {
    if (!selected || !courierId || saving) return;
    setSaving(true); setModalError("");
    try {
      const reassign = selected.assignmentId;
      await dispatchRequest(reassign ? `dispatch/assignments/${reassign}/reassign` : "dispatch/assignments", {
        method: "POST", headers: { "content-type": "application/json" },
        body: JSON.stringify(reassign ? { courierId } : { parcelId: selected.parcelId, courierId, runDate: date }),
      });
      setSelected(null); setNotice(reassign ? "Parcel reassigned. Run sheets updated." : "Parcel assigned. Courier run sheet updated.");
      await load();
    } catch (e) { setModalError(e instanceof Error ? e.message : "Assignment failed"); await load(); }
    finally { setSaving(false); }
  }
  const active = workloads.reduce((sum, c) => sum + c.activeCount, 0);
  const candidates = selected ? rankedCouriers(workloads, selected, zones) : [];
  const card = "rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200";
  return <div className="space-y-5">
    <header className="flex flex-wrap items-end justify-between gap-4">
      <div><p className="text-xs text-slate-500">Operations / Dispatch</p><h1 className="mt-1 text-3xl font-semibold tracking-tight text-slate-900">Dispatch &amp; workload board</h1><p className="mt-1 text-sm text-slate-600">Allocate parcels, balance the fleet, and keep daily runs moving.</p></div>
      <div className="flex gap-2"><Link href="/admin/couriers" className="btn-secondary">Configure couriers</Link><button className="btn-secondary" onClick={() => void load()}>Refresh board</button></div>
    </header>
    {error && <p role="alert" className="rounded-xl bg-red-50 p-4 text-red-700">{error}</p>}
    {notice && <p role="status" className="rounded-xl bg-brand-50 p-4 text-brand-700">{notice}</p>}
    <section aria-label="Dispatch summary" className="grid grid-cols-2 gap-4 lg:grid-cols-4">
      {[["Awaiting assignment", pool.totalElements], ["Active deliveries", active], ["Delivered today", workloads.reduce((s,c) => s+c.deliveredToday,0)], ["Failed today", workloads.reduce((s,c) => s+c.failedToday,0)]].map(([label,value]) => <div className={card} key={label}><p className="text-sm text-slate-500">{label}</p><p className="mt-2 text-3xl font-semibold text-slate-900">{value}</p></div>)}
    </section>
    <div className="flex flex-wrap items-center gap-4">
      <label className="text-sm">Zone <select aria-label="Zone filter" value={zone} onChange={e => { setZone(e.target.value); setPage(0); }} className="ml-2 rounded-lg border border-slate-200 p-2"><option value="">All zones</option>{zones.map(z => <option key={z.id} value={z.code}>{z.name}</option>)}</select></label>
      <label className="text-sm">Run date <input aria-label="Run date" type="date" value={date} onChange={e => { if(e.target.value) setDate(e.target.value); }} className="ml-2 rounded-lg border border-slate-200 p-2" /></label>
      <span className="text-xs text-slate-500">Updates automatically every 5 seconds and on parcel activity</span>
    </div>
    <div className="grid gap-5 xl:grid-cols-3">
      <section className={`${card} xl:col-span-2`} aria-busy={loading}>
        <h2 className="font-semibold text-slate-900">Unassigned parcels</h2><p className="mt-1 text-xs text-slate-500">Oldest bookings first</p>
        <div className="mt-4 overflow-x-auto"><table className="w-full text-left text-sm"><thead className="border-b border-slate-200 text-xs text-slate-500"><tr>{["Parcel", "Route", "Zone / size", "Age", "Action"].map(h => <th key={h} className="p-3">{h}</th>)}</tr></thead><tbody>{pool.content.map(p => <tr key={p.parcelId} className="border-b border-slate-100"><td className="p-3 font-mono text-brand-700">{p.trackingNumber}</td><td className="p-3">{p.pickupCity} &rarr; {p.dropoffCity}</td><td className="p-3">{p.zone} / {p.sizeClass}</td><td className="p-3"><time dateTime={p.createdAt} title={new Date(p.createdAt).toLocaleString()}>{parcelAge(p.createdAt)}</time></td><td className="p-3"><button className="btn-primary" onClick={() => open(p)}>Assign</button></td></tr>)}</tbody></table></div>
        {pool.content.length === 0 && <p className="py-12 text-center text-sm text-slate-500">{loading ? "Loading parcels..." : "No parcels awaiting assignment in this zone."}</p>}
        <div className="mt-4 flex items-center justify-between text-xs text-slate-500"><span>{pool.totalElements} parcels · Page {page+1}</span><div className="flex gap-2"><button className="btn-secondary" disabled={page===0} onClick={() => setPage(page-1)}>Previous</button><button className="btn-secondary" disabled={(page+1)*20>=pool.totalElements} onClick={() => setPage(page+1)}>Next</button></div></div>
      </section>
      <section className={card}><h2 className="font-semibold text-slate-900">Courier workloads</h2><p className="mt-1 text-xs text-slate-500">Active / capacity · today&apos;s outcomes</p>
        <ul className="mt-4 space-y-4">{workloads.map(c => <li key={c.courierId} className="rounded-xl bg-slate-50 p-3"><div className="flex justify-between gap-2 text-sm"><span className="font-medium">{c.courierName}</span><span>{c.activeCount} / {c.capacity ?? "Unset"}</span></div><LoadBar courier={c}/><p className="mt-2 text-xs text-slate-500">{c.deliveredToday} delivered · {c.failedToday} failed · {c.assignable ? "Available" : "Unavailable"}</p></li>)}</ul>
        {!loading && workloads.length===0 && <p className="py-8 text-sm text-slate-500">No couriers configured.</p>}
      </section>
    </div>
    <section className={card}><h2 className="font-semibold text-slate-900">Assigned runs · {date}</h2><div className="mt-4 overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr>{["Parcel", "Courier", "Destination", "Status", "Action"].map(h => <th className="p-3 text-xs text-slate-500" key={h}>{h}</th>)}</tr></thead><tbody>{assigned.map(p => <tr key={p.parcelId} className="border-t border-slate-100"><td className="p-3 font-mono">{p.trackingNumber}</td><td className="p-3">{workloads.find(c=>c.courierId===p.courierId)?.courierName ?? p.courierId}</td><td className="p-3">{p.dropoffCity}</td><td className="p-3">{p.status.replaceAll("_", " ")}</td><td className="p-3"><button className="btn-secondary" disabled={["DELIVERED","FAILED","CANCELLED"].includes(p.status)} onClick={() => open(p)}>Reassign</button></td></tr>)}</tbody></table></div>{assigned.length===0 && <p className="py-8 text-center text-sm text-slate-500">No assignments for this day.</p>}</section>
    <dialog ref={dialog} aria-labelledby="allocation-title" onCancel={e => { if (saving) e.preventDefault(); }} onClose={() => { if (!saving) setSelected(null); }} className="m-auto w-full max-w-lg rounded-2xl p-6 shadow-xl backdrop:bg-slate-900/40">
      <form onSubmit={e => { e.preventDefault(); void save(); }}>
        <h2 id="allocation-title" className="text-xl font-semibold">{selected?.assignmentId ? "Reassign parcel" : "Assign parcel"}</h2><p className="mt-1 text-sm text-slate-500">{selected?.trackingNumber} · {selected?.zone} · {date}</p>
        {modalError && <p role="alert" className="mt-3 text-sm text-red-700">{modalError}</p>}
        <fieldset disabled={saving} className="mt-5 max-h-80 space-y-3 overflow-y-auto"><legend className="mb-2 text-sm font-medium">Choose courier · zone matches first, then lowest load</legend>{candidates.map(c => <label key={c.courierId} className={`block cursor-pointer rounded-xl border p-3 ${courierId===c.courierId ? "border-brand-600 bg-brand-50" : "border-slate-200"}`}><span className="flex items-center justify-between gap-2 text-sm"><span><input type="radio" name="courier" value={c.courierId} checked={courierId===c.courierId} onChange={() => setCourierId(c.courierId)}/> {c.courierName}</span><span>{c.activeCount} / {c.capacity ?? "Unset"}</span></span><LoadBar courier={c}/>{selected && zoneMatches(c,selected,zones) && <span className="text-xs font-medium text-brand-700">Zone match</span>}</label>)}</fieldset>
        {candidates.length===0 && <p className="my-4 text-sm text-slate-500">No available couriers. Configure the fleet before assigning.</p>}
        <div className="mt-5 flex justify-end gap-2"><button type="button" disabled={saving} className="btn-secondary" onClick={() => setSelected(null)}>Cancel</button><button disabled={saving || !candidates.some(c=>c.courierId===courierId)} className="btn-primary" type="submit">{saving ? "Saving..." : "Confirm assignment"}</button></div>
      </form>
    </dialog>
  </div>;
}
function LoadBar({ courier }: { courier: Workload }) {
  const value = courier.capacity ? Math.min(100, courier.activeCount/courier.capacity*100) : 0;
  return <div role="meter" aria-label={`${courier.courierName} workload`} aria-valuemin={0} aria-valuemax={Math.max(courier.capacity ?? 1,courier.activeCount)} aria-valuenow={courier.activeCount} className="my-2 h-2 overflow-hidden rounded-full bg-[var(--viz-track)]"><div className="h-full rounded-full bg-brand-600 transition-all" style={{ width: `${value}%` }}/></div>;
}
