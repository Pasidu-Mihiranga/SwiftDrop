import Link from "next/link";
import { auth } from "@/auth";
import { gatewayFetch } from "@/lib/gateway";

export const metadata = { title: "Today's runs" };

const LIFECYCLE_STEPS = [
  { code: "CREATED", label: "Booking created", detail: "Customer or merchant books parcel" },
  { code: "PICKED_UP", label: "Picked up", detail: "Collected from sender by courier" },
  { code: "IN_TRANSIT", label: "In transit", detail: "Moving between provincial sorting hubs" },
  { code: "OUT_FOR_DELIVERY", label: "Out for delivery", detail: "On courier vehicle for final drop-off" },
  { code: "DELIVERED", label: "Delivered", detail: "Handed over with photo proof of delivery" },
];

export default async function CourierHome() {
  const session = await auth();
  let dispatchConnected = false;
  try {
    const res = await gatewayFetch("/api/v1/dispatch/meta/whoami");
    dispatchConnected = res.ok;
  } catch {
    dispatchConnected = false;
  }

  return (
    <div className="space-y-5">
      {/* Header matching PR #36 */}
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <nav aria-label="Breadcrumb" className="text-xs text-slate-500">
            <Link href="/" className="hover:text-slate-800">
              SwiftDrop
            </Link>
            <span className="mx-1.5 text-slate-300">/</span>
            <span className="text-slate-700">Courier operations</span>
          </nav>
          <h1 className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">Today&apos;s run sheet</h1>
          <p className="mt-1 text-sm text-slate-600">
            {session?.user.name ?? session?.user.email ?? "Courier"} &middot; Mobile status lifecycle &amp; proof of delivery &middot; Sri Lanka time
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Link href="/track" className="btn-secondary py-2 text-xs">
            Lookup tracking #
          </Link>
          <Link href="/account" className="btn-secondary py-2 text-xs">
            Courier profile
          </Link>
        </div>
      </header>

      {/* KPI Row matching PR #36 (.viz-root) */}
      <section aria-label="Run sheet summary" className="viz-root grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-6">
        <div className="relative col-span-2 overflow-hidden rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
          <div className="pointer-events-none absolute -right-10 -top-10 h-36 w-36 rounded-full bg-brand-50" aria-hidden="true" />
          <p className="relative text-sm font-medium text-slate-600">Shift readiness</p>
          <p className="relative mt-1 text-4xl font-semibold tracking-tight text-slate-900 sm:text-5xl">Active</p>
          <p className="relative mt-1 text-xs text-slate-500">
            Dispatch link: {dispatchConnected ? "Connected to dispatch-service" : "Standby"}
          </p>
          <div className="relative mt-4 h-2.5 overflow-hidden rounded-full bg-[var(--viz-track)]">
            <div className="viz-grow h-full w-full rounded-full bg-[var(--viz-delivered)]" />
          </div>
        </div>

        <div className="flex min-w-0 flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-created)]" />
            Pickups
          </p>
          <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">Ready</p>
          <p className="mt-2 text-xs text-slate-500">Scan on collection</p>
        </div>

        <div className="flex min-w-0 flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-delivered)]" />
            Proof of delivery
          </p>
          <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">S3 / Photo</p>
          <p className="mt-2 text-xs text-slate-500">Presigned upload up to 5 MB</p>
        </div>

        <div className="flex min-w-0 flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-failed)]" />
            Exceptions
          </p>
          <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">Guarded</p>
          <p className="mt-2 text-xs text-slate-500">Requires failure reason code</p>
        </div>

        <div className="flex min-w-0 flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-cancelled)]" />
            Live push
          </p>
          <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">Real-time</p>
          <p className="mt-2 text-xs text-slate-500">Outbox &rarr; Kafka &rarr; Socket.IO</p>
        </div>
      </section>

      {/* Bento Grid: Parcel State Machine & Quick Lookup */}
      <div className="grid gap-5 lg:grid-cols-3">
        <section className="rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200 lg:col-span-2">
          <h2 className="font-semibold text-slate-900">Parcel status lifecycle (M4 state machine)</h2>
          <p className="text-xs text-slate-500">
            Enforced transitions in parcel-service with append-only audit history and outbox events
          </p>

          <ol className="mt-4 grid gap-3 sm:grid-cols-5">
            {LIFECYCLE_STEPS.map((step, index) => (
              <li key={step.code} className="rounded-xl bg-slate-50 p-3.5 ring-1 ring-slate-200/80">
                <div className="flex items-center justify-between">
                  <span className="flex h-6 w-6 items-center justify-center rounded-full bg-brand-600 text-xs font-bold text-white">
                    {index + 1}
                  </span>
                  <span className="font-mono text-[11px] font-semibold text-brand-700">{step.code}</span>
                </div>
                <p className="mt-2 text-sm font-semibold text-slate-900">{step.label}</p>
                <p className="mt-1 text-xs text-slate-500">{step.detail}</p>
              </li>
            ))}
          </ol>
        </section>

        <section className="flex flex-col justify-between rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
          <div className="space-y-3">
            <h2 className="font-semibold text-slate-900">Courier run sheet</h2>
            <p className="text-xs text-slate-500">
              Assigned pickups and deliveries appear here as soon as a dispatcher assigns parcels to your run sheet.
            </p>
            <div className="rounded-xl bg-slate-50 p-4 text-xs text-slate-600 ring-1 ring-slate-200/70">
              <p className="font-semibold text-slate-900">Signed in courier</p>
              <p className="mt-1">{session?.user.name ?? session?.user.email}</p>
              <p className="mt-2 text-slate-500">
                Use the public tracker below to inspect the live timeline of any parcel on your route.
              </p>
            </div>
          </div>

          <div className="mt-4 flex flex-wrap gap-2 border-t border-slate-100 pt-3">
            <Link href="/track" className="btn-primary w-full py-2 text-xs">
              Open live parcel tracker
            </Link>
          </div>
        </section>
      </div>
    </div>
  );
}
