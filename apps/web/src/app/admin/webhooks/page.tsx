"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";

interface WebhookAttempt {
  at: string;
  httpStatus?: number;
  latencyMs: number;
  error?: string;
}

interface WebhookDelivery {
  id: string;
  endpointId: string;
  merchantId: string;
  eventId: string;
  eventType: string;
  url: string;
  status: "PENDING" | "DELIVERED" | "RETRYING" | "DEAD";
  attemptCount: number;
  attempts: WebhookAttempt[];
  nextRetryAt: string | null;
  createdAt: string;
  deliveredAt: string | null;
  deadLetteredAt: string | null;
}

export default function AdminWebhooksPage() {
  const [deliveries, setDeliveries] = useState<WebhookDelivery[]>([]);
  const [total, setTotal] = useState(0);
  const [statusFilter, setStatusFilter] = useState<"ALL" | "DEAD" | "RETRYING" | "DELIVERED" | "PENDING">("ALL");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [replayingId, setReplayingId] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const loadDeliveries = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const qs = statusFilter !== "ALL" ? `?status=${statusFilter}&page=0&size=50` : "?page=0&size=50";
      const res = await fetch(`/api/bff/api/v1/admin/webhooks/deliveries${qs}`);
      if (res.ok) {
        const data = await res.json();
        const items: WebhookDelivery[] = data.content ?? [];
        setDeliveries(items);
        setTotal(data.page?.totalElements ?? items.length);
      } else {
        setError(`Could not load webhook deliveries (HTTP ${res.status})`);
      }
    } catch {
      setError("Webhook service is unreachable. Ensure webhook-service is running.");
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    loadDeliveries();
  }, [loadDeliveries]);

  async function handleReplay(id: string) {
    setReplayingId(id);
    setNotice(null);
    try {
      const res = await fetch(`/api/bff/api/v1/admin/webhooks/deliveries/${id}/replay`, {
        method: "POST",
      });
      if (res.ok) {
        setNotice(`Delivery ${id.slice(0, 8)} queued for immediate replay.`);
        await loadDeliveries();
      } else {
        const problem = await res.json().catch(() => ({}));
        setError(problem.detail || `Replay failed (HTTP ${res.status})`);
      }
    } catch {
      setError("Network error while replaying dead-lettered webhook.");
    } finally {
      setReplayingId(null);
    }
  }

  const deadCount = deliveries.filter((d) => d.status === "DEAD").length;
  const deliveredCount = deliveries.filter((d) => d.status === "DELIVERED").length;
  const retryingCount = deliveries.filter((d) => d.status === "RETRYING").length;
  const successRate =
    deliveredCount + deadCount > 0 ? Math.round((deliveredCount / (deliveredCount + deadCount)) * 1000) / 10 : 100;

  return (
    <div className="space-y-5">
      {/* Header matching PR #36 */}
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <nav aria-label="Breadcrumb" className="text-xs text-slate-500">
            <Link href="/admin" className="hover:text-slate-800">
              Operations
            </Link>
            <span className="mx-1.5 text-slate-300">/</span>
            <span className="text-slate-700">Webhooks DLQ</span>
          </nav>
          <h1 className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">
            Webhook delivery log &amp; dead-letter replay
          </h1>
          <p className="mt-1 text-sm text-slate-600">
            M8 Webhook Service &middot; BullMQ exponential backoff &middot; Dead-letter queue replay
          </p>
        </div>
        <button type="button" onClick={loadDeliveries} disabled={loading} className="btn-secondary py-2 text-xs">
          {loading ? "Refreshing..." : "Refresh deliveries"}
        </button>
      </header>

      {/* KPI Row matching PR #36 (.viz-root) */}
      <section aria-label="Webhook summary" className="viz-root grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-6">
        <div className="relative col-span-2 overflow-hidden rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
          <div className="pointer-events-none absolute -right-10 -top-10 h-36 w-36 rounded-full bg-brand-50" aria-hidden="true" />
          <p className="relative text-sm font-medium text-slate-600">Delivery reliability</p>
          <p className="relative mt-1 text-4xl font-semibold tracking-tight text-slate-900 sm:text-5xl">
            {successRate}%
          </p>
          <p className="relative mt-1 text-xs text-slate-500">
            {deliveredCount} delivered vs {deadCount} dead-lettered in current view
          </p>
          <div className="relative mt-4 h-2.5 overflow-hidden rounded-full bg-[var(--viz-track)]">
            <div className="viz-grow h-full rounded-full bg-[var(--viz-delivered)]" style={{ width: `${successRate}%` }} />
          </div>
        </div>

        <div className="flex min-w-0 flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-created)]" />
            Total logged
          </p>
          <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">{total}</p>
          <p className="mt-2 text-xs text-slate-500">Across all merchants</p>
        </div>

        <div className="flex min-w-0 flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-delivered)]" />
            Delivered
          </p>
          <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">{deliveredCount}</p>
          <p className="mt-2 text-xs text-slate-500">2xx response received</p>
        </div>

        <div className="flex min-w-0 flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-cancelled)]" />
            Retrying
          </p>
          <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">{retryingCount}</p>
          <p className="mt-2 text-xs text-slate-500">Scheduled in BullMQ</p>
        </div>

        <div className="flex min-w-0 flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-failed)]" />
            Dead-lettered
          </p>
          <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">{deadCount}</p>
          <p className="mt-2 text-xs text-slate-500">Ready for manual replay</p>
        </div>
      </section>

      {/* Filter Bar */}
      <div className="flex flex-wrap items-center gap-2">
        <div role="group" aria-label="Webhook status filter" className="inline-flex flex-wrap rounded-xl bg-white p-1 shadow-sm ring-1 ring-slate-200">
          {(["ALL", "DEAD", "RETRYING", "DELIVERED", "PENDING"] as const).map((status) => (
            <button
              key={status}
              type="button"
              onClick={() => setStatusFilter(status)}
              className={`rounded-lg px-3 py-1.5 text-xs font-medium transition sm:text-sm ${
                statusFilter === status
                  ? "bg-brand-600 text-white shadow-sm"
                  : "text-slate-600 hover:bg-slate-100 hover:text-slate-900"
              }`}
            >
              {status === "ALL" ? "All deliveries" : status === "DEAD" ? "Dead-lettered (DLQ)" : status}
            </button>
          ))}
        </div>
      </div>

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

      {/* Deliveries Table */}
      <section className="overflow-hidden rounded-2xl bg-white shadow-sm ring-1 ring-slate-200">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-slate-600">
            <thead className="border-b border-slate-200 bg-slate-50/80 text-xs font-semibold uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-5 py-3">Delivery / Merchant</th>
                <th className="px-4 py-3">Event type</th>
                <th className="px-4 py-3">Target URL</th>
                <th className="px-4 py-3">Attempts</th>
                <th className="px-4 py-3">Last error / HTTP</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-5 py-3 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? (
                <tr>
                  <td colSpan={7} className="px-5 py-12 text-center text-slate-500">
                    Loading webhook deliveries...
                  </td>
                </tr>
              ) : deliveries.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-5 py-12 text-center text-slate-500">
                    No webhook deliveries match the selected filter.
                  </td>
                </tr>
              ) : (
                deliveries.map((d) => {
                  const lastAttempt = d.attempts[d.attempts.length - 1];
                  return (
                    <tr key={d.id} className="hover:bg-slate-50/70">
                      <td className="px-5 py-3.5 text-xs">
                        <p className="font-mono font-semibold text-slate-900">{d.id.slice(0, 12)}</p>
                        <p className="font-mono text-slate-400">Merchant: {d.merchantId.slice(0, 8)}</p>
                      </td>
                      <td className="px-4 py-3.5 font-mono text-xs font-semibold text-slate-800">{d.eventType}</td>
                      <td className="max-w-[240px] truncate px-4 py-3.5 font-mono text-xs text-slate-600">{d.url}</td>
                      <td className="px-4 py-3.5 tabular-nums text-xs font-semibold text-slate-900">
                        {d.attemptCount}
                      </td>
                      <td className="px-4 py-3.5 text-xs text-slate-500">
                        {lastAttempt
                          ? lastAttempt.error || `HTTP ${lastAttempt.httpStatus ?? "ERR"} (${lastAttempt.latencyMs}ms)`
                          : "Queued"}
                      </td>
                      <td className="px-4 py-3.5">
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
                      <td className="px-5 py-3.5 text-right">
                        {d.status === "DEAD" ? (
                          <button
                            type="button"
                            disabled={replayingId === d.id}
                            onClick={() => handleReplay(d.id)}
                            className="btn-primary px-3 py-1 text-xs"
                          >
                            {replayingId === d.id ? "Replaying..." : "Replay"}
                          </button>
                        ) : (
                          <span className="text-xs text-slate-400">&mdash;</span>
                        )}
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
