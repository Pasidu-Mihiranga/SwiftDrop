"use client";

import { useEffect, useState } from "react";
import { type TrackingView, STATUS_LABELS } from "@/lib/tracking";
import { followSession } from "@/lib/tracking-socket";

interface Toast {
  id: string;
  text: string;
}

const MAX_TOASTS = 3;

/** A toast text for an update, e.g. "SD261020K7Q2M9X: Out for delivery, Kandy". */
export function toastFor(view: TrackingView): Toast | null {
  const last = view.events.at(-1);
  if (!last) return null;
  const place = last.city ? `, ${last.city}` : "";
  return { id: `${view.trackingNumber}:${last.at}`, text: `${view.trackingNumber}: ${STATUS_LABELS[last.status]}${place}` };
}

/**
 * Shows a short notice when one of the signed-in user's parcels changes status. Only the latest few are kept.
 */
export function LiveToasts() {
  const [toasts, setToasts] = useState<Toast[]>([]);

  useEffect(
    () =>
      followSession((view) => {
        const toast = toastFor(view);
        if (!toast) return;
        setToasts((current) => {
          if (current.some((t) => t.id === toast.id)) return current;
          return [...current, toast].slice(-MAX_TOASTS);
        });
      }, () => undefined),
    [],
  );

  if (toasts.length === 0) return null;
  return (
    <div className="fixed bottom-4 right-4 z-50 flex max-w-sm flex-col gap-2" aria-live="polite">
      {toasts.map((toast) => (
        <div key={toast.id} role="status" className="card flex items-start justify-between gap-3 text-sm">
          <span>{toast.text}</span>
          <button
            type="button"
            className="text-slate-500 hover:text-slate-900"
            aria-label="Dismiss"
            onClick={() => setToasts((current) => current.filter((t) => t.id !== toast.id))}
          >
            Close
          </button>
        </div>
      ))}
    </div>
  );
}
