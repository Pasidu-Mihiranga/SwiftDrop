import Link from "next/link";
import { auth } from "@/auth";
import { gatewayFetch } from "@/lib/gateway";

export const metadata = {
  title: "Delivery History & Receipts — SwiftDrop",
  description: "Browse past deliveries, check status timelines and download official receipts.",
};

interface ParcelResponse {
  id: string;
  trackingNumber: string;
  status: "CREATED" | "PICKED_UP" | "IN_TRANSIT" | "OUT_FOR_DELIVERY" | "DELIVERED" | "FAILED" | "CANCELLED";
  pickup: {
    line1: string;
    line2?: string;
    city: string;
    postalCode?: string;
  };
  dropoff: {
    line1: string;
    line2?: string;
    city: string;
    postalCode?: string;
  };
  recipient: {
    name: string;
    phone: string;
    email?: string;
  };
  weightKg: number;
  sizeClass: string;
  serviceLevel: string;
  fee: {
    amount: number;
    currency: string;
  };
  notes?: string;
  createdAt: string;
}

interface ParcelPageResponse {
  content: ParcelResponse[];
  page: {
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
  };
}

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

const STATUS_BADGES: Record<string, { bg: string; text: string; label: string }> = {
  CREATED: { bg: "bg-slate-100", text: "text-slate-700", label: "Created" },
  PICKED_UP: { bg: "bg-amber-50", text: "text-amber-700", label: "Picked Up" },
  IN_TRANSIT: { bg: "bg-blue-50", text: "text-blue-700", label: "In Transit" },
  OUT_FOR_DELIVERY: { bg: "bg-indigo-50", text: "text-indigo-700", label: "Out for Delivery" },
  DELIVERED: { bg: "bg-emerald-50", text: "text-emerald-700", label: "Delivered" },
  FAILED: { bg: "bg-rose-50", text: "text-rose-700", label: "Failed" },
  CANCELLED: { bg: "bg-slate-100", text: "text-slate-500", label: "Cancelled" },
};

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
      totalElements = data.page.totalElements;
      totalPages = data.page.totalPages;
    } else {
      error = `Unable to fetch deliveries (HTTP ${res.status})`;
    }
  } catch (err) {
    error = "Could not reach parcel-service. Ensure services are running.";
  }

  // Client-side filtering if search query or date range is provided
  if (searchQuery) {
    parcels = parcels.filter(
      (p) =>
        p.trackingNumber.toLowerCase().includes(searchQuery) ||
        p.recipient.name.toLowerCase().includes(searchQuery) ||
        p.dropoff.city.toLowerCase().includes(searchQuery)
    );
  }

  if (params.from) {
    const fromTime = new Date(params.from).getTime();
    if (!isNaN(fromTime)) {
      parcels = parcels.filter((p) => new Date(p.createdAt).getTime() >= fromTime);
    }
  }

  if (params.to) {
    const toTime = new Date(params.to).getTime() + 86400000; // include full day
    if (!isNaN(toTime)) {
      parcels = parcels.filter((p) => new Date(p.createdAt).getTime() <= toTime);
    }
  }

  const deliveredCount = parcels.filter((p) => p.status === "DELIVERED").length;
  const activeCount = parcels.filter((p) => ["CREATED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY"].includes(p.status)).length;

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <Link href="/customer" className="text-sm font-medium text-brand-600 hover:underline">
              &larr; Customer Dashboard
            </Link>
          </div>
          <h1 className="mt-1 text-2xl font-bold tracking-tight text-slate-900">Delivery History &amp; Receipts</h1>
          <p className="text-sm text-slate-500">
            Search previous shipments, follow live updates and download official PDF delivery receipts.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <Link href="/track" className="btn-secondary text-xs">
            Public Tracker
          </Link>
        </div>
      </div>

      {/* Summary KPI Cards */}
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <div className="card py-4">
          <p className="text-xs font-medium text-slate-500">Total Shipments</p>
          <p className="mt-1 text-2xl font-bold text-slate-900">{totalElements}</p>
        </div>
        <div className="card py-4">
          <p className="text-xs font-medium text-blue-600">Active Deliveries</p>
          <p className="mt-1 text-2xl font-bold text-blue-700">{activeCount}</p>
        </div>
        <div className="card py-4">
          <p className="text-xs font-medium text-emerald-600">Delivered</p>
          <p className="mt-1 text-2xl font-bold text-emerald-700">{deliveredCount}</p>
        </div>
        <div className="card py-4">
          <p className="text-xs font-medium text-slate-500">Signed In As</p>
          <p className="mt-1 truncate text-sm font-semibold text-slate-800">
            {session?.user.name ?? session?.user.email ?? "Customer"}
          </p>
        </div>
      </div>

      {/* Search and Filters Form */}
      <form method="GET" className="card space-y-4">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-5">
          {/* Search by tracking number */}
          <div className="lg:col-span-2">
            <label htmlFor="search" className="block text-xs font-medium text-slate-600 mb-1">
              Search by Tracking # or Recipient
            </label>
            <input
              id="search"
              name="search"
              type="text"
              placeholder="e.g. TRK-COL-2026-..., Saman"
              defaultValue={params.search ?? ""}
              className="input text-sm"
            />
          </div>

          {/* Status filter */}
          <div>
            <label htmlFor="status" className="block text-xs font-medium text-slate-600 mb-1">
              Status Filter
            </label>
            <select
              id="status"
              name="status"
              defaultValue={params.status ?? "ALL"}
              className="input text-sm bg-white"
            >
              <option value="ALL">All Statuses</option>
              <option value="CREATED">Created</option>
              <option value="PICKED_UP">Picked Up</option>
              <option value="IN_TRANSIT">In Transit</option>
              <option value="OUT_FOR_DELIVERY">Out for Delivery</option>
              <option value="DELIVERED">Delivered</option>
              <option value="FAILED">Failed</option>
              <option value="CANCELLED">Cancelled</option>
            </select>
          </div>

          {/* Date range From */}
          <div>
            <label htmlFor="from" className="block text-xs font-medium text-slate-600 mb-1">
              From Date
            </label>
            <input
              id="from"
              name="from"
              type="date"
              defaultValue={params.from ?? ""}
              className="input text-sm"
            />
          </div>

          {/* Date range To */}
          <div>
            <label htmlFor="to" className="block text-xs font-medium text-slate-600 mb-1">
              To Date
            </label>
            <input
              id="to"
              name="to"
              type="date"
              defaultValue={params.to ?? ""}
              className="input text-sm"
            />
          </div>
        </div>

        <div className="flex items-center justify-end gap-2 pt-2 border-t border-slate-100">
          <Link href="/customer/history" className="btn-secondary text-xs">
            Reset Filters
          </Link>
          <button type="submit" className="btn-primary text-xs">
            Apply Filters
          </button>
        </div>
      </form>

      {/* Error state */}
      {error && (
        <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
          <p className="font-semibold">Notice</p>
          <p className="mt-1">{error}</p>
        </div>
      )}

      {/* Deliveries Table / List */}
      <div className="card overflow-hidden p-0">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-slate-600">
            <thead className="border-b border-slate-200 bg-slate-50 text-xs font-semibold text-slate-700">
              <tr>
                <th className="px-4 py-3">Tracking #</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Route (Origin → Destination)</th>
                <th className="px-4 py-3">Recipient</th>
                <th className="px-4 py-3">Fee (LKR)</th>
                <th className="px-4 py-3">Booked Date</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {parcels.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-4 py-12 text-center text-slate-400">
                    <div className="mx-auto max-w-sm space-y-2">
                      <p className="text-base font-semibold text-slate-700">No deliveries found</p>
                      <p className="text-xs text-slate-500">
                        {searchQuery || currentStatus
                          ? "No parcels match the specified filter criteria. Try resetting filters."
                          : "You have not placed any delivery bookings yet."}
                      </p>
                    </div>
                  </td>
                </tr>
              ) : (
                parcels.map((parcel) => {
                  const badge = STATUS_BADGES[parcel.status] ?? {
                    bg: "bg-slate-100",
                    text: "text-slate-700",
                    label: parcel.status,
                  };
                  const dateStr = new Date(parcel.createdAt).toLocaleDateString("en-GB", {
                    year: "numeric",
                    month: "short",
                    day: "numeric",
                  });

                  return (
                    <tr key={parcel.id} className="hover:bg-slate-50/70 transition-colors">
                      <td className="px-4 py-3.5 font-medium text-slate-900">
                        <Link
                          href={`/track/${parcel.trackingNumber}`}
                          className="font-mono text-brand-600 hover:underline"
                        >
                          {parcel.trackingNumber}
                        </Link>
                      </td>
                      <td className="px-4 py-3.5">
                        <span className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium ${badge.bg} ${badge.text}`}>
                          {badge.label}
                        </span>
                      </td>
                      <td className="px-4 py-3.5">
                        <div className="text-xs">
                          <span className="font-semibold text-slate-700">{parcel.pickup.city}</span>
                          <span className="text-slate-400 mx-1">→</span>
                          <span className="font-semibold text-slate-700">{parcel.dropoff.city}</span>
                        </div>
                        <p className="truncate text-xs text-slate-400 max-w-[200px]">{parcel.dropoff.line1}</p>
                      </td>
                      <td className="px-4 py-3.5">
                        <p className="font-medium text-slate-800">{parcel.recipient.name}</p>
                        <p className="text-xs text-slate-400">{parcel.recipient.phone}</p>
                      </td>
                      <td className="px-4 py-3.5 font-medium text-slate-800">
                        {parcel.fee.currency} {Number(parcel.fee.amount).toFixed(2)}
                      </td>
                      <td className="px-4 py-3.5 text-xs text-slate-500">{dateStr}</td>
                      <td className="px-4 py-3.5 text-right">
                        <div className="flex items-center justify-end gap-2">
                          <Link
                            href={`/track/${parcel.trackingNumber}`}
                            className="rounded-md border border-slate-200 bg-white px-2.5 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50"
                          >
                            Track
                          </Link>
                          {/* Receipt download button via BFF */}
                          <a
                            href={`/api/bff/api/v1/parcels/${parcel.id}/receipt`}
                            download={`receipt-${parcel.trackingNumber}.pdf`}
                            className="inline-flex items-center gap-1 rounded-md bg-brand-50 px-2.5 py-1 text-xs font-semibold text-brand-700 hover:bg-brand-100"
                            title="Download official PDF delivery receipt"
                          >
                            <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                            </svg>
                            Receipt
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

        {/* Pagination footer */}
        {totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-slate-200 bg-slate-50 px-4 py-3 text-xs text-slate-600 sm:px-6">
            <div>
              Showing page <span className="font-semibold text-slate-800">{currentPage + 1}</span> of{" "}
              <span className="font-semibold text-slate-800">{totalPages}</span> ({totalElements} total parcels)
            </div>
            <div className="flex gap-2">
              {currentPage > 0 ? (
                <Link
                  href={{
                    pathname: "/customer/history",
                    query: { ...params, page: (currentPage - 1).toString() },
                  }}
                  className="btn-secondary py-1 px-3 text-xs"
                >
                  Previous
                </Link>
              ) : (
                <button disabled className="btn-secondary py-1 px-3 text-xs opacity-40 cursor-not-allowed">
                  Previous
                </button>
              )}

              {currentPage + 1 < totalPages ? (
                <Link
                  href={{
                    pathname: "/customer/history",
                    query: { ...params, page: (currentPage + 1).toString() },
                  }}
                  className="btn-secondary py-1 px-3 text-xs"
                >
                  Next
                </Link>
              ) : (
                <button disabled className="btn-secondary py-1 px-3 text-xs opacity-40 cursor-not-allowed">
                  Next
                </button>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
