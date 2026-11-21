"use client";

import { useEffect, useState } from "react";
import Link from "next/link";

interface Preferences {
  email: boolean;
  sms: boolean;
  events: string[];
}

const AVAILABLE_EVENTS = [
  { id: "CREATED", label: "Booking Confirmation", description: "When a new delivery order is placed and confirmed." },
  { id: "PICKED_UP", label: "Parcel Picked Up", description: "When our courier collects your parcel." },
  { id: "IN_TRANSIT", label: "In Transit / Hub Scans", description: "When your parcel moves through sorting hubs." },
  { id: "OUT_FOR_DELIVERY", label: "Out for Delivery", description: "When your parcel is loaded for delivery today." },
  { id: "DELIVERED", label: "Delivered", description: "When your parcel has been successfully delivered." },
  { id: "FAILED", label: "Delivery Exception / Failed", description: "When an issue or failed delivery attempt occurs." },
];

export default function NotificationPreferencesPage() {
  const [preferences, setPreferences] = useState<Preferences>({
    email: true,
    sms: true,
    events: ["CREATED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED", "FAILED"],
  });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{ type: "success" | "error"; text: string } | null>(null);

  useEffect(() => {
    async function load() {
      try {
        const res = await fetch("/api/bff/api/v1/notifications/preferences");
        if (res.ok) {
          const data = (await res.json()) as Preferences;
          const rawEvents = data.events ?? ["CREATED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED", "FAILED"];
          // Normalize PARCEL_CREATED to CREATED if legacy key is present
          const normalizedEvents = rawEvents.map((e) => (e === "PARCEL_CREATED" ? "CREATED" : e));
          setPreferences({
            email: data.email ?? true,
            sms: data.sms ?? true,
            events: normalizedEvents,
          });
        }
      } catch {
        setMessage({ type: "error", text: "Failed to load preferences from server." });
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  const handleToggleEvent = (eventId: string) => {
    setPreferences((prev) => {
      const exists = prev.events.includes(eventId);
      return {
        ...prev,
        events: exists ? prev.events.filter((e) => e !== eventId) : [...prev.events, eventId],
      };
    });
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setMessage(null);

    try {
      const res = await fetch("/api/bff/api/v1/notifications/preferences", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(preferences),
      });

      if (res.ok) {
        setMessage({ type: "success", text: "Your notification preferences have been updated." });
      } else {
        setMessage({ type: "error", text: "Could not save preferences. Please try again." });
      }
    } catch {
      setMessage({ type: "error", text: "An unexpected network error occurred." });
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="mx-auto max-w-3xl space-y-4 py-8">
        <div className="h-6 w-48 animate-pulse rounded bg-slate-200" />
        <div className="h-64 animate-pulse rounded-2xl bg-slate-100" />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-brand-700">
            <Link href="/customer" className="hover:underline">Dashboard</Link>
            <span>/</span>
            <span>Notifications</span>
          </div>
          <h1 className="mt-1 text-2xl font-bold tracking-tight text-slate-900">
            Notification Preferences
          </h1>
          <p className="mt-1 text-sm text-slate-600">
            Control which delivery notifications you receive and how we reach you.
          </p>
        </div>
      </div>

      {message && (
        <div
          className={`rounded-xl border p-4 text-sm font-medium ${
            message.type === "success"
              ? "border-emerald-200 bg-emerald-50 text-emerald-800"
              : "border-red-200 bg-red-50 text-red-800"
          }`}
        >
          {message.text}
        </div>
      )}

      <form onSubmit={handleSave} className="space-y-6">
        {/* Channel Selection */}
        <section className="card space-y-4">
          <div>
            <h2 className="text-base font-semibold text-slate-900">Delivery Channels</h2>
            <p className="text-xs text-slate-500">Choose the communication channels you wish to use.</p>
          </div>

          <div className="grid gap-3 sm:grid-cols-2">
            <label className="flex items-center justify-between p-3.5 rounded-xl border border-slate-200 hover:border-slate-300 cursor-pointer transition-colors">
              <div className="space-y-0.5">
                <span className="text-sm font-medium text-slate-800">Email Notifications</span>
                <p className="text-xs text-slate-500">Sent to your registered email address</p>
              </div>
              <input
                type="checkbox"
                checked={preferences.email}
                onChange={(e) => setPreferences({ ...preferences, email: e.target.checked })}
                className="h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
              />
            </label>

            <label className="flex items-center justify-between p-3.5 rounded-xl border border-slate-200 hover:border-slate-300 cursor-pointer transition-colors">
              <div className="space-y-0.5">
                <span className="text-sm font-medium text-slate-800">SMS Notifications</span>
                <p className="text-xs text-slate-500">Sent as SMS to your mobile phone</p>
              </div>
              <input
                type="checkbox"
                checked={preferences.sms}
                onChange={(e) => setPreferences({ ...preferences, sms: e.target.checked })}
                className="h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
              />
            </label>
          </div>
        </section>

        {/* Event Subscriptions */}
        <section className="card space-y-4">
          <div>
            <h2 className="text-base font-semibold text-slate-900">Delivery Events</h2>
            <p className="text-xs text-slate-500">Select which lifecycle updates you want to be alerted on.</p>
          </div>

          <div className="divide-y divide-slate-100">
            {AVAILABLE_EVENTS.map((event) => (
              <label
                key={event.id}
                className="flex items-start justify-between py-3 cursor-pointer hover:bg-slate-50/50 rounded-lg px-2 -mx-2 transition-colors"
              >
                <div className="pr-4">
                  <span className="text-sm font-medium text-slate-800">{event.label}</span>
                  <p className="text-xs text-slate-500 mt-0.5">{event.description}</p>
                </div>
                <input
                  type="checkbox"
                  checked={preferences.events.includes(event.id)}
                  onChange={() => handleToggleEvent(event.id)}
                  className="mt-1 h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
                />
              </label>
            ))}
          </div>
        </section>

        <div className="flex items-center justify-end gap-3 pt-2">
          <Link href="/customer" className="btn-secondary text-sm">
            Cancel
          </Link>
          <button type="submit" disabled={saving} className="btn-primary text-sm">
            {saving ? "Saving..." : "Save Preferences"}
          </button>
        </div>
      </form>
    </div>
  );
}
