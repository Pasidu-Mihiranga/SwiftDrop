"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";

interface CourierRecord {
  userId: string;
  fullName: string;
  email: string;
  phone: string | null;
  vehicleType: "BIKE" | "THREE_WHEELER" | "VAN" | "TRUCK" | null;
  homeZoneId: string | null;
  maxDailyCapacity: number | null;
  active: boolean;
  assignable: boolean;
}

interface ZoneOption {
  id?: string;
  code: string;
  name: string;
}

const VEHICLE_LABELS: Record<string, string> = {
  BIKE: "Motorbike",
  THREE_WHEELER: "Three-wheeler",
  VAN: "Delivery van",
  TRUCK: "Light truck",
};

export default function AdminCouriersPage() {
  const [couriers, setCouriers] = useState<CourierRecord[]>([]);
  const [zones, setZones] = useState<ZoneOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<"ALL" | "ASSIGNABLE" | "BIKE" | "THREE_WHEELER" | "VAN">("ALL");
  const [search, setSearch] = useState("");

  // Edit modal state
  const [editing, setEditing] = useState<CourierRecord | null>(null);
  const [vehicleType, setVehicleType] = useState<"BIKE" | "THREE_WHEELER" | "VAN" | "TRUCK">("BIKE");
  const [homeZoneId, setHomeZoneId] = useState("");
  const [maxDailyCapacity, setMaxDailyCapacity] = useState("25");
  const [active, setActive] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saveMessage, setSaveMessage] = useState<{ type: "success" | "error"; text: string } | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [courierRes, zoneRes] = await Promise.all([
        fetch("/api/bff/api/v1/admin/couriers"),
        fetch("/api/bff/api/v1/pricing/zones"),
      ]);
      if (courierRes.ok) {
        const list = (await courierRes.json()) as CourierRecord[];
        setCouriers(Array.isArray(list) ? list : []);
      } else {
        setError(`Could not load courier directory (HTTP ${courierRes.status})`);
      }
      if (zoneRes.ok) {
        const zList = (await zoneRes.json()) as ZoneOption[];
        setZones(Array.isArray(zList) ? zList : []);
      }
    } catch {
      setError("User service is unreachable. Ensure user-service is running.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  function openEditor(c: CourierRecord) {
    setEditing(c);
    setVehicleType(c.vehicleType ?? "BIKE");
    setHomeZoneId(c.homeZoneId ?? "");
    setMaxDailyCapacity(String(c.maxDailyCapacity ?? 25));
    setActive(c.active);
    setSaveMessage(null);
  }

  async function handleSaveProfile(e: React.FormEvent) {
    e.preventDefault();
    if (!editing) return;
    setSaving(true);
    setSaveMessage(null);
    try {
      const res = await fetch(`/api/bff/api/v1/admin/couriers/${editing.userId}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          vehicleType,
          homeZoneId: homeZoneId.trim() || null,
          maxDailyCapacity: parseInt(maxDailyCapacity, 10) || 20,
          active,
        }),
      });
      if (res.ok) {
        const updated = (await res.json()) as CourierRecord;
        setCouriers((prev) => prev.map((c) => (c.userId === updated.userId ? updated : c)));
        setEditing(null);
        setSaveMessage({ type: "success", text: `Updated courier profile for ${updated.fullName}.` });
      } else {
        const problem = await res.json().catch(() => ({}));
        setSaveMessage({
          type: "error",
          text: problem.detail || `Could not save courier profile (HTTP ${res.status})`,
        });
      }
    } catch {
      setSaveMessage({ type: "error", text: "Network error while updating courier profile." });
    } finally {
      setSaving(false);
    }
  }

  const assignableCount = couriers.filter((c) => c.assignable).length;
  const totalCapacity = couriers.reduce((sum, c) => sum + (c.active ? c.maxDailyCapacity ?? 0 : 0), 0);
  const bikeCount = couriers.filter((c) => c.vehicleType === "BIKE" || c.vehicleType === "THREE_WHEELER").length;
  const vanCount = couriers.filter((c) => c.vehicleType === "VAN" || c.vehicleType === "TRUCK").length;
  const assignableRate = couriers.length > 0 ? Math.round((assignableCount / couriers.length) * 1000) / 10 : 0;

  const filtered = couriers.filter((c) => {
    if (filter === "ASSIGNABLE" && !c.assignable) return false;
    if (filter === "BIKE" && c.vehicleType !== "BIKE") return false;
    if (filter === "THREE_WHEELER" && c.vehicleType !== "THREE_WHEELER") return false;
    if (filter === "VAN" && c.vehicleType !== "VAN" && c.vehicleType !== "TRUCK") return false;
    if (search.trim()) {
      const q = search.trim().toLowerCase();
      return (
        c.fullName.toLowerCase().includes(q) ||
        c.email.toLowerCase().includes(q) ||
        c.userId.toLowerCase().includes(q)
      );
    }
    return true;
  });

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
            <span className="text-slate-700">Couriers</span>
          </nav>
          <h1 className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">Courier directory</h1>
          <p className="mt-1 text-sm text-slate-600">
            M1 User Service &middot; Fleet vehicles, home zones &amp; daily dispatch capacities
          </p>
        </div>
        <button type="button" onClick={loadData} disabled={loading} className="btn-secondary py-2 text-xs">
          {loading ? "Refreshing..." : "Refresh fleet"}
        </button>
      </header>

      {/* KPI Row matching PR #36 (.viz-root) */}
      <section aria-label="Fleet summary" className="viz-root grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-6">
        <div className="relative col-span-2 overflow-hidden rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
          <div className="pointer-events-none absolute -right-10 -top-10 h-36 w-36 rounded-full bg-brand-50" aria-hidden="true" />
          <p className="relative text-sm font-medium text-slate-600">Assignable readiness</p>
          <p className="relative mt-1 text-4xl font-semibold tracking-tight text-slate-900 sm:text-5xl">
            {couriers.length > 0 ? `${assignableRate}%` : "0%"}
          </p>
          <p className="relative mt-1 text-xs text-slate-500">
            {assignableCount} of {couriers.length} couriers configured and ready for dispatch
          </p>
          <div className="relative mt-4 h-2.5 overflow-hidden rounded-full bg-[var(--viz-track)]">
            <div className="viz-grow h-full rounded-full bg-[var(--viz-delivered)]" style={{ width: `${assignableRate}%` }} />
          </div>
        </div>

        <div className="flex min-w-0 flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-delivered)]" />
            Total couriers
          </p>
          <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">{couriers.length}</p>
          <p className="mt-2 text-xs text-slate-500">{assignableCount} assignable today</p>
        </div>

        <div className="flex min-w-0 flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-created)]" />
            Daily capacity
          </p>
          <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">{totalCapacity}</p>
          <p className="mt-2 text-xs text-slate-500">Max parcels per day</p>
        </div>

        <div className="flex min-w-0 flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-cancelled)]" />
            Light fleet
          </p>
          <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">{bikeCount}</p>
          <p className="mt-2 text-xs text-slate-500">Motorbikes &amp; three-wheelers</p>
        </div>

        <div className="flex min-w-0 flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-failed)]" />
            Heavy fleet
          </p>
          <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">{vanCount}</p>
          <p className="mt-2 text-xs text-slate-500">Delivery vans &amp; trucks</p>
        </div>
      </section>

      {/* Filter Bar */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div role="group" aria-label="Fleet filter" className="inline-flex flex-wrap rounded-xl bg-white p-1 shadow-sm ring-1 ring-slate-200">
          {(
            [
              ["ALL", "All couriers"],
              ["ASSIGNABLE", "Assignable only"],
              ["BIKE", "Motorbikes"],
              ["THREE_WHEELER", "Three-wheelers"],
              ["VAN", "Vans & trucks"],
            ] as const
          ).map(([key, label]) => (
            <button
              key={key}
              type="button"
              onClick={() => setFilter(key)}
              className={`rounded-lg px-3 py-1.5 text-xs font-medium transition sm:text-sm ${
                filter === key ? "bg-brand-600 text-white shadow-sm" : "text-slate-600 hover:bg-slate-100 hover:text-slate-900"
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        <input
          type="search"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Find courier by name or email..."
          className="rounded-xl border border-slate-200 bg-white px-3 py-1.5 text-sm shadow-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-100"
        />
      </div>

      {saveMessage && (
        <div
          role="status"
          className={`rounded-xl px-4 py-3 text-sm ring-1 ${
            saveMessage.type === "success"
              ? "bg-emerald-50 text-emerald-800 ring-emerald-200"
              : "bg-red-50 text-red-800 ring-red-200"
          }`}
        >
          {saveMessage.text}
        </div>
      )}

      {error && (
        <div role="status" className="rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-800 ring-1 ring-amber-200">
          {error}
        </div>
      )}

      {/* Courier Table */}
      <section className="overflow-hidden rounded-2xl bg-white shadow-sm ring-1 ring-slate-200">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-slate-600">
            <thead className="border-b border-slate-200 bg-slate-50/80 text-xs font-semibold uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-5 py-3">Courier</th>
                <th className="px-4 py-3">Contact</th>
                <th className="px-4 py-3">Vehicle</th>
                <th className="px-4 py-3">Home zone</th>
                <th className="px-4 py-3">Daily capacity</th>
                <th className="px-4 py-3">Dispatch status</th>
                <th className="px-5 py-3 text-right">Manage</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? (
                <tr>
                  <td colSpan={7} className="px-5 py-12 text-center text-slate-500">
                    Loading courier directory...
                  </td>
                </tr>
              ) : filtered.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-5 py-12 text-center text-slate-500">
                    No couriers match the current filter.
                  </td>
                </tr>
              ) : (
                filtered.map((c) => (
                  <tr key={c.userId} className="transition-colors hover:bg-slate-50/70">
                    <td className="px-5 py-3.5">
                      <p className="font-semibold text-slate-900">{c.fullName}</p>
                      <p className="font-mono text-xs text-slate-400">{c.userId}</p>
                    </td>
                    <td className="px-4 py-3.5 text-xs">
                      <p className="text-slate-800">{c.email}</p>
                      <p className="text-slate-500">{c.phone ?? "No phone"}</p>
                    </td>
                    <td className="px-4 py-3.5">
                      {c.vehicleType ? (
                        <span className="status-pill-neutral">{VEHICLE_LABELS[c.vehicleType] ?? c.vehicleType}</span>
                      ) : (
                        <span className="text-xs text-slate-400">Unassigned</span>
                      )}
                    </td>
                    <td className="px-4 py-3.5 font-mono text-xs text-slate-700">
                      {c.homeZoneId ? c.homeZoneId.slice(0, 8) : "Any zone"}
                    </td>
                    <td className="px-4 py-3.5 font-semibold tabular-nums text-slate-900">
                      {c.maxDailyCapacity ? `${c.maxDailyCapacity} / day` : "Not set"}
                    </td>
                    <td className="px-4 py-3.5">
                      <span className={c.assignable ? "status-pill-success" : "status-pill-warning"}>
                        {c.assignable ? "Assignable" : c.active ? "Needs vehicle/capacity" : "Inactive"}
                      </span>
                    </td>
                    <td className="px-5 py-3.5 text-right">
                      <button
                        type="button"
                        onClick={() => openEditor(c)}
                        className="rounded-lg px-3 py-1 text-xs font-medium text-brand-700 ring-1 ring-slate-200 hover:bg-slate-50"
                      >
                        Configure
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>

      {/* Edit Courier Profile Modal */}
      {editing && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
          <form
            onSubmit={handleSaveProfile}
            className="w-full max-w-md space-y-4 rounded-2xl bg-white p-6 shadow-xl ring-1 ring-slate-200"
          >
            <div>
              <h2 className="text-lg font-semibold text-slate-900">Configure courier profile</h2>
              <p className="text-xs text-slate-500">
                {editing.fullName} &middot; {editing.email}
              </p>
            </div>

            <div className="space-y-3">
              <div>
                <label htmlFor="edit-vehicle" className="block text-xs font-medium text-slate-700">
                  Vehicle type
                </label>
                <select
                  id="edit-vehicle"
                  value={vehicleType}
                  onChange={(e) => setVehicleType(e.target.value as "BIKE" | "THREE_WHEELER" | "VAN" | "TRUCK")}
                  className="input mt-1 bg-white text-sm"
                >
                  <option value="BIKE">Motorbike (BIKE)</option>
                  <option value="THREE_WHEELER">Three-wheeler (THREE_WHEELER)</option>
                  <option value="VAN">Delivery van (VAN)</option>
                  <option value="TRUCK">Light truck (TRUCK)</option>
                </select>
              </div>

              <div>
                <label htmlFor="edit-capacity" className="block text-xs font-medium text-slate-700">
                  Max daily capacity (1–200 parcels)
                </label>
                <input
                  id="edit-capacity"
                  type="number"
                  min={1}
                  max={200}
                  required
                  value={maxDailyCapacity}
                  onChange={(e) => setMaxDailyCapacity(e.target.value)}
                  className="input mt-1 text-sm"
                />
              </div>

              <div>
                <label htmlFor="edit-zone" className="block text-xs font-medium text-slate-700">
                  Home zone UUID (optional)
                </label>
                <input
                  id="edit-zone"
                  type="text"
                  value={homeZoneId}
                  onChange={(e) => setHomeZoneId(e.target.value)}
                  placeholder="Leave blank for any zone"
                  className="input mt-1 font-mono text-xs"
                />
                {zones.length > 0 && (
                  <p className="mt-1 text-[11px] text-slate-500">
                    Active zones: {zones.map((z) => z.code).join(", ")}
                  </p>
                )}
              </div>

              <label className="flex items-center gap-2 pt-1 text-sm text-slate-800">
                <input
                  type="checkbox"
                  checked={active}
                  onChange={(e) => setActive(e.target.checked)}
                  className="h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
                />
                Active for daily dispatch runs
              </label>
            </div>

            <div className="flex items-center justify-end gap-2 border-t border-slate-100 pt-4">
              <button type="button" onClick={() => setEditing(null)} className="btn-secondary py-1.5 text-xs">
                Cancel
              </button>
              <button type="submit" disabled={saving} className="btn-primary py-1.5 text-xs">
                {saving ? "Saving..." : "Save profile"}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
