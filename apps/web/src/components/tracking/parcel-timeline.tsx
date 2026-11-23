"use client";

import { useEffect, useState } from "react";
import type { TrackingView } from "@/lib/tracking";
import { LiveTimeline } from "./live-timeline";

type State = { kind: "loading" } | { kind: "ready"; view: TrackingView } | { kind: "missing" } | { kind: "error" };

/**
 * The live timeline for a parcel the caller may see (M6 on M3's and M7's pages): loads the owner
 * timeline from tracking-service through the BFF, then follows live updates over Socket.IO.
 */
export function ParcelTimeline({ parcelId, fetchImpl = fetch }: { parcelId: string; fetchImpl?: typeof fetch }) {
  const [state, setState] = useState<State>({ kind: "loading" });

  useEffect(() => {
    let active = true;
    fetchImpl(`/api/bff/api/v1/tracking/parcels/${encodeURIComponent(parcelId)}/timeline`, { cache: "no-store" })
      .then(async (response) => {
        if (!active) return;
        if (response.ok) setState({ kind: "ready", view: (await response.json()) as TrackingView });
        else setState({ kind: response.status === 404 ? "missing" : "error" });
      })
      .catch(() => active && setState({ kind: "error" }));
    return () => {
      active = false;
    };
  }, [parcelId, fetchImpl]);

  if (state.kind === "loading") return <p className="text-sm text-slate-500">Loading live timeline...</p>;
  if (state.kind === "missing") {
    return <p className="text-sm text-slate-500">The live timeline appears once the parcel's first update is processed.</p>;
  }
  if (state.kind === "error") return <p className="text-sm text-slate-500">The live timeline is not available right now.</p>;
  return <LiveTimeline initial={state.view} />;
}

/** A row's timeline, loaded only when the reader opens it (merchant order lists can be long). */
export function TimelineDisclosure({ parcelId }: { parcelId: string }) {
  const [open, setOpen] = useState(false);
  return (
    <details onToggle={(event) => setOpen((event.currentTarget as HTMLDetailsElement).open)}>
      <summary className="cursor-pointer text-xs font-medium text-brand-700">Timeline</summary>
      {open && (
        <div className="mt-2 min-w-64">
          <ParcelTimeline parcelId={parcelId} />
        </div>
      )}
    </details>
  );
}
