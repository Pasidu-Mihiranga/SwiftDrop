"use client";

import { useEffect, useState, useCallback } from "react";
import Link from "next/link";

interface SmsRecord {
  _id: string;
  recipientPhoneMasked: string;
  body: string;
  eventType: string;
  trackingNumber?: string;
  status: "SENT" | "PENDING" | "FAILED";
  sentAt: string;
}

export default function AdminSmsInboxPage() {
  const [messages, setMessages] = useState<SmsRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [autoRefresh, setAutoRefresh] = useState(true);

  const fetchMessages = useCallback(async () => {
    try {
      const res = await fetch("/api/bff/api/v1/admin/notifications/sms-inbox");
      if (res.ok) {
        const raw = await res.json();
        const list: SmsRecord[] = Array.isArray(raw) ? raw : (raw.messages ?? []);
        setMessages(list);
        setError(null);
      } else {
        setError(`Failed to fetch SMS inbox (HTTP ${res.status})`);
      }
    } catch {
      setError("Network error fetching SMS inbox.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchMessages();
  }, [fetchMessages]);

  useEffect(() => {
    if (!autoRefresh) return;
    const interval = setInterval(fetchMessages, 5000);
    return () => clearInterval(interval);
  }, [autoRefresh, fetchMessages]);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-brand-700">
            <Link href="/admin" className="hover:underline">Operations</Link>
            <span>/</span>
            <span>Notifications</span>
          </div>
          <h1 className="mt-1 text-2xl font-bold tracking-tight text-slate-900">
            Mock SMS Outbox &amp; Delivery Log
          </h1>
          <p className="mt-1 text-sm text-slate-600">
            Inspect mock SMS messages dispatched by the Notification Service with PII phone masking.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <label className="flex items-center gap-2 text-xs font-medium text-slate-600 bg-white border border-slate-200 px-3 py-2 rounded-lg cursor-pointer shadow-2xs">
            <input
              type="checkbox"
              checked={autoRefresh}
              onChange={(e) => setAutoRefresh(e.target.checked)}
              className="h-3.5 w-3.5 rounded text-brand-600 focus:ring-brand-500"
            />
            Auto-refresh (5s)
          </label>
          <button
            type="button"
            onClick={() => {
              setLoading(true);
              fetchMessages();
            }}
            className="btn-secondary text-xs"
          >
            Refresh Now
          </button>
        </div>
      </div>

      {error && (
        <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800 font-medium">
          {error}
        </div>
      )}

      <div className="card overflow-hidden p-0">
        <div className="overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-left text-sm">
            <thead className="bg-slate-50 text-xs font-semibold uppercase text-slate-500">
              <tr>
                <th className="px-4 py-3">Sent Time</th>
                <th className="px-4 py-3">Recipient Phone</th>
                <th className="px-4 py-3">Event Type</th>
                <th className="px-4 py-3">Tracking No.</th>
                <th className="px-4 py-3">Message Content</th>
                <th className="px-4 py-3">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 bg-white">
              {loading && messages.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-4 py-8 text-center text-slate-500">
                    Loading SMS records...
                  </td>
                </tr>
              ) : messages.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-4 py-8 text-center text-slate-500">
                    No mock SMS messages dispatched yet. Trigger an event to see messages here.
                  </td>
                </tr>
              ) : (
                messages.map((sms) => (
                  <tr key={sms._id} className="hover:bg-slate-50/70 transition-colors">
                    <td className="px-4 py-3 font-mono text-xs text-slate-600 whitespace-nowrap">
                      {new Date(sms.sentAt).toLocaleTimeString(undefined, {
                        hour: "2-digit",
                        minute: "2-digit",
                        second: "2-digit",
                      })}
                      <span className="block text-[10px] text-slate-400">
                        {new Date(sms.sentAt).toLocaleDateString()}
                      </span>
                    </td>
                    <td className="px-4 py-3 font-mono text-xs font-semibold text-slate-800 whitespace-nowrap">
                      {sms.recipientPhoneMasked}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      <span className="inline-block rounded-md bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-700">
                        {sms.eventType}
                      </span>
                    </td>
                    <td className="px-4 py-3 font-mono text-xs text-brand-700 whitespace-nowrap">
                      {sms.trackingNumber ? (
                        <Link href={`/track?trackingNumber=${sms.trackingNumber}`} className="hover:underline">
                          {sms.trackingNumber}
                        </Link>
                      ) : (
                        <span className="text-slate-400">—</span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-xs text-slate-700 max-w-md whitespace-pre-wrap">
                      {sms.body}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-700 border border-emerald-200">
                        <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
                        {sms.status}
                      </span>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
