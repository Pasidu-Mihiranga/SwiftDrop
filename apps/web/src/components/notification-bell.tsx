"use client";

import { useEffect, useState, useRef } from "react";
import Link from "next/link";

interface InAppNotification {
  _id: string;
  title: string;
  message: string;
  type: string;
  trackingNumber?: string;
  read: boolean;
  createdAt: string;
}

/** Format notification titles to be clean, concise, and customer-facing without brand prefixes or raw tracking brackets */
function formatNotificationTitle(title: string, type?: string): string {
  if (!title) return "Notification";
  let clean = title.trim();

  // Remove "[SD...]" bracketed tracking numbers
  clean = clean.replace(/\[\s*SD[0-9A-Z]+\s*\]/gi, "").trim();

  // Remove "SwiftDrop: " or "SwiftDrop Update: " prefix
  clean = clean.replace(/^SwiftDrop(?:\s*Update)?:\s*/gi, "").trim();

  // If title was only "[SD...]", infer friendly title from event type
  if (!clean || clean.startsWith("[")) {
    if (type === "ParcelCreated") return "Booking Confirmed";
    if (type === "ParcelAssigned") return "Courier Assigned";
    if (type === "ParcelStatusChanged") return "Delivery Update";
    if (type === "MerchantLifecycleChanged") return "Merchant Update";
    return "Delivery Update";
  }

  return clean;
}

/** Format notification body into concise customer messages without developer localhost links or repetitive IDs */
function formatNotificationMessage(message: string, trackingNumber?: string): string {
  if (!message) return "";
  let clean = message.trim();

  // Remove tracking URLs from in-app notifications (the UI already provides a direct link)
  clean = clean.replace(/\s*Track(?:\s*live)?(?:\s*at)?[:\s]+https?:\/\/\S+/gi, "");
  clean = clean.replace(/https?:\/\/\S+/gi, "");

  // Remove redundant brand prefixes
  clean = clean.replace(/^SwiftDrop(?:\s*Update)?:\s*/gi, "");

  // Convert verbose booking confirmation into a clean, concise phrase
  clean = clean.replace(
    /Your delivery booking\s+[A-Z0-9]+\s+has been confirmed\.\s*Destination:\s*([A-Za-z0-9\s-]+)\.?/gi,
    (_, city) => {
      const formattedCity = city.trim().charAt(0).toUpperCase() + city.trim().slice(1).toLowerCase();
      return `Delivery booking confirmed for ${formattedCity}.`;
    },
  );

  // Shorten parcel status changes e.g. "Parcel SD... is now Delivered." -> "Delivered."
  if (trackingNumber) {
    clean = clean.replace(new RegExp(`Parcel\\s+${trackingNumber}\\s+(?:is\\s+now|has\\s+been)\\s+`, "gi"), "");
  }
  clean = clean.replace(/Parcel\s+[A-Z0-9]+\s+(?:is\\s+now|has\\s+been)\s+/gi, "");

  // Clean robotic note prefixes
  clean = clean.replace(/Note:\s*Received by:\s*/gi, "Received by: ");
  clean = clean.replace(/Note:\s*Received by\s*/gi, "Received by ");
  clean = clean.replace(/Note:\s*Delivery failed:\s*/gi, "Reason: ");
  clean = clean.replace(/Note:\s*/gi, "");

  // Fix ALL-CAPS city destination (e.g. "Destination: DEKATANA." -> "Destination: Dekatana.")
  clean = clean.replace(/Destination:\s*([A-Z]{3,})\b/g, (_, city: string) => {
    return `Destination: ${city.charAt(0).toUpperCase()}${city.slice(1).toLowerCase()}`;
  });

  return clean.trim();
}

export function NotificationBell() {
  const [notifications, setNotifications] = useState<InAppNotification[]>([]);
  const [unreadCount, setUnreadCount] = useState<number>(0);
  const [isOpen, setIsOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const popoverRef = useRef<HTMLDivElement>(null);

  const fetchNotifications = async () => {
    try {
      const res = await fetch("/api/bff/api/v1/notifications/me");
      if (res.ok) {
        const raw = await res.json();
        const list: InAppNotification[] = Array.isArray(raw) ? raw : (raw.content ?? []);
        const unread = typeof raw.unreadCount === "number" ? raw.unreadCount : list.filter((n) => !n.read).length;
        setNotifications(list);
        setUnreadCount(unread);
      }
    } catch {
      // Ignore network errors in polling
    }
  };

  useEffect(() => {
    fetchNotifications();
    const interval = setInterval(fetchNotifications, 15000);
    return () => clearInterval(interval);
  }, []);

  // Close on outside click
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (popoverRef.current && !popoverRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }
    if (isOpen) {
      document.addEventListener("mousedown", handleClickOutside);
    }
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [isOpen]);

  const markAsRead = async (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      setNotifications((prev) =>
        prev.map((n) => (n._id === id ? { ...n, read: true } : n))
      );
      setUnreadCount((prev) => Math.max(0, prev - 1));
      await fetch(`/api/bff/api/v1/notifications/me/${id}/read`, {
        method: "POST",
      });
    } catch {
      // Ignore
    }
  };

  return (
    <div className="relative" ref={popoverRef}>
      <button
        type="button"
        onClick={() => setIsOpen((prev) => !prev)}
        aria-label="Notifications"
        className="relative rounded-full p-2 text-slate-600 hover:bg-slate-100 hover:text-slate-900 focus:outline-none focus:ring-2 focus:ring-brand-500"
      >
        {/* Bell SVG */}
        <svg
          xmlns="http://www.w3.org/2000/svg"
          className="h-5 w-5"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth={2}
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"
          />
        </svg>

        {unreadCount > 0 && (
          <span className="absolute top-1 right-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-600 px-1 text-[10px] font-bold text-white shadow-xs">
            {unreadCount > 9 ? "9+" : unreadCount}
          </span>
        )}
      </button>

      {isOpen && (
        <div className="absolute right-0 mt-2 w-80 sm:w-96 rounded-xl border border-slate-200 bg-white shadow-xl z-50 overflow-hidden">
          <div className="flex items-center justify-between border-b border-slate-100 bg-slate-50 px-4 py-3">
            <div className="flex items-center gap-2">
              <span className="font-semibold text-slate-800 text-sm">Notifications</span>
              {unreadCount > 0 && (
                <span className="rounded-full bg-brand-100 px-2 py-0.5 text-xs font-semibold text-brand-800">
                  {unreadCount} unread
                </span>
              )}
            </div>
            <Link
              href="/customer/notifications/preferences"
              onClick={() => setIsOpen(false)}
              className="text-xs text-brand-600 hover:text-brand-800 font-medium"
            >
              Preferences
            </Link>
          </div>

          <div className="max-h-96 overflow-y-auto divide-y divide-slate-100">
            {notifications.length === 0 ? (
              <div className="p-6 text-center text-sm text-slate-500">
                No notifications yet.
              </div>
            ) : (
              notifications.map((n) => (
                <div
                  key={n._id}
                  className={`p-4 text-xs transition-colors ${
                    n.read ? "bg-white" : "bg-brand-50/40 hover:bg-brand-50/70"
                  }`}
                >
                  <div className="flex items-start justify-between gap-2">
                    <h4 className={`font-medium ${n.read ? "text-slate-800" : "text-brand-950 font-bold"}`}>
                      {formatNotificationTitle(n.title, n.type)}
                    </h4>
                    {!n.read && (
                      <button
                        onClick={(e) => markAsRead(n._id, e)}
                        className="text-[11px] text-brand-600 hover:text-brand-800 font-medium shrink-0 ml-2"
                        title="Mark as read"
                      >
                        Mark read
                      </button>
                    )}
                  </div>
                  <p className="mt-1 text-slate-600 leading-relaxed">
                    {formatNotificationMessage(n.message, n.trackingNumber)}
                  </p>
                  <div className="mt-2 flex items-center justify-between text-[11px] text-slate-400">
                    {n.trackingNumber ? (
                      <Link
                        href={`/track/${n.trackingNumber}`}
                        onClick={() => setIsOpen(false)}
                        className="text-brand-600 hover:underline font-mono"
                      >
                        {n.trackingNumber}
                      </Link>
                    ) : (
                      <span />
                    )}
                    <span>{new Date(n.createdAt).toLocaleDateString(undefined, {
                      month: "short",
                      day: "numeric",
                      hour: "2-digit",
                      minute: "2-digit",
                    })}</span>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
}
