"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";

const WEBHOOK_EVENTS = [
  {
    code: "parcel.created",
    label: "parcel.created",
    description: "Fired when a new merchant order is created and assigned a tracking number.",
  },
  {
    code: "parcel.status_changed",
    label: "parcel.status_changed",
    description: "Fired whenever a parcel transitions status (PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, FAILED).",
  },
] as const;

interface WebhookEndpoint {
  id: string;
  url: string;
  eventTypes: string[];
  active: boolean;
  createdAt: string;
}

interface WebhookAttempt {
  at: string;
  httpStatus?: number;
  latencyMs: number;
  error?: string;
}

interface WebhookDelivery {
  id: string;
  endpointId: string;
  eventType: string;
  url: string;
  status: "PENDING" | "DELIVERED" | "RETRYING" | "DEAD";
  attemptCount: number;
  attempts: WebhookAttempt[];
  nextRetryAt: string | null;
  createdAt: string;
}

export default function MerchantWebhooksPage() {
  const [endpoints, setEndpoints] = useState<WebhookEndpoint[]>([]);
  const [deliveries, setDeliveries] = useState<WebhookDelivery[]>([]);
  const [totalDeliveries, setTotalDeliveries] = useState(0);
  const [statusFilter, setStatusFilter] = useState<"ALL" | "DELIVERED" | "RETRYING" | "DEAD" | "PENDING">("ALL");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Registration form state
  const [url, setUrl] = useState("");
  const [selectedEvents, setSelectedEvents] = useState<string[]>(["parcel.created", "parcel.status_changed"]);
  const [submitting, setSubmitting] = useState(false);
  const [issuedSecret, setIssuedSecret] = useState<{ url: string; secret: string } | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const loadAll = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const qs = statusFilter !== "ALL" ? `?status=${statusFilter}&page=0&size=25` : "?page=0&size=25";
      const [epRes, delRes] = await Promise.all([
        fetch("/api/bff/api/v1/webhooks/endpoints"),
        fetch(`/api/bff/api/v1/webhooks/deliveries${qs}`),
      ]);
      if (epRes.ok) {
        const list = await epRes.json();
        setEndpoints(Array.isArray(list) ? list : []);
      } else {
        const problem = await epRes.json().catch(() => ({}));
        setError(problem.detail || `Could not load webhook endpoints (HTTP ${epRes.status})`);
      }
      if (delRes.ok) {
        const data = await delRes.json();
        const items: WebhookDelivery[] = data.content ?? [];
        setDeliveries(items);
        setTotalDeliveries(data.page?.totalElements ?? items.length);
      }
    } catch {
      setError("Webhook service is unreachable. Ensure webhook-service is running.");
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    loadAll();
  }, [loadAll]);

  function toggleEvent(code: string) {
    setSelectedEvents((prev) => (prev.includes(code) ? prev.filter((e) => e !== code) : [...prev, code]));
  }

  async function handleRegister(e: React.FormEvent) {
    e.preventDefault();
    if (!url.trim() || selectedEvents.length === 0) return;
    setSubmitting(true);
    setError(null);
    setNotice(null);
    try {
      const res = await fetch("/api/bff/api/v1/webhooks/endpoints", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ url: url.trim(), eventTypes: selectedEvents }),
      });
      if (res.ok) {
        const created = (await res.json()) as WebhookEndpoint & { secret: string };
        setIssuedSecret({ url: created.url, secret: created.secret });
        setUrl("");
        await loadAll();
      } else {
        const problem = await res.json().catch(() => ({}));
        setError(problem.detail || `Could not register endpoint (HTTP ${res.status})`);
      }
    } catch {
      setError("Network error while registering webhook endpoint.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleTestPing(id: string) {
    setNotice(null);
    setError(null);
    try {
      const res = await fetch(`/api/bff/api/v1/webhooks/endpoints/${id}/test`, { method: "POST" });
      if (res.ok) {
        setNotice("Test ping (webhook.test) queued for delivery.");
        setTimeout(loadAll, 1000);
      } else {
        const problem = await res.json().catch(() => ({}));
        setError(problem.detail || `Could not queue test ping (HTTP ${res.status})`);
      }
    } catch {
      setError("Network error while sending test ping.");
    }
  }

  async function handleRemove(id: string) {
    setNotice(null);
    setError(null);
    try {
      const res = await fetch(`/api/bff/api/v1/webhooks/endpoints/${id}`, { method: "DELETE" });
      if (res.ok) {
        setEndpoints((prev) => prev.filter((ep) => ep.id !== id));
        setNotice("Webhook endpoint removed.");
      } else {
        setError(`Could not delete endpoint (HTTP ${res.status})`);
      }
    } catch {
      setError("Network error while removing webhook endpoint.");
    }
  }

  const deliveredCount = deliveries.filter((d) => d.status === "DELIVERED").length;
  const retryingCount = deliveries.filter((d) => d.status === "RETRYING").length;
  const deadCount = deliveries.filter((d) => d.status === "DEAD").length;

  return (
    <div className="space-y-5">
      {/* Header matching PR #36 */}
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <nav aria-label="Breadcrumb" className="text-xs text-slate-500">
            <Link href="/merchant" className="hover:text-slate-800">
              Merchant portal
            </Link>
            <span className="mx-1.5 text-slate-300">/</span>
            <span className="text-slate-700">Webhooks</span>
          </nav>
          <h1 className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">
            Webhook subscriptions &amp; delivery log
          </h1>
          <p className="mt-1 text-sm text-slate-600">
            M8 Webhooks &middot; HMAC-SHA256 signing &middot; BullMQ exponential backoff retries
          </p>
        </div>
        <button type="button" onClick={loadAll} disabled={loading} className="btn-secondary py-2 text-xs">
          {loading ? "Refreshing..." : "Refresh log"}
        </button>
      </header>

      {/* One-time HMAC Secret Banner */}
      {issuedSecret && (
        <section
          role="alert"
          className="space-y-2 rounded-2xl bg-emerald-50 p-5 text-sm text-emerald-900 shadow-sm ring-1 ring-emerald-200"
        >
          <div className="flex items-center justify-between">
            <p className="font-semibold">Signing secret issued (shown only once)</p>
            <button
              type="button"
              onClick={() => setIssuedSecret(null)}
              className="text-xs font-medium text-emerald-700 hover:underline"
            >
              Dismiss
            </button>
          </div>
          <p className="text-xs text-emerald-800">
            Save this secret now to verify <span className="font-mono font-semibold">X-SwiftDrop-Signature-256</span> on{" "}
            <span className="font-mono">{issuedSecret.url}</span>:
          </p>
          <div className="rounded-xl bg-white px-4 py-2.5 font-mono text-xs font-bold text-slate-900 ring-1 ring-emerald-200 select-all">
            {issuedSecret.secret}
          </div>
        </section>
      )}

      {/* KPI Row matching PR #36 (.viz-root) */}
      <section aria-label="Webhook metrics" className="viz-root grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
        <div className="flex flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-created)]" />
            Active endpoints
          </p>
          <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">{endpoints.length}</p>
          <p className="mt-1 text-xs text-slate-500">Subscribed HTTPS targets</p>
        </div>

        <div className="flex flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-delivered)]" />
            Delivered
          </p>
          <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">{deliveredCount}</p>
          <p className="mt-1 text-xs text-slate-500">2xx acknowledged</p>
        </div>

        <div className="flex flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-cancelled)]" />
            Retrying
          </p>
          <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">{retryingCount}</p>
          <p className="mt-1 text-xs text-slate-500">In backoff queue</p>
        </div>

        <div className="flex flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-failed)]" />
            Dead-lettered
          </p>
          <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">{deadCount}</p>
          <p className="mt-1 text-xs text-slate-500">{totalDeliveries} total events</p>
        </div>
      </section>

      {notice && (
        <div role="status" className="rounded-xl bg-emerald-50 px-4 py-3 text-sm text-emerald-800 ring-1 ring-emerald-200">
          {notice}
        </div>
      )}

      {error && (
        <div role="status" className="rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-800 ring-1 ring-amber-200">
          {error}
        </div>
      )}

      {/* Bento Grid: Register Endpoint + Active Endpoints */}
      <div className="grid gap-5 lg:grid-cols-3">
        <form
          onSubmit={handleRegister}
          className="space-y-4 rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200"
        >
          <div>
            <h2 className="font-semibold text-slate-900">Register webhook endpoint</h2>
            <p className="text-xs text-slate-500">Generates a fresh 256-bit HMAC signing secret</p>
          </div>

          <div>
            <label htmlFor="wh-url" className="block text-xs font-medium text-slate-700">
              HTTPS callback URL *
            </label>
            <input
              id="wh-url"
              type="url"
              required
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              placeholder="https://store.example.lk/webhooks/swiftdrop"
              className="input mt-1 font-mono text-xs"
            />
          </div>

          <fieldset className="space-y-2">
            <legend className="text-xs font-medium text-slate-700">Subscribed events *</legend>
            {WEBHOOK_EVENTS.map((ev) => (
              <label
                key={ev.code}
                className="flex cursor-pointer items-start gap-2.5 rounded-xl border border-slate-200 p-2.5 text-xs hover:bg-slate-50"
              >
                <input
                  type="checkbox"
                  checked={selectedEvents.includes(ev.code)}
                  onChange={() => toggleEvent(ev.code)}
                  className="mt-0.5 h-4 w-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500"
                />
                <div>
                  <span className="font-mono font-semibold text-slate-900">{ev.label}</span>
                  <p className="mt-0.5 text-slate-500">{ev.description}</p>
                </div>
              </label>
            ))}
          </fieldset>

          <button
            type="submit"
            disabled={submitting || !url.trim() || selectedEvents.length === 0}
            className="btn-primary w-full py-2 text-xs"
          >
            {submitting ? "Registering..." : "Create endpoint"}
          </button>
        </form>

        <section className="rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200 lg:col-span-2">
          <h2 className="font-semibold text-slate-900">Registered endpoints</h2>
          <p className="text-xs text-slate-500">Send a signed webhook.test ping to verify your receiver</p>

          {endpoints.length === 0 ? (
            <div className="mt-6 rounded-xl bg-slate-50 px-6 py-10 text-center text-xs text-slate-500 ring-1 ring-slate-200/70">
              No webhook endpoints registered yet.
            </div>
          ) : (
            <ul className="mt-4 divide-y divide-slate-100">
              {endpoints.map((ep) => (
                <li key={ep.id} className="flex flex-wrap items-center justify-between gap-3 py-3.5">
                  <div className="min-w-0">
                    <p className="truncate font-mono text-xs font-semibold text-slate-900">{ep.url}</p>
                    <div className="mt-1 flex flex-wrap gap-1.5">
                      {ep.eventTypes.map((ev) => (
                        <span key={ev} className="status-pill-neutral font-mono">
                          {ev}
                        </span>
                      ))}
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => handleTestPing(ep.id)}
                      className="rounded-lg bg-brand-50 px-3 py-1 text-xs font-semibold text-brand-700 hover:bg-brand-100"
                    >
                      Send test ping
                    </button>
                    <button
                      type="button"
                      onClick={() => handleRemove(ep.id)}
                      className="rounded-lg px-2.5 py-1 text-xs font-medium text-red-700 ring-1 ring-red-200 hover:bg-red-50"
                    >
                      Remove
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      {/* Delivery Log Table */}
      <section className="overflow-hidden rounded-2xl bg-white shadow-sm ring-1 ring-slate-200">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 px-5 py-4">
          <div>
            <h2 className="font-semibold text-slate-900">Webhook delivery log</h2>
            <p className="text-xs text-slate-500">Every HTTP POST attempt with latency and response status</p>
          </div>

          <div role="group" aria-label="Filter deliveries" className="inline-flex rounded-xl bg-slate-100 p-1">
            {(["ALL", "DELIVERED", "RETRYING", "DEAD", "PENDING"] as const).map((st) => (
              <button
                key={st}
                type="button"
                onClick={() => setStatusFilter(st)}
                className={`rounded-lg px-2.5 py-1 text-xs font-medium transition ${
                  statusFilter === st ? "bg-white text-slate-900 shadow-xs" : "text-slate-600 hover:text-slate-900"
                }`}
              >
                {st}
              </button>
            ))}
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-slate-600">
            <thead className="border-b border-slate-200 bg-slate-50/80 text-xs font-semibold uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-5 py-3">Event</th>
                <th className="px-4 py-3">Endpoint URL</th>
                <th className="px-4 py-3">Attempts</th>
                <th className="px-4 py-3">Last response</th>
                <th className="px-5 py-3 text-right">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {deliveries.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-5 py-10 text-center text-xs text-slate-500">
                    No webhook delivery records match the current filter.
                  </td>
                </tr>
              ) : (
                deliveries.map((d) => {
                  const last = d.attempts[d.attempts.length - 1];
                  return (
                    <tr key={d.id} className="hover:bg-slate-50/70">
                      <td className="px-5 py-3.5 font-mono text-xs font-semibold text-slate-900">{d.eventType}</td>
                      <td className="max-w-[260px] truncate px-4 py-3.5 font-mono text-xs text-slate-600">{d.url}</td>
                      <td className="px-4 py-3.5 tabular-nums text-xs font-semibold text-slate-900">
                        {d.attemptCount}
                      </td>
                      <td className="px-4 py-3.5 text-xs text-slate-500">
                        {last
                          ? last.error || `HTTP ${last.httpStatus ?? "ERR"} (${last.latencyMs}ms)`
                          : "Pending dispatch"}
                      </td>
                      <td className="px-5 py-3.5 text-right">
                        <span
                          className={
                            d.status === "DELIVERED"
                              ? "status-pill-success"
                              : d.status === "DEAD"
                              ? "rounded-full bg-red-50 px-2.5 py-0.5 text-xs font-semibold text-red-700"
                              : "status-pill-warning"
                          }
                        >
                          {d.status}
                        </span>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
