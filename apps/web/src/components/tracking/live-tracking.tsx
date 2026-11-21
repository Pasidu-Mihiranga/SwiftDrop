"use client";

import { useEffect, useState } from "react";
import { applyUpdate, lastUpdateLabel, STATUS_LABELS, stepperFor, type TrackingView } from "@/lib/tracking";
import { followParcel, type LiveState } from "@/lib/tracking-socket";
import { TrackingStepper } from "./tracking-stepper";

const LIVE_LABEL: Record<LiveState, string> = {
  connecting: "Connecting...",
  live: "Live",
  reconnecting: "Reconnecting...",
};

/**
 * The parcel's current status, kept up to date from the server's live pushes. The server-rendered view
 * is shown first, so the page is useful before the socket connects (and without JavaScript).
 */
export function LiveTracking({ initial }: { initial: TrackingView }) {
  const [view, setView] = useState(initial);
  const [liveState, setLiveState] = useState<LiveState>("connecting");

  useEffect(() => {
    setView(initial);
    return followParcel(
      initial.trackingNumber,
      (incoming) => setView((current) => applyUpdate(current, incoming)),
      setLiveState,
    );
  }, [initial]);

  const stepper = stepperFor(view);
  const updated = lastUpdateLabel(view);
  const status = view.currentStatus ? STATUS_LABELS[view.currentStatus] : "Awaiting first update";

  return (
    <div className="card">
      <div className="flex items-center justify-between gap-3">
        <p className="text-xs uppercase tracking-wide text-slate-500">Tracking number</p>
        <p aria-live="polite" className="text-xs font-medium text-slate-500">{LIVE_LABEL[liveState]}</p>
      </div>
      <h1 className="font-mono text-xl font-bold">{view.trackingNumber}</h1>

      <dl className="mt-4 grid grid-cols-2 gap-3 text-sm">
        <div>
          <dt className="text-slate-500">From</dt>
          <dd className="font-medium">{view.from ?? "-"}</dd>
        </div>
        <div>
          <dt className="text-slate-500">To</dt>
          <dd className="font-medium">{view.to ?? "-"}</dd>
        </div>
        <div className="col-span-2">
          <dt className="text-slate-500">Recipient</dt>
          <dd className="font-medium">{view.recipient ?? "-"}</dd>
        </div>
      </dl>

      <p className="mt-5 text-lg font-semibold text-brand-700" data-testid="current-status">{status}</p>
      {updated ? <p className="text-sm text-slate-500">Last update: {updated}</p> : null}

      <TrackingStepper stepper={stepper} />

      {view.events.length > 0 ? (
        <section className="mt-6" aria-label="Status history">
          <h2 className="text-sm font-semibold text-slate-700">History</h2>
          <ul className="mt-2 space-y-2 text-sm text-slate-600">
            {[...view.events].reverse().map((event) => (
              <li key={`${event.status}-${event.at}`}>
                {STATUS_LABELS[event.status]}
                {event.city ? `, ${event.city}` : ""}
              </li>
            ))}
          </ul>
        </section>
      ) : null}
    </div>
  );
}
