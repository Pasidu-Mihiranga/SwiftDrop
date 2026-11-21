import type { Metadata } from "next";
import { TrackForm } from "@/components/track-form";
import { LiveTracking } from "@/components/tracking/live-tracking";
import { fetchPublicTracking, type PublicLookup } from "@/lib/tracking-api";
import { TRACKING_NUMBER_PATTERN } from "@/lib/tracking";

export async function generateMetadata({ params }: { params: Promise<{ trackingNumber: string }> }): Promise<Metadata> {
  const { trackingNumber } = await params;
  return { title: `Tracking ${trackingNumber.toUpperCase()}` };
}

/** Public, mobile-first tracking page: a server-rendered status, then live updates over the socket. */
export default async function TrackPage({ params }: { params: Promise<{ trackingNumber: string }> }) {
  const { trackingNumber } = await params;
  const number = trackingNumber.toUpperCase();

  let lookup: PublicLookup = { kind: "not-found" };
  if (TRACKING_NUMBER_PATTERN.test(number)) lookup = await fetchPublicTracking(number);

  return (
    <div className="mx-auto max-w-xl space-y-6">
      <TrackForm defaultValue={number} />
      {lookup.kind === "found" ? <LiveTracking initial={lookup.view} /> : <LookupMessage lookup={lookup} />}
    </div>
  );
}

function LookupMessage({ lookup }: { lookup: Exclude<PublicLookup, { kind: "found" }> }) {
  const message = {
    "not-found": "We could not find a parcel with this tracking number. Check the number and try again.",
    busy: "Too many lookups from your connection. Please wait a minute and try again.",
    unavailable: "Tracking is temporarily unavailable. Please try again shortly.",
  }[lookup.kind];
  return (
    <div className="card" role="status">
      <p className="text-slate-700">{message}</p>
    </div>
  );
}
