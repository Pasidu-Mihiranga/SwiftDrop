import Link from "next/link";
import { auth } from "@/auth";
import { LiveToasts } from "@/components/tracking/live-toasts";
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

export const metadata = { title: "My Deliveries" };

interface PageProps {
  searchParams: Promise<{
    status?: string;
    search?: string;
    page?: string;
    size?: string;
  }>;
}

const STATUS_FILTERS: { value: string; label: string }[] = [
  { value: "ALL", label: "All" },
  { value: "CREATED", label: "Created" },
  { value: "PICKED_UP", label: "Picked up" },
  { value: "IN_TRANSIT", label: "In transit" },
  { value: "OUT_FOR_DELIVERY", label: "Out for delivery" },
  { value: "DELIVERED", label: "Delivered" },
  { value: "FAILED", label: "Failed" },
  { value: "CANCELLED", label: "Cancelled" },
];

export default async function CustomerDashboardPage({ searchParams }: PageProps) {
  const session = await auth();
  const params = await searchParams;

  const currentStatus = params.status && params.status !== "ALL" ? params.status : undefined;
  const searchQuery = params.search?.trim().toLowerCase() ?? "";
  const currentPage = Math.max(0, parseInt(params.page ?? "0", 10) || 0);
  const pageSize = Math.min(50, Math.max(1, parseInt(params.size ?? "10", 10) || 10));

  let parcels: ParcelResponse[] = [];
  let totalElements = 0;
  let totalPages = 1;
  let serviceError: string | null = null;

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
      serviceError = `Could not load parcels (HTTP ${res.status})`;
    }
  } catch {
    serviceError = "Parcel service is unreachable. Ensure the service is running.";
  }

  if (searchQuery && parcels.length > 0) {
    parcels = parcels.filter(
      (p) =>
        p.trackingNumber.toLowerCase().includes(searchQuery) ||
        p.recipient.name.toLowerCase().includes(searchQuery) ||
        p.dropoff.city.toLowerCase().includes(searchQuery) ||
        p.dropoff.district.toLowerCase().includes(searchQuery),
    );
  }

  const deliveredCount = parcels.filter((p) => p.status === "DELIVERED").length;
  const activeCount = parcels.filter((p) =>
    ["CREATED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY"].includes(p.status),
  ).length;
  const failedCount = parcels.filter((p) => p.status === "FAILED").length;
  const cancelledCount = parcels.filter((p) => p.status === "CANCELLED").length;
  const completedAttempts = deliveredCount + failedCount;
  const completionRate = completedAttempts > 0 ? Math.round((deliveredCount / completedAttempts) * 1000) / 10 : 100;

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
            <span className="text-slate-700">Customer portal</span>
          </nav>
          <h1 className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">My deliveries</h1>
          <p className="mt-1 text-sm text-slate-600">
            {session?.user?.name || session?.user?.email || "Customer"} &middot; Live tracking &amp; receipts &middot; Sri Lanka time
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Link href="/customer/notifications/preferences" className="btn-secondary py-2 text-xs">
            Alert preferences
          </Link>
          <Link href="/customer/history" className="btn-secondary py-2 text-xs">
            History &amp; receipts
          </Link>
          <Link href="/customer/book" className="btn-primary py-2 text-xs">
            + Book new delivery
          </Link>
        </div>
      </header>

      {/* KPI Row matching PR #36 (.viz-root) */}
      <section aria-label="Delivery summary" className="viz-root grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-6">
        <div className="relative col-span-2 overflow-hidden rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
          <div className="pointer-events-none absolute -right-10 -top-10 h-36 w-36 rounded-full bg-brand-50" aria-hidden="true" />
          <p className="relative text-sm font-medium text-slate-600">Delivery completion</p>
          <p className="relative mt-1 text-4xl font-semibold tracking-tight text-slate-900 sm:text-5xl">
            {completedAttempts > 0 ? `${completionRate}%` : `${totalElements}`}
          </p>
          <p className="relative mt-1 text-xs text-slate-500">
            {completedAttempts > 0 ? `${deliveredCount} of ${completedAttempts} completed shipments delivered` : "Total bookings on your account"}
          </p>
          <div className="relative mt-4 h-2.5 overflow-hidden rounded-full bg-[var(--viz-track)]">
            <div
              className="viz-grow h-full rounded-full bg-[var(--viz-delivered)]"
              style={{ width: `${completedAttempts > 0 ? completionRate : Math.min(100, totalElements * 20)}%` }}
            />
          </div>
        </div>

        <div className="flex min-w-0 flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-created)]" />
            Active in progress
          </p>
          <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">{activeCount}</p>
          <p className="mt-2 text-xs text-slate-500">Awaiting pickup or in transit</p>
        </div>

        <div className="flex min-w-0 flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-delivered)]" />
            Delivered
          </p>
          <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">{deliveredCount}</p>
          <p className="mt-2 text-xs text-slate-500">PDF receipts available</p>
        </div>

        <div className="flex min-w-0 flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-failed)]" />
            Exceptions / Cancelled
          </p>
          <p className="mt-1 text-2xl font-semibold tracking-tight text-slate-900 sm:text-3xl">
            {failedCount + cancelledCount}
          </p>
          <p className="mt-2 text-xs text-slate-500">
            {failedCount} failed &middot; {cancelledCount} cancelled
          </p>
        </div>

        <div className="flex min-w-0 flex-col justify-between rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
          <p className="flex items-center gap-2 text-sm font-medium text-slate-600">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-[var(--viz-cancelled)]" />
            Address book
          </p>
          <p className="mt-1 text-lg font-semibold tracking-tight text-slate-900">Saved locations</p>
          <Link href="/account" className="mt-2 text-xs font-medium text-brand-700 hover:underline">
            Manage profile &amp; addresses &rarr;
          </Link>
        </div>
      </section>

      {/* Filter Bar matching PR #36 */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div role="tablist" aria-label="Parcel status" className="inline-flex flex-wrap rounded-xl bg-white p-1 shadow-sm ring-1 ring-slate-200">
          {STATUS_FILTERS.map((filter) => {
            const active = (currentStatus ?? "ALL") === filter.value;
            const nextUrl = new URLSearchParams();
            if (filter.value !== "ALL") nextUrl.set("status", filter.value);
            if (searchQuery) nextUrl.set("search", searchQuery);
            const qs = nextUrl.toString();
            return (
              <Link
                key={filter.value}
                href={qs ? `/customer?${qs}` : "/customer"}
                className={`rounded-lg px-3 py-1.5 text-xs font-medium transition sm:text-sm ${
                  active ? "bg-brand-600 text-white shadow-sm" : "text-slate-600 hover:bg-slate-100 hover:text-slate-900"
                }`}
              >
                {filter.label}
              </Link>
            );
          })}
        </div>

        <form method="GET" action="/customer" className="flex items-center gap-2">
          {currentStatus && <input type="hidden" name="status" value={currentStatus} />}
          <input
            type="text"
            name="search"
            defaultValue={searchQuery}
            placeholder="Find tracking #, recipient, city..."
            className="rounded-xl border border-slate-200 bg-white px-3 py-1.5 text-sm shadow-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-100"
          />
          <button type="submit" className="btn-secondary py-1.5 text-xs">
            Filter
          </button>
          {searchQuery && (
            <Link
              href={`/customer${currentStatus ? `?status=${currentStatus}` : ""}`}
              className="text-xs font-medium text-slate-500 hover:text-slate-800"
            >
              Reset
            </Link>
          )}
        </form>
      </div>

      {serviceError && (
        <p role="status" className="flex items-center gap-2 rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-800 ring-1 ring-amber-200">
          {serviceError}
        </p>
      )}

      {/* Parcels Table Card */}
      <section className="overflow-hidden rounded-2xl bg-white shadow-sm ring-1 ring-slate-200">
        <div className="flex items-center justify-between border-b border-slate-100 px-5 py-4">
          <div>
            <h2 className="font-semibold text-slate-900">Recent shipments</h2>
            <p className="text-xs text-slate-500">Click a tracking number for parcel details, cancellation, or official PDF receipt</p>
          </div>
          <Link href="/quote" className="text-xs font-medium text-brand-700 hover:underline">
            Estimate a fee &rarr;
          </Link>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-slate-600">
            <thead className="border-b border-slate-200 bg-slate-50/80 text-xs font-semibold uppercase tracking-wide text-slate-500">
              <tr>
                <th scope="col" className="px-5 py-3">Tracking number</th>
                <th scope="col" className="px-4 py-3">Status</th>
                <th scope="col" className="px-4 py-3">Destination &amp; recipient</th>
                <th scope="col" className="px-4 py-3">Specs</th>
                <th scope="col" className="px-4 py-3">Fee</th>
                <th scope="col" className="px-4 py-3">Booked</th>
                <th scope="col" className="px-5 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {parcels.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-6 py-14 text-center">
                    <div className="mx-auto max-w-sm space-y-3">
                      <p className="font-semibold text-slate-900">No deliveries in this view</p>
                      <p className="text-xs text-slate-500">
                        {currentStatus
                          ? `No parcels match status "${currentStatus}".`
                          : "You have not booked any parcels yet. Create a delivery with an instant fee quote."}
                      </p>
                      <div className="flex justify-center gap-2 pt-1">
                        {currentStatus && (
                          <Link href="/customer" className="btn-secondary py-1.5 text-xs">
                            Show all statuses
                          </Link>
                        )}
                        <Link href="/customer/book" className="btn-primary py-1.5 text-xs">
                          + Book delivery
                        </Link>
                      </div>
                    </div>
                  </td>
                </tr>
              ) : (
                parcels.map((parcel) => (
                  <tr key={parcel.id} className="transition-colors hover:bg-slate-50/70">
                    <td className="px-5 py-3.5 font-mono font-semibold text-slate-900">
                      <Link href={`/customer/parcels/${parcel.id}`} className="text-brand-700 hover:underline">
                        {parcel.trackingNumber}
                      </Link>
                    </td>
                    <td className="px-4 py-3.5">
                      <span
                        className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold ${statusBadgeClass(
                          parcel.status as ParcelStatus,
                        )}`}
                      >
                        {statusLabel(parcel.status as ParcelStatus)}
                      </span>
                    </td>
                    <td className="px-4 py-3.5">
                      <div className="font-medium text-slate-900">{parcel.recipient.name}</div>
                      <div className="text-xs text-slate-500">
                        {parcel.dropoff.city}, {parcel.dropoff.district}
                      </div>
                    </td>
                    <td className="px-4 py-3.5 text-xs">
                      <span className="font-medium text-slate-800">{parcel.weightKg} kg</span>
                      <span className="text-slate-400"> &middot; </span>
                      <span>Size {parcel.sizeClass}</span>
                      <span className="text-slate-400"> &middot; </span>
                      <span className="text-slate-500">{parcel.serviceLevel}</span>
                    </td>
                    <td className="px-4 py-3.5 font-semibold tabular-nums text-slate-900">
                      {formatMoney(parcel.fee.amount, parcel.fee.currency)}
                    </td>
                    <td className="px-4 py-3.5 text-xs text-slate-500">
                      {new Date(parcel.createdAt).toLocaleDateString("en-GB", {
                        year: "numeric",
                        month: "short",
                        day: "numeric",
                      })}
                    </td>
                    <td className="px-5 py-3.5 text-right">
                      <div className="flex items-center justify-end gap-2">
                        <Link
                          href={`/track/${parcel.trackingNumber}`}
                          className="rounded-lg px-2.5 py-1 text-xs font-medium text-brand-700 ring-1 ring-slate-200 hover:bg-slate-50"
                        >
                          Live track
                        </Link>
                        <a
                          href={`/api/bff/api/v1/parcels/${parcel.id}/receipt`}
                          download={`receipt-${parcel.trackingNumber}.pdf`}
                          className="rounded-lg bg-brand-50 px-2.5 py-1 text-xs font-semibold text-brand-700 hover:bg-brand-100"
                        >
                          Receipt
                        </a>
                        <Link
                          href={`/customer/parcels/${parcel.id}`}
                          className="rounded-lg px-2.5 py-1 text-xs font-medium text-slate-700 ring-1 ring-slate-200 hover:bg-slate-50"
                        >
                          Details
                        </Link>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-slate-100 bg-slate-50/50 px-5 py-3 text-xs text-slate-600">
            <span>
              Page <strong className="text-slate-900">{currentPage + 1}</strong> of{" "}
              <strong className="text-slate-900">{totalPages}</strong> ({totalElements} total parcels)
            </span>
            <div className="flex gap-2">
              <Link
                href={`/customer?page=${Math.max(0, currentPage - 1)}${
                  currentStatus ? `&status=${currentStatus}` : ""
                }${searchQuery ? `&search=${searchQuery}` : ""}`}
                className={`btn-secondary px-3 py-1 text-xs ${
                  currentPage === 0 ? "pointer-events-none opacity-50" : ""
                }`}
              >
                &larr; Previous
              </Link>
              <Link
                href={`/customer?page=${Math.min(totalPages - 1, currentPage + 1)}${
                  currentStatus ? `&status=${currentStatus}` : ""
                }${searchQuery ? `&search=${searchQuery}` : ""}`}
                className={`btn-secondary px-3 py-1 text-xs ${
                  currentPage >= totalPages - 1 ? "pointer-events-none opacity-50" : ""
                }`}
              >
                Next &rarr;
              </Link>
            </div>
          </div>
        )}
      </section>
    </div>
  );
}
