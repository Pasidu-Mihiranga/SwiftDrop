"use client";

import { useEffect, useState } from "react";
import { applyUpdate, type TrackingView } from "@/lib/tracking";
import { followSession } from "@/lib/tracking-socket";

/**
 * Live view of every parcel pushed to the dispatch room, newest first, keyed by tracking number.
 * For staff only: the server refuses the dispatch room to other roles, so this feed stays empty for them.
 * Used by the dispatcher board (M5) once that page is in place.
 */
export function useDispatchFeed(): TrackingView[] {
  const [byNumber, setByNumber] = useState<Record<string, TrackingView>>({});

  useEffect(
    () =>
      followSession((incoming) => {
        setByNumber((current) => {
          const existing = current[incoming.trackingNumber];
          const next = existing ? applyUpdate(existing, incoming) : incoming;
          return { ...current, [incoming.trackingNumber]: next };
        });
      }, () => undefined),
    [],
  );

  return Object.values(byNumber).sort((a, b) => (b.events.at(-1)?.at ?? "").localeCompare(a.events.at(-1)?.at ?? ""));
}
