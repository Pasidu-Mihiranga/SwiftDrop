import Link from "next/link";
import { auth } from "@/auth";
import { LiveToasts } from "@/components/tracking/live-toasts";
import { gatewayFetch } from "@/lib/gateway";

export const metadata = { title: "Merchant portal" };

interface WebhookEndpoint {
  id: string;
  url: string;
  eventTypes: string[];
  active: boolean;
  createdAt: string;
}

interface DeliveryPage {
  content: {
    id: string;
    eventType: string;
    url: string;
    status: "PENDING" | "DELIVERED" | "RETRYING" | "DEAD";
    attemptCount: number;
    createdAt: string;
  }[];
  page: { totalElements: number };
}

export default async function MerchantHome() {
  const session = await auth();
  let endpoints: WebhookEndpoint[] = [];
  let deliveries: DeliveryPage["content"] = [];
  let totalDeliveries = 0;

  try {
    const [epRes, delRes] = await Promise.all([
      gatewayFetch("/api/v1/webhooks/endpoints"),
      gatewayFetch("/api/v1/webhooks/deliveries?page=0&size=5"),
    ]);
    if (epRes.ok) {
      const data = await epRes.json();
      endpoints = Array.isArray(data) ? data : [];
    }
    if (delRes.ok) {
      const data = (await delRes.json()) as DeliveryPage;
      deliveries = data.content ?? [];
      totalDeliveries = data.page?.totalElements ?? deliveries.length;
    }
  } catch {
    // Services offline fallback
  }

  const deliveredCount = deliveries.filter((d) => d.status === "DELIVERED").length;
  const deadCount = deliveries.filter((d) => d.status === "DEAD").length;
  const retryingCount = deliveries.filter((d) => d.status === "RETRYING").length;

  return (
    <div className="space-y-5">
      <LiveToasts />

      {/* Header matching PR #36 */}
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <nav aria-label="Breadcrumb" className="text-xs text-slate-500">
            <Link href="/" className="hover:text-slate-800">
              SwiftDrop
            </Link>
            <span className="mx-1.5 text-slate-300">/</span>
            <span className="text-slate-700">Merchant portal</span>
          </nav>
          <h1 className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">Merchant portal</h1>
          <p className="mt-1 text-sm text-slate-600">
            {session?.user.name ?? session?.user.email ?? "Merchant"} &middot; Store API orders, HMAC webhooks &amp; live notifications
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Link href="/merchant/register" className="btn-secondary py-2 text-xs">
            Onboarding status
          </Link>
          <Link href="/merchant/webhooks" className="btn-primary py-2 text-xs">
            Manage webhooks &amp; logs
          </Link>
        </div>
      </header>

      {/* KPI Row matching PR #36 (.viz-root) */}
      <section aria-label="Merchant summary" className="viz-root grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-6">
        <div className="relative col-span-2 overflow-hidden rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
          <div className="pointer-events-none absolute -right-10 -top-10 h-36 w-36 rounded-full bg-brand-50" aria-hidden="true" />
          <p className="relative text-sm font-medium text-slate-600">Webhook endpoints</p>
          <p className="relative mt-1 text-4xl font-semibold tracking-tight text-slate-900 sm:text-5xl">
            {endpoints.length}
          </p>
          <p className="relative mt-1 text-xs text-slate-500">
            HMAC-SHA256 signed event subscriptions with BullMQ backoff
          </p>
          <div className="relative mt-4 h-2.5 overflow-hidden rounded-full bg-[var(--viz-track)]">
            <div
              className="viz-grow h-full rounded-full bg-[var(--viz-delivered)]"
              style={{ width: `${endpoints.length > 0 ? 100 : 20}%` }}
            />
          </div>
        </div>

        <div className="flex min-w-0 flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-created)]" />
            Webhook events
          </p>
          <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">{totalDeliveries}</p>
          <p className="mt-2 text-xs text-slate-500">Dispatched to your store</p>
        </div>

        <div className="flex min-w-0 flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-delivered)]" />
            Delivered (2xx)
          </p>
          <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">{deliveredCount}</p>
          <p className="mt-2 text-xs text-slate-500">In latest activity window</p>
        </div>

        <div className="flex min-w-0 flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-failed)]" />
            Retrying / DLQ
          </p>
          <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">
            {retryingCount + deadCount}
          </p>
          <p className="mt-2 text-xs text-slate-500">
            {retryingCount} retrying &middot; {deadCount} dead-lettered
          </p>
        </div>

        <div className="flex min-w-0 flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-cancelled)]" />
            Negotiated rates
          </p>
          <p className="mt-1 text-xl font-semibold tracking-tight text-slate-900">B2B pricing</p>
          <Link href="/quote" className="mt-2 text-xs font-medium text-brand-700 hover:underline">
            Check lane tariff &rarr;
          </Link>
        </div>
      </section>

      {/* Bento Grid: Webhook Endpoints + Recent Webhook Deliveries */}
      <div className="grid gap-5 lg:grid-cols-3">
        <section className="rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200 lg:col-span-2">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="font-semibold text-slate-900">Recent webhook deliveries (M8)</h2>
              <p className="text-xs text-slate-500">Signed with X-SwiftDrop-Signature-256 and retried with exponential backoff</p>
            </div>
            <Link href="/merchant/webhooks" className="text-xs font-medium text-brand-700 hover:underline">
              Open webhook manager &rarr;
            </Link>
          </div>

          {deliveries.length === 0 ? (
            <div className="mt-6 rounded-xl bg-slate-50 px-6 py-10 text-center ring-1 ring-slate-200/70">
              <p className="font-semibold text-slate-900">No webhook deliveries yet</p>
              <p className="mt-1 text-xs text-slate-500">
                Register an HTTPS webhook endpoint and send a test ping to verify your store&apos;s signature handler.
              </p>
              <Link href="/merchant/webhooks" className="btn-primary mt-4 inline-flex py-1.5 text-xs">
                Register webhook endpoint
              </Link>
            </div>
          ) : (
            <div className="mt-4 overflow-x-auto">
              <table className="w-full text-left text-sm text-slate-600">
                <thead className="border-b border-slate-200 bg-slate-50/80 text-xs font-semibold uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="px-4 py-2.5">Event</th>
                    <th className="px-4 py-2.5">Destination URL</th>
                    <th className="px-4 py-2.5">Attempts</th>
                    <th className="px-4 py-2.5 text-right">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {deliveries.map((d) => (
                    <tr key={d.id} className="hover:bg-slate-50/70">
                      <td className="px-4 py-3 font-mono text-xs font-semibold text-slate-900">{d.eventType}</td>
                      <td className="max-w-[240px] truncate px-4 py-3 font-mono text-xs text-slate-600">{d.url}</td>
                      <td className="px-4 py-3 tabular-nums text-xs">{d.attemptCount}</td>
                      <td className="px-4 py-3 text-right">
                        <span
                          className={
                            d.status === "DELIVERED"
                              ? "status-pill-success"
                              : d.status === "DEAD"
                              ? "rounded-full bg-red-50 px-2 py-0.5 text-xs font-medium text-red-700"
                              : "status-pill-warning"
                          }
                        >
                          {d.status}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <section className="flex flex-col justify-between rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
          <div className="space-y-4">
            <div>
              <h2 className="font-semibold text-slate-900">Developer integration (M7 &middot; M8)</h2>
              <p className="text-xs text-slate-500">OAuth2 Client Credentials &amp; HMAC Webhooks</p>
            </div>

            <div className="space-y-2.5 text-xs text-slate-600">
              <div className="rounded-xl bg-slate-50 p-3.5 ring-1 ring-slate-200/70">
                <p className="font-semibold text-slate-900">1. Machine-to-machine OAuth2</p>
                <p className="mt-1 text-slate-500">
                  Authenticate with Keycloak client <span className="font-mono text-slate-800">merchant-demo</span> to create orders via <span className="font-mono text-slate-800">POST /api/v1/merchant/orders</span> with an <span className="font-mono text-slate-800">Idempotency-Key</span>.
                </p>
              </div>

              <div className="rounded-xl bg-slate-50 p-3.5 ring-1 ring-slate-200/70">
                <p className="font-semibold text-slate-900">2. Signed webhooks</p>
                <p className="mt-1 text-slate-500">
                  Verify <span className="font-mono text-slate-800">X-SwiftDrop-Signature-256</span> using your endpoint&apos;s HMAC secret before acknowledging events.
                </p>
              </div>
            </div>
          </div>

          <div className="mt-4 flex flex-col gap-2 border-t border-slate-100 pt-3">
            <Link href="/merchant/webhooks" className="btn-primary w-full py-2 text-xs">
              Configure webhook endpoints
            </Link>
            <Link href="/track" className="btn-secondary w-full py-2 text-xs">
              Track a merchant shipment
            </Link>
          </div>
        </section>
      </div>
    </div>
  );
}
