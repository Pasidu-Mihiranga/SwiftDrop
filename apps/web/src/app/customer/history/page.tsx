import Link from "next/link";
import { auth } from "@/auth";
import { gatewayFetch } from "@/lib/gateway";
import {
  extractParcelPageMeta,
  formatMoney,
  statusBadgeClass,
  statusLabel,
  type ParcelPageResponse,
  type ParcelResponse,
  type ParcelStatus,
} from "@/lib/parcels";

export const metadata = {
  title: "Delivery History & Receipts",
  description: "Browse past deliveries, check status timelines and download official receipts.",
};

interface PageProps {
  searchParams: Promise<{
    status?: string;
    search?: string;
    page?: string;
    size?: string;
    from?: string;
    to?: string;
  }>;
}

export default async function DeliveryHistoryPage({ searchParams }: PageProps) {
  const session = await auth();
  const params = await searchParams;

  const currentStatus = params.status && params.status !== "ALL" ? params.status : undefined;
  const searchQuery = params.search?.trim().toLowerCase() ?? "";
  const currentPage = Math.max(0, parseInt(params.page ?? "0", 10) || 0);
  const pageSize = Math.min(50, Math.max(1, parseInt(params.size ?? "10", 10) || 10));

  let parcels: ParcelResponse[] = [];
  let totalElements = 0;
  let totalPages = 1;
  let error: string | null = null;

  try {
    const queryParts = [`page=${currentPage}`, `size=${pageSize}`];
    if (currentStatus) {
      queryParts.push(`status=${currentStatus}`);
    }
    const res = await gatewayFetch(`/api/v1/parcels/mine?${queryParts.join("&")}`);
    if (res.ok) {
      const data = (await res.json()) as ParcelPageResponse;
      parcels = data.content || [];
      const meta = extractParcelPageMeta(data, parcels.length);
      totalElements = meta.totalElements;
      totalPages = meta.totalPages;
    } else {
      error = `Unable to fetch deliveries (HTTP ${res.status})`;
    }
  } catch {
    error = "Could not reach parcel-service. Ensure services are running.";
  }

  if (searchQuery) {
    parcels = parcels.filter(
      (p) =>
        p.trackingNumber.toLowerCase().includes(searchQuery) ||
        p.recipient.name.toLowerCase().includes(searchQuery) ||
        p.dropoff.city.toLowerCase().includes(searchQuery),
    );
  }

  if (params.from) {
    const fromTime = new Date(params.from).getTime();
    if (!isNaN(fromTime)) {
      parcels = parcels.filter((p) => new Date(p.createdAt).getTime() >= fromTime);
    }
  }

  if (params.to) {
    const toTime = new Date(params.to).getTime() + 86400000;
    if (!isNaN(toTime)) {
      parcels = parcels.filter((p) => new Date(p.createdAt).getTime() <= toTime);
    }
  }

  const deliveredCount = parcels.filter((p) => p.status === "DELIVERED").length;
  const activeCount = parcels.filter((p) =>
    ["CREATED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY"].includes(p.status),
  ).length;
  const totalSpend = parcels.reduce((sum, p) => sum + (Number(p.fee?.amount) || 0), 0);

  return (
    <div className="space-y-5">
      {/* Header matching PR #36 */}
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <nav aria-label="Breadcrumb" className="text-xs text-slate-500">
            <Link href="/customer" className="hover:text-slate-800">
              My deliveries
            </Link>
            <span className="mx-1.5 text-slate-300">/</span>
            <span className="text-slate-700">History &amp; receipts</span>
          </nav>
          <h1 className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">
            Delivery history &amp; receipts
          </h1>
          <p className="mt-1 text-sm text-slate-600">
            {session?.user.name ?? session?.user.email ?? "Customer"} &middot; Official OpenPDF tax receipts &middot; Sri Lanka time
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Link href="/customer/notifications/preferences" className="btn-secondary py-2 text-xs">
            Notification settings
          </Link>
          <Link href="/customer/book" className="btn-primary py-2 text-xs">
            + Book delivery
          </Link>
        </div>
      </header>

      {/* Summary KPI Row matching PR #36 */}
      <section aria-label="History summary" className="viz-root grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
        <div className="flex flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-created)]" />
            Total shipments
          </p>
          <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">{totalElements}</p>
          <p className="mt-1 text-xs text-slate-500">All recorded bookings</p>
        </div>

        <div className="flex flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-delivered)]" />
            Delivered in view
          </p>
          <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">{deliveredCount}</p>
          <p className="mt-1 text-xs text-slate-500">Completed with receipt</p>
        </div>

        <div className="flex flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-cancelled)]" />
            Active in progress
          </p>
          <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">{activeCount}</p>
          <p className="mt-1 text-xs text-slate-500">Currently moving through hubs</p>
        </div>

        <div className="flex flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-brand-600" />
            Page spend
          </p>
          <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">
            {formatMoney(totalSpend, "LKR")}
          </p>
          <p className="mt-1 text-xs text-slate-500">Sum of visible shipments</p>
        </div>
      </section>

      {/* Filter Card */}
      <form method="GET" className="rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200 sm:p-5">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-5">
          <div className="lg:col-span-2">
            <label htmlFor="search" className="mb-1 block text-xs font-medium text-slate-600">
              Tracking number or recipient
            </label>
            <input
              id="search"
              name="search"
              type="text"
              placeholder="e.g. SD2611..., Sunimal"
              defaultValue={params.search ?? ""}
              className="input text-sm"
            />
          </div>

          <div>
            <label htmlFor="status" className="mb-1 block text-xs font-medium text-slate-600">
              Status
            </label>
            <select id="status" name="status" defaultValue={params.status ?? "ALL"} className="input bg-white text-sm">
              <option value="ALL">All statuses</option>
              <option value="CREATED">Created</option>
              <option value="PICKED_UP">Picked up</option>
              <option value="IN_TRANSIT">In transit</option>
              <option value="OUT_FOR_DELIVERY">Out for delivery</option>
              <option value="DELIVERED">Delivered</option>
              <option value="FAILED">Failed</option>
              <option value="CANCELLED">Cancelled</option>
            </select>
          </div>

          <div>
            <label htmlFor="from" className="mb-1 block text-xs font-medium text-slate-600">
              From date
            </label>
            <input id="from" name="from" type="date" defaultValue={params.from ?? ""} className="input text-sm" />
          </div>

          <div>
            <label htmlFor="to" className="mb-1 block text-xs font-medium text-slate-600">
              To date
            </label>
            <input id="to" name="to" type="date" defaultValue={params.to ?? ""} className="input text-sm" />
          </div>
        </div>

        <div className="mt-4 flex items-center justify-end gap-2 border-t border-slate-100 pt-3">
          <Link href="/customer/history" className="btn-secondary py-1.5 text-xs">
            Reset filters
          </Link>
          <button type="submit" className="btn-primary py-1.5 text-xs">
            Apply filters
          </button>
        </div>
      </form>

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
                <th className="px-5 py-3">Tracking #</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Route (Origin &rarr; Destination)</th>
                <th className="px-4 py-3">Recipient</th>
                <th className="px-4 py-3">Fee</th>
                <th className="px-4 py-3">Booked</th>
                <th className="px-5 py-3 text-right">Receipt &amp; actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {parcels.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-4 py-14 text-center text-slate-500">
                    <div className="mx-auto max-w-sm space-y-2">
                      <p className="font-semibold text-slate-900">No deliveries found</p>
                      <p className="text-xs text-slate-500">
                        {searchQuery || currentStatus
                          ? "No parcels match the selected filters."
                          : "You have not placed any delivery bookings yet."}
                      </p>
                    </div>
                  </td>
                </tr>
              ) : (
                parcels.map((parcel) => {
                  const dateStr = new Date(parcel.createdAt).toLocaleDateString("en-GB", {
                    year: "numeric",
                    month: "short",
                    day: "numeric",
                  });

                  return (
                    <tr key={parcel.id} className="transition-colors hover:bg-slate-50/70">
                      <td className="px-5 py-3.5 font-mono font-semibold text-slate-900">
                        <Link href={`/customer/parcels/${parcel.id}`} className="text-brand-700 hover:underline">
                          {parcel.trackingNumber}
                        </Link>
                      </td>
                      <td className="px-4 py-3.5">
                        <span
                          className={`inline-flex rounded-full border px-2.5 py-0.5 text-xs font-semibold ${statusBadgeClass(
                            parcel.status as ParcelStatus,
                          )}`}
                        >
                          {statusLabel(parcel.status as ParcelStatus)}
                        </span>
                      </td>
                      <td className="px-4 py-3.5">
                        <div className="text-xs">
                          <span className="font-semibold text-slate-800">{parcel.pickup.city}</span>
                          <span className="mx-1 text-slate-400">&rarr;</span>
                          <span className="font-semibold text-slate-800">{parcel.dropoff.city}</span>
                        </div>
                        <p className="max-w-[200px] truncate text-xs text-slate-400">{parcel.dropoff.line1}</p>
                      </td>
                      <td className="px-4 py-3.5">
                        <p className="font-medium text-slate-900">{parcel.recipient.name}</p>
                        <p className="text-xs text-slate-500">{parcel.recipient.phone}</p>
                      </td>
                      <td className="px-4 py-3.5 font-semibold tabular-nums text-slate-900">
                        {formatMoney(Number(parcel.fee.amount), parcel.fee.currency)}
                      </td>
                      <td className="px-4 py-3.5 text-xs text-slate-500">{dateStr}</td>
                      <td className="px-5 py-3.5 text-right">
                        <div className="flex items-center justify-end gap-2">
                          <Link
                            href={`/track/${parcel.trackingNumber}`}
                            className="rounded-lg px-2.5 py-1 text-xs font-medium text-slate-700 ring-1 ring-slate-200 hover:bg-slate-50"
                          >
                            Track
                          </Link>
                          <a
                            href={`/api/bff/api/v1/parcels/${parcel.id}/receipt`}
                            download={`receipt-${parcel.trackingNumber}.pdf`}
                            className="inline-flex items-center gap-1 rounded-lg bg-brand-50 px-2.5 py-1 text-xs font-semibold text-brand-700 hover:bg-brand-100"
                            title="Download official PDF delivery receipt"
                          >
                            <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                              <path
                                strokeLinecap="round"
                                strokeLinejoin="round"
                                strokeWidth={2}
                                d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
                              />
                            </svg>
                            PDF receipt
                          </a>
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        {totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-slate-100 bg-slate-50/50 px-5 py-3 text-xs text-slate-600">
            <div>
              Showing page <span className="font-semibold text-slate-900">{currentPage + 1}</span> of{" "}
              <span className="font-semibold text-slate-900">{totalPages}</span> ({totalElements} total parcels)
            </div>
            <div className="flex gap-2">
              {currentPage > 0 ? (
                <Link
                  href={{
                    pathname: "/customer/history",
                    query: { ...params, page: (currentPage - 1).toString() },
                  }}
                  className="btn-secondary px-3 py-1 text-xs"
                >
                  Previous
                </Link>
              ) : (
                <button disabled className="btn-secondary cursor-not-allowed px-3 py-1 text-xs opacity-40">
                  Previous
                </button>
              )}

              {currentPage + 1 < totalPages ? (
                <Link
                  href={{
                    pathname: "/customer/history",
                    query: { ...params, page: (currentPage + 1).toString() },
                  }}
                  className="btn-secondary px-3 py-1 text-xs"
                >
                  Next
                </Link>
              ) : (
                <button disabled className="btn-secondary cursor-not-allowed px-3 py-1 text-xs opacity-40">
                  Next
                </button>
              )}
            </div>
          </div>
        )}
      </section>
    </div>
  );
}
