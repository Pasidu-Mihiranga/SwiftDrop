import Link from "next/link";
import { auth } from "@/auth";
import { gatewayFetch } from "@/lib/gateway";

export const metadata = { title: "Operations" };

async function checkService(path: string): Promise<{ ok: boolean; detail: string }> {
  try {
    const res = await gatewayFetch(path);
    if (res.status >= 500) return { ok: false, detail: `HTTP ${res.status}` };
    if (!res.ok) return { ok: false, detail: `HTTP ${res.status}` };
    return { ok: true, detail: "Connected" };
  } catch {
    return { ok: false, detail: "Offline" };
  }
}

export default async function AdminHome() {
  const session = await auth();
  const roles = session && !session.error ? session.user.roles : [];
  const isAdmin = roles.includes("ADMIN");

  const [pricingCheck, usersCheck] = await Promise.all([
    checkService("/api/v1/pricing/zones"),
    checkService("/api/v1/users/meta/whoami"),
  ]);

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
            <span className="text-slate-700">Operations</span>
          </nav>
          <h1 className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">
            Operations command center
          </h1>
          <p className="mt-1 text-sm text-slate-600">
            {session?.user.name ?? session?.user.email ?? "Operator"} &middot; Roles: {roles.join(", ") || "STAFF"} &middot; Sri Lanka time
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Link href="/admin/reports" className="btn-primary py-2 text-xs">
            Open delivery reports
          </Link>
          <Link href="/admin/dispatch" className="btn-secondary py-2 text-xs">
            Dispatch board
          </Link>
        </div>
      </header>

      {/* KPI Row matching PR #36 (.viz-root) */}
      <section aria-label="Operations summary" className="viz-root grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-6">
        <div className="relative col-span-2 overflow-hidden rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
          <div className="pointer-events-none absolute -right-10 -top-10 h-36 w-36 rounded-full bg-brand-50" aria-hidden="true" />
          <p className="relative text-sm font-medium text-slate-600">Island-wide coverage</p>
          <p className="relative mt-1 text-4xl font-semibold tracking-tight text-slate-900 sm:text-5xl">9 zones</p>
          <p className="relative mt-1 text-xs text-slate-500">25 districts &middot; 81 priced zone-pair lanes</p>
          <div className="relative mt-4 h-2.5 overflow-hidden rounded-full bg-[var(--viz-track)]">
            <div className="viz-grow h-full w-full rounded-full bg-[var(--viz-delivered)]" />
          </div>
        </div>

        <div className="flex min-w-0 flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-delivered)]" />
            Analytics (M10)
          </p>
          <p className="mt-1 text-xl font-semibold tracking-tight text-slate-900">Delivery reports</p>
          <Link href="/admin/reports" className="mt-2 text-xs font-medium text-brand-700 hover:underline">
            Daily KPIs &amp; CSV export &rarr;
          </Link>
        </div>

        <div className="flex min-w-0 flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-created)]" />
            Fleet &amp; dispatch (M1 &middot; M5)
          </p>
          <p className="mt-1 text-xl font-semibold tracking-tight text-slate-900">Couriers &amp; loads</p>
          <Link href="/admin/couriers" className="mt-2 text-xs font-medium text-brand-700 hover:underline">
            Manage courier directory &rarr;
          </Link>
        </div>

        <div className="flex min-w-0 flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-failed)]" />
            Webhooks (M8)
          </p>
          <p className="mt-1 text-xl font-semibold tracking-tight text-slate-900">Dead-letter queue</p>
          <Link href="/admin/webhooks" className="mt-2 text-xs font-medium text-brand-700 hover:underline">
            Inspect &amp; replay DLQ &rarr;
          </Link>
        </div>

        <div className="flex min-w-0 flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-cancelled)]" />
            Gateway health
          </p>
          <p className="mt-1 text-xl font-semibold tracking-tight text-slate-900">
            {pricingCheck.ok && usersCheck.ok ? "Healthy" : "Degraded"}
          </p>
          <p className="mt-2 text-xs text-slate-500">
            Pricing: {pricingCheck.detail} &middot; Users: {usersCheck.detail}
          </p>
        </div>
      </section>

      {/* Pricing & Zones Card (M2) */}
      {isAdmin ? (
        <section className="rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <span className="status-pill-neutral">M2 &middot; Pricing &amp; Zones</span>
              <h2 className="mt-2 text-lg font-semibold text-slate-900">Zones &amp; pricing configuration</h2>
              <p className="mt-1 text-sm text-slate-600">
                Zone coverage, district assignments, size multipliers, the rate cards every quote is priced from, and the charges and negotiated terms layered on top.
              </p>
            </div>
            <Link href="/quote" className="text-xs font-medium text-brand-700 hover:underline">
              Public quote calculator &rarr;
            </Link>
          </div>
          <div className="mt-4 flex flex-wrap gap-2">
            <Link className="btn-primary py-2 text-xs" href="/admin/pricing/zones">
              Zones &amp; districts
            </Link>
            <Link className="btn-secondary py-2 text-xs" href="/admin/pricing/rate-cards">
              Rate cards
            </Link>
            <Link className="btn-secondary py-2 text-xs" href="/admin/pricing/remote-areas">
              Remote areas
            </Link>
            <Link className="btn-secondary py-2 text-xs" href="/admin/pricing/merchants">
              Merchant rates
            </Link>
          </div>
        </section>
      ) : null}

      {/* Bento Grid of Operations Modules */}
      <div className="grid gap-5 lg:grid-cols-3">
        <section className="rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200 lg:col-span-2">
          <h2 className="font-semibold text-slate-900">Operations modules</h2>
          <p className="text-xs text-slate-500">Direct access to every operational workspace</p>

          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            <Link
              href="/admin/reports"
              className="group rounded-xl border border-slate-200 p-4 transition hover:border-brand-500 hover:bg-brand-50/20"
            >
              <div className="flex items-center justify-between">
                <span className="font-semibold text-slate-900 group-hover:text-brand-700">Delivery reports</span>
                <span className="status-pill-neutral">M10</span>
              </div>
              <p className="mt-1 text-xs text-slate-500">
                Deliveries per day, success rate, period comparisons, zone breakdown, and courier leaderboard.
              </p>
            </Link>

            <Link
              href="/admin/dispatch"
              className="group rounded-xl border border-slate-200 p-4 transition hover:border-brand-500 hover:bg-brand-50/20"
            >
              <div className="flex items-center justify-between">
                <span className="font-semibold text-slate-900 group-hover:text-brand-700">Dispatch &amp; workload</span>
                <span className="status-pill-neutral">M5 &middot; M6</span>
              </div>
              <p className="mt-1 text-xs text-slate-500">
                Live dispatch stream, courier workload distribution by zone, and run-sheet assignments.
              </p>
            </Link>

            <Link
              href="/admin/couriers"
              className="group rounded-xl border border-slate-200 p-4 transition hover:border-brand-500 hover:bg-brand-50/20"
            >
              <div className="flex items-center justify-between">
                <span className="font-semibold text-slate-900 group-hover:text-brand-700">Courier directory</span>
                <span className="status-pill-neutral">M1</span>
              </div>
              <p className="mt-1 text-xs text-slate-500">
                Inspect and configure courier vehicles, home zones, daily parcel capacities, and active status.
              </p>
            </Link>

            {isAdmin && (
              <>
                <Link
                  href="/admin/webhooks"
                  className="group rounded-xl border border-slate-200 p-4 transition hover:border-brand-500 hover:bg-brand-50/20"
                >
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-slate-900 group-hover:text-brand-700">
                      Webhook dead-letter replay
                    </span>
                    <span className="status-pill-neutral">M8</span>
                  </div>
                  <p className="mt-1 text-xs text-slate-500">
                    Audit webhook delivery logs across all merchants and replay dead-lettered events.
                  </p>
                </Link>

                <Link
                  href="/admin/notifications/sms-inbox"
                  className="group rounded-xl border border-slate-200 p-4 transition hover:border-brand-500 hover:bg-brand-50/20"
                >
                  <div className="flex items-center justify-between">
                    <span className="font-semibold text-slate-900 group-hover:text-brand-700">SMS notification log</span>
                    <span className="status-pill-neutral">M9</span>
                  </div>
                  <p className="mt-1 text-xs text-slate-500">
                    Real-time customer SMS dispatches with PII-masked numbers and clickable tracking links.
                  </p>
                </Link>
              </>
            )}
          </div>
        </section>

        <section className="flex flex-col justify-between rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
          <div className="space-y-4">
            <div>
              <h2 className="font-semibold text-slate-900">Operator session</h2>
              <p className="text-xs text-slate-500">Authenticated via Keycloak OIDC + BFF</p>
            </div>

            <div className="rounded-xl bg-slate-50 p-3.5 text-sm ring-1 ring-slate-200/70">
              <p className="font-medium text-slate-900">{session?.user.name ?? session?.user.email}</p>
              <div className="mt-2 flex flex-wrap gap-1.5">
                {roles.map((role) => (
                  <span key={role} className="rounded-full bg-brand-50 px-2.5 py-0.5 text-xs font-medium text-brand-700">
                    {role}
                  </span>
                ))}
              </div>
            </div>

            <div className="space-y-2 text-xs">
              <div className="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2">
                <span className="font-medium text-slate-700">pricing-service</span>
                <span className={pricingCheck.ok ? "status-pill-success" : "status-pill-warning"}>
                  {pricingCheck.detail}
                </span>
              </div>
              <div className="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2">
                <span className="font-medium text-slate-700">user-service</span>
                <span className={usersCheck.ok ? "status-pill-success" : "status-pill-warning"}>
                  {usersCheck.detail}
                </span>
              </div>
            </div>
          </div>

          <div className="mt-4 border-t border-slate-100 pt-3">
            <Link href="/account" className="text-xs font-medium text-brand-700 hover:underline">
              Manage operator profile &amp; saved addresses &rarr;
            </Link>
          </div>
        </section>
      </div>
    </div>
  );
}
