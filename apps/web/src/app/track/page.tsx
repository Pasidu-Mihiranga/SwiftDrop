import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { TrackForm } from "@/components/track-form";
import { TRACKING_NUMBER_PATTERN } from "@/lib/tracking";

export const metadata: Metadata = { title: "Track a parcel" };

export default async function TrackSearchPage({
  searchParams,
}: {
  searchParams: Promise<{ invalid?: string; q?: string; trackingNumber?: string }>;
}) {
  const { invalid, q, trackingNumber } = await searchParams;
  const candidate = (q ?? trackingNumber ?? "").trim().toUpperCase();
  if (candidate && TRACKING_NUMBER_PATTERN.test(candidate)) {
    redirect(`/track/${candidate}`);
  }

  return (
    <div className="mx-auto max-w-2xl space-y-5">
      <header>
        <nav aria-label="Breadcrumb" className="text-xs text-slate-500">
          <Link href="/" className="hover:text-slate-800">
            SwiftDrop
          </Link>
          <span className="mx-1.5 text-slate-300">/</span>
          <span className="text-slate-700">Live tracking</span>
        </nav>
        <h1 className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">Track a parcel</h1>
        <p className="mt-1 text-sm text-slate-600">
          Real-time courier milestones &middot; Socket.IO live timeline &middot; No sign-in required
        </p>
      </header>

      <section className="rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200 sm:p-6">
        {invalid && (
          <p role="alert" className="mb-4 rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-800 ring-1 ring-amber-200">
            That does not look like a valid SwiftDrop tracking number (expected format: <span className="font-mono font-semibold">SD261020ABCDEFG</span>).
          </p>
        )}
        <TrackForm defaultValue={candidate || undefined} />
        <p className="mt-3 text-xs text-slate-500">
          Enter the 15-character tracking number from your booking confirmation, SMS notification, or PDF receipt.
        </p>
      </section>

      <section className="grid gap-4 sm:grid-cols-3">
        <div className="rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-xs font-medium text-slate-500">
            <span className="h-2 w-2 rounded-full bg-[var(--viz-created,#6250d6)]" />
            Instant projection
          </p>
          <p className="mt-1 text-sm font-semibold text-slate-900">Booking to doorstep</p>
          <p className="mt-1 text-xs text-slate-500">Every scan from pickup to delivery is projected into the timeline.</p>
        </div>
        <div className="rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-xs font-medium text-slate-500">
            <span className="h-2 w-2 rounded-full bg-[var(--viz-delivered,#2a78d6)]" />
            Live socket stream
          </p>
          <p className="mt-1 text-sm font-semibold text-slate-900">Zero page refresh</p>
          <p className="mt-1 text-xs text-slate-500">Status transitions push over Socket.IO as soon as the courier updates.</p>
        </div>
        <div className="rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-xs font-medium text-slate-500">
            <span className="h-2 w-2 rounded-full bg-[var(--viz-cancelled,#1baf7a)]" />
            Privacy by default
          </p>
          <p className="mt-1 text-sm font-semibold text-slate-900">PII-masked public view</p>
          <p className="mt-1 text-xs text-slate-500">Public lookups show city, zone, and milestones without exposing street addresses.</p>
        </div>
      </section>
    </div>
  );
}
