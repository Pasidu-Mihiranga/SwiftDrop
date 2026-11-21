import type { Metadata } from "next";
import Link from "next/link";
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
    <div className="mx-auto max-w-2xl space-y-5">
      <header>
        <nav aria-label="Breadcrumb" className="text-xs text-slate-500">
          <Link href="/track" className="hover:text-slate-800">
            Live tracking
          </Link>
          <span className="mx-1.5 text-slate-300">/</span>
          <span className="font-mono text-slate-700">{number}</span>
        </nav>
        <h1 className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">
          Parcel <span className="font-mono">{number}</span>
        </h1>
        <p className="mt-1 text-sm text-slate-600">
          Live delivery timeline &middot; Real-time Socket.IO updates &middot; Sri Lanka time
        </p>
      </header>

      <div className="rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
        <TrackForm defaultValue={number} />
      </div>

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
    <section
      role="status"
      className="flex flex-col items-center justify-center gap-3 rounded-2xl bg-white px-6 py-12 text-center shadow-sm ring-1 ring-slate-200"
    >
      <span className="flex h-11 w-11 items-center justify-center rounded-full bg-amber-50 text-amber-600">
        <svg viewBox="0 0 20 20" fill="none" className="h-5 w-5" aria-hidden="true">
          <path
            d="M10 3.2 17.4 16H2.6L10 3.2ZM10 8v3.8M10 14.2h.01"
            stroke="currentColor"
            strokeWidth="1.6"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </span>
      <div>
        <p className="font-semibold text-slate-900">Tracking timeline unavailable</p>
        <p className="mt-1 text-sm text-slate-500">{message}</p>
      </div>
    </section>
  );
}
