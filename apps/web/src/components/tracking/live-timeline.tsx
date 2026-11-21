"use client";

import { useEffect, useState } from "react";
import { applyUpdate, STATUS_LABELS, stepperFor, type TrackingView } from "@/lib/tracking";
import { followSession } from "@/lib/tracking-socket";
import { TrackingStepper } from "./tracking-stepper";

/**
 * Reusable timeline for a parcel detail page (M3 customer, M7 merchant). Starts from the view the page
 * already has and then follows the signed-in user's live pushes for this tracking number only.
 */
export function LiveTimeline({ initial }: { initial: TrackingView }) {
  const [view, setView] = useState(initial);

  useEffect(() => {
    setView(initial);
    return followSession((incoming) => {
      if (incoming.trackingNumber !== initial.trackingNumber) return;
      setView((current) => applyUpdate(current, incoming));
    }, () => undefined);
  }, [initial]);

  return (
    <section aria-label="Delivery timeline">
      <TrackingStepper stepper={stepperFor(view)} />
      <ul className="mt-4 space-y-2 text-sm text-slate-600">
        {[...view.events].reverse().map((event) => (
          <li key={`${event.status}-${event.at}`}>
            {STATUS_LABELS[event.status]}
            {event.city ? `, ${event.city}` : ""}
          </li>
        ))}
      </ul>
    </section>
  );
}
