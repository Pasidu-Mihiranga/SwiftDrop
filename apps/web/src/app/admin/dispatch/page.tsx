"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useDispatchFeed } from "@/components/tracking/use-dispatch-feed";

interface CourierRecord {
  userId: string;
  fullName: string;
  email: string;
  vehicleType: string | null;
  homeZoneId: string | null;
  maxDailyCapacity: number | null;
  active: boolean;
  assignable: boolean;
}

interface ZoneOption {
  code: string;
  name: string;
  districts: string[];
}

export default function DispatchHome() {
  const [couriers, setCouriers] = useState<CourierRecord[]>([]);
  const [zones, setZones] = useState<ZoneOption[]>([]);
  const [selectedZone, setSelectedZone] = useState<string>("ALL");
  const [loading, setLoading] = useState(true);
  const liveFeed = useDispatchFeed();

  const loadDispatchContext = useCallback(async () => {
    setLoading(true);
    try {
      const [courierRes, zoneRes] = await Promise.all([
        fetch("/api/bff/api/v1/admin/couriers?assignable=true"),
        fetch("/api/bff/api/v1/pricing/zones"),
      ]);
      if (courierRes.ok) {
        const list = (await courierRes.json()) as CourierRecord[];
        setCouriers(Array.isArray(list) ? list : []);
      }
      if (zoneRes.ok) {
        const zList = (await zoneRes.json()) as ZoneOption[];
        setZones(Array.isArray(zList) ? zList : []);
      }
    } catch {
      // Keep graceful empty state
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadDispatchContext();
  }, [loadDispatchContext]);

  const totalCapacity = couriers.reduce((sum, c) => sum + (c.maxDailyCapacity ?? 0), 0);

  return (
    <div className="space-y-5">
      {/* Header matching PR #36 */}
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <nav aria-label="Breadcrumb" className="text-xs text-slate-500">
            <Link href="/admin" className="hover:text-slate-800">
              Operations
            </Link>
            <span className="mx-1.5 text-slate-300">/</span>
            <span className="text-slate-700">Dispatch</span>
          </nav>
          <h1 className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">
            Dispatch &amp; workload board
          </h1>
          <p className="mt-1 text-sm text-slate-600">
            M5 Dispatch &middot; M6 Real-time Socket.IO feed &middot; M1 Assignable courier directory
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Link href="/admin/couriers" className="btn-secondary py-2 text-xs">
            Configure couriers
          </Link>
          <button type="button" onClick={loadDispatchContext} className="btn-secondary py-2 text-xs">
            Refresh board
          </button>
        </div>
      </header>

      {/* KPI Row matching PR #36 (.viz-root) */}
      <section aria-label="Dispatch summary" className="viz-root grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-6">
        <div className="relative col-span-2 overflow-hidden rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
          <div className="pointer-events-none absolute -right-10 -top-10 h-36 w-36 rounded-full bg-brand-50" aria-hidden="true" />
          <p className="relative text-sm font-medium text-slate-600">Assignable fleet capacity</p>
          <p className="relative mt-1 text-4xl font-semibold tracking-tight text-slate-900 sm:text-5xl">
            {totalCapacity}
          </p>
          <p className="relative mt-1 text-xs text-slate-500">
            Daily parcel slots across {couriers.length} assignable couriers
          </p>
          <div className="relative mt-4 h-2.5 overflow-hidden rounded-full bg-[var(--viz-track)]">
            <div
              className="viz-grow h-full rounded-full bg-[var(--viz-delivered)]"
              style={{ width: `${couriers.length > 0 ? 100 : 15}%` }}
            />
          </div>
        </div>

        <div className="flex min-w-0 flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-delivered)]" />
            Ready couriers
          </p>
          <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">{couriers.length}</p>
          <p className="mt-2 text-xs text-slate-500">Active with vehicle assigned</p>
        </div>

        <div className="flex min-w-0 flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-created)]" />
            Service zones
          </p>
          <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">
            {zones.length || 9}
          </p>
          <p className="mt-2 text-xs text-slate-500">Province hubs online</p>
        </div>

        <div className="flex min-w-0 flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-cancelled)]" />
            Live socket events
          </p>
          <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">{liveFeed.length}</p>
          <p className="mt-2 text-xs text-slate-500">Real-time parcel transitions</p>
        </div>

        <div className="flex min-w-0 flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-failed)]" />
            Zone focus
          </p>
          <p className="mt-1 text-xl font-semibold tracking-tight text-slate-900">
            {selectedZone === "ALL" ? "All zones" : selectedZone}
          </p>
          <Link href="/admin/reports" className="mt-2 text-xs font-medium text-brand-700 hover:underline">
            Zone delivery reports &rarr;
          </Link>
        </div>
      </section>

      {/* Zone Filter Bar */}
      <div className="flex flex-wrap items-center gap-2">
        <div role="group" aria-label="Zone filter" className="inline-flex flex-wrap rounded-xl bg-white p-1 shadow-sm ring-1 ring-slate-200">
          <button
            type="button"
            onClick={() => setSelectedZone("ALL")}
            className={`rounded-lg px-3 py-1.5 text-xs font-medium transition sm:text-sm ${
              selectedZone === "ALL" ? "bg-brand-600 text-white shadow-sm" : "text-slate-600 hover:bg-slate-100"
            }`}
          >
            All zones
          </button>
          {zones.map((z) => (
            <button
              key={z.code}
              type="button"
              onClick={() => setSelectedZone(z.code)}
              className={`rounded-lg px-3 py-1.5 text-xs font-medium transition sm:text-sm ${
                selectedZone === z.code ? "bg-brand-600 text-white shadow-sm" : "text-slate-600 hover:bg-slate-100"
              }`}
            >
              {z.code}
            </button>
          ))}
        </div>
      </div>

      {/* Main Bento Grid: Courier Workload Table + Live Dispatch Feed */}
      <div className="grid gap-5 lg:grid-cols-3">
        <section className="overflow-hidden rounded-2xl bg-white shadow-sm ring-1 ring-slate-200 lg:col-span-2">
          <div className="flex items-center justify-between border-b border-slate-100 px-5 py-4">
            <div>
              <h2 className="font-semibold text-slate-900">Assignable courier fleet &amp; workload</h2>
              <p className="text-xs text-slate-500">Couriers ready to receive run-sheet assignments</p>
            </div>
            <Link href="/admin/couriers" className="text-xs font-medium text-brand-700 hover:underline">
              Edit capacities &rarr;
            </Link>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-slate-600">
              <thead className="border-b border-slate-200 bg-slate-50/80 text-xs font-semibold uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-5 py-3">Courier</th>
                  <th className="px-4 py-3">Vehicle</th>
                  <th className="px-4 py-3">Daily capacity</th>
                  <th className="px-5 py-3 text-right">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {loading ? (
                  <tr>
                    <td colSpan={4} className="px-5 py-10 text-center text-slate-500">
                      Loading assignable couriers...
                    </td>
                  </tr>
                ) : couriers.length === 0 ? (
                  <tr>
                    <td colSpan={4} className="px-5 py-10 text-center text-slate-500">
                      No assignable couriers found. Configure courier vehicles and daily capacities in the Courier Directory.
                    </td>
                  </tr>
                ) : (
                  couriers.map((c) => (
                    <tr key={c.userId} className="hover:bg-slate-50/70">
                      <td className="px-5 py-3">
                        <p className="font-medium text-slate-900">{c.fullName}</p>
                        <p className="text-xs text-slate-500">{c.email}</p>
                      </td>
                      <td className="px-4 py-3">
                        <span className="status-pill-neutral">{c.vehicleType ?? "BIKE"}</span>
                      </td>
                      <td className="px-4 py-3 font-semibold tabular-nums text-slate-900">
                        {c.maxDailyCapacity ?? 20} parcels / day
                      </td>
                      <td className="px-5 py-3 text-right">
                        <span className="status-pill-success">Ready for dispatch</span>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </section>

        {/* Live Dispatch Socket Stream (M6) */}
        <section className="flex flex-col rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="font-semibold text-slate-900">Live dispatch feed</h2>
              <p className="text-xs text-slate-500">Socket.IO room: dispatch:unassigned</p>
            </div>
            <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-2.5 py-0.5 text-xs font-medium text-emerald-700">
              <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
              Live
            </span>
          </div>

          {liveFeed.length === 0 ? (
            <div className="my-auto py-12 text-center">
              <p className="text-sm font-medium text-slate-700">Listening for parcel events</p>
              <p className="mt-1 text-xs text-slate-500">
                New bookings, assignments, and courier status transitions appear here in real time.
              </p>
            </div>
          ) : (
            <ul className="mt-4 space-y-3 overflow-y-auto">
              {liveFeed.map((item, idx) => {
                const latest = item.events.at(-1);
                return (
                  <li key={`${item.trackingNumber}-${idx}`} className="rounded-xl bg-slate-50 p-3 text-xs ring-1 ring-slate-200/70">
                    <div className="flex items-center justify-between">
                      <Link
                        href={`/track/${item.trackingNumber}`}
                        className="font-mono font-semibold text-brand-700 hover:underline"
                      >
                        {item.trackingNumber}
                      </Link>
                      <span className="status-pill-neutral">{item.currentStatus ?? "CREATED"}</span>
                    </div>
                    <p className="mt-1 text-slate-600">
                      {[item.from, item.to].filter(Boolean).join(" -> ") || latest?.city || "Updated live"}
                    </p>
                  </li>
                );
              })}
            </ul>
          )}
        </section>
      </div>
    </div>
  );
}
