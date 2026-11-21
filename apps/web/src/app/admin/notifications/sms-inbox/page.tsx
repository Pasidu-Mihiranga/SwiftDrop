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

function getEventBadge(eventType: string, body = "") {
  if (eventType === "ParcelCreated") {
    return {
      label: "Booking Confirmed",
      className: "bg-blue-50 text-blue-700 border-blue-200",
    };
  }
  if (eventType === "ParcelAssigned") {
    return {
      label: "Courier Assigned",
      className: "bg-purple-50 text-purple-700 border-purple-200",
    };
  }
  if (eventType === "MerchantLifecycleChanged") {
    return {
      label: "Merchant Update",
      className: "bg-slate-100 text-slate-700 border-slate-200",
    };
  }
  if (eventType === "ParcelStatusChanged") {
    const lower = body.toLowerCase();
    if (lower.includes("delivered")) {
      return {
        label: "Delivered",
        className: "bg-emerald-50 text-emerald-700 border-emerald-200",
      };
    }
    if (lower.includes("out for delivery")) {
      return {
        label: "Out for Delivery",
        className: "bg-amber-50 text-amber-700 border-amber-200",
      };
    }
    if (lower.includes("picked up")) {
      return {
        label: "Picked Up",
        className: "bg-indigo-50 text-indigo-700 border-indigo-200",
      };
    }
    if (lower.includes("transit")) {
      return {
        label: "In Transit",
        className: "bg-indigo-50 text-indigo-700 border-indigo-200",
      };
    }
    if (lower.includes("cancelled") || lower.includes("canceled")) {
      return {
        label: "Cancelled",
        className: "bg-rose-50 text-rose-700 border-rose-200",
      };
    }
    if (lower.includes("failed") || lower.includes("unsuccessful")) {
      return {
        label: "Delivery Failed",
        className: "bg-rose-50 text-rose-700 border-rose-200",
      };
    }
    return {
      label: "Status Update",
      className: "bg-blue-50 text-blue-700 border-blue-200",
    };
  }
  return {
    label: "Notification",
    className: "bg-slate-100 text-slate-700 border-slate-200",
  };
}

/** Format SMS message body to be completely customer-friendly with no developer localhost URLs or raw casing */
function formatMessageForCustomer(body: string): string {
  if (!body) return "";
  let clean = body;

  // Replace developer localhost URLs with user-friendly web links
  clean = clean.replace(/http:\/\/localhost:\d+\/track\/?/g, "https://swiftdrop.lk/track/");
  clean = clean.replace(/http:\/\/localhost:\d+\//g, "https://swiftdrop.lk/");

  // Remove robotic "Note: Received by:" prefixes -> "Received by"
  clean = clean.replace(/Note:\s*Received by:\s*/gi, "Received by: ");
  clean = clean.replace(/Note:\s*Delivery failed:\s*/gi, "Reason: ");

  // Fix all-caps destination city (e.g. "Destination: DEKATANA." -> "Destination: Dekatana.")
  clean = clean.replace(/Destination:\s*([A-Z]{3,})\b/g, (_, city: string) => {
    return `Destination: ${city.charAt(0).toUpperCase()}${city.slice(1).toLowerCase()}`;
  });

  return clean;
}

/** Render message text with unbroken, clear, and well-defined clickable tracking links */
function renderMessageWithLinks(body: string) {
  const formatted = formatMessageForCustomer(body);
  if (!formatted) return null;

  // Match URLs like https://swiftdrop.lk/track/SD... or http://...
  const urlRegex = /(https?:\/\/[^\s]+)/g;
  const parts = formatted.split(urlRegex);

  return (
    <>
      {parts.map((part, index) => {
        if (part.match(urlRegex)) {
          // Strip any trailing punctuation like period or comma from URL
          const punctMatch = part.match(/^(https?:\/\/[^\s]+?)([.,!?:;]?)$/);
          const rawUrl = punctMatch ? punctMatch[1] : part;
          const trailingPunct = punctMatch ? punctMatch[2] : "";

          // If it is a SwiftDrop tracking URL, route to the app's track page
          const trackMatch = rawUrl.match(/\/track\/([A-Za-z0-9]+)/);
          const href = trackMatch ? `/track/${trackMatch[1]}` : rawUrl;

          return (
            <span key={index} className="inline-block">
              <Link
                href={href}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-block whitespace-nowrap font-medium text-brand-600 underline hover:text-brand-800 transition-colors"
              >
                {rawUrl}
              </Link>
              {trailingPunct}
            </span>
          );
        }
        return <span key={index}>{part}</span>;
      })}
    </>
  );
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
        setError(`Failed to fetch SMS records (HTTP ${res.status})`);
      }
    } catch {
      setError("Network error fetching SMS notifications.");
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
            SMS Delivery Notifications
          </h1>
          <p className="mt-1 text-sm text-slate-600">
            Review customer text messages, tracking links, and delivery status updates dispatched to recipients.
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
                <th className="px-4 py-3">Notification Alert</th>
                <th className="px-4 py-3">Tracking No.</th>
                <th className="px-4 py-3 min-w-[380px]">SMS Message Content</th>
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
                    No SMS messages dispatched yet. Booking and delivery status updates will appear here.
                  </td>
                </tr>
              ) : (
                messages.map((sms) => {
                  const badge = getEventBadge(sms.eventType, sms.body);
                  return (
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
                        <span
                          className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold ${badge.className}`}
                        >
                          {badge.label}
                        </span>
                      </td>
                      <td className="px-4 py-3 font-mono text-xs text-brand-700 whitespace-nowrap">
                        {sms.trackingNumber ? (
                          <Link href={`/track/${sms.trackingNumber}`} target="_blank" rel="noopener noreferrer" className="hover:underline font-bold">
                            {sms.trackingNumber}
                          </Link>
                        ) : (
                          <span className="text-slate-400">—</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-xs text-slate-700 min-w-[380px] max-w-2xl leading-relaxed">
                        {renderMessageWithLinks(sms.body)}
                      </td>
                      <td className="px-4 py-3 whitespace-nowrap">
                        <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-700 border border-emerald-200">
                          <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
                          {sms.status}
                        </span>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
