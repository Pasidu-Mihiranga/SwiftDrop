import Link from "next/link";
import { auth } from "@/auth";
import { LiveToasts } from "@/components/tracking/live-toasts";
import { gatewayFetch } from "@/lib/gateway";
import {
  formatMoney,
  statusBadgeClass,
  statusLabel,
  type ParcelPageResponse,
  type ParcelResponse,
  type ParcelStatus,
} from "@/lib/parcels";

export const metadata = { title: "My Deliveries — SwiftDrop" };

interface PageProps {
  searchParams: Promise<{
    status?: string;
    search?: string;
    page?: string;
    size?: string;
  }>;
}

const STATUS_FILTERS: { value: string; label: string }[] = [
  { value: "ALL", label: "All Parcels" },
  { value: "CREATED", label: "Created" },
  { value: "PICKED_UP", label: "Picked Up" },
  { value: "IN_TRANSIT", label: "In Transit" },
  { value: "OUT_FOR_DELIVERY", label: "Out for Delivery" },
  { value: "DELIVERED", label: "Delivered" },
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
      totalElements = data.totalElements || parcels.length;
      totalPages = Math.max(1, data.totalPages || 1);
    } else {
      serviceError = `Failed to load parcels (HTTP ${res.status})`;
    }
  } catch (err: unknown) {
    serviceError = "Parcel service is unreachable. Ensure the service is running.";
  }

  // Client-side search filtering by tracking number or recipient
  if (searchQuery && parcels.length > 0) {
    parcels = parcels.filter(
      (p) =>
        p.trackingNumber.toLowerCase().includes(searchQuery) ||
        p.recipient.name.toLowerCase().includes(searchQuery) ||
        p.dropoff.city.toLowerCase().includes(searchQuery) ||
        p.dropoff.district.toLowerCase().includes(searchQuery),
    );
  }

  const activeCount = parcels.filter((p) =>
    ["CREATED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY"].includes(p.status),
  ).length;

  return (
    <div className="space-y-6">
      <LiveToasts />
      {/* Top Banner & Quick Navigation */}
      <div className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-brand-100 bg-gradient-to-r from-brand-50 via-white to-brand-50/30 p-6 shadow-xs">
        <div>
          <span className="text-xs font-bold uppercase tracking-wider text-brand-700">Customer Portal</span>
          <h1 className="text-2xl font-extrabold text-slate-900 mt-0.5">My Deliveries</h1>
          <p className="text-sm text-slate-600 mt-1">
            Welcome back, {session?.user?.name || "Customer"}. Book new deliveries, track live progress, and manage orders.
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <Link href="/customer/history" className="btn-secondary text-sm">
            Delivery History &amp; Receipts
          </Link>
          <Link href="/customer/book" className="btn-primary text-sm shadow-sm">
            + Book New Delivery
          </Link>
        </div>
      </div>

      {/* Metrics Overview */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <div className="card flex items-center justify-between p-4">
          <div>
            <span className="text-xs text-slate-500 uppercase tracking-wider block">Active Deliveries</span>
            <span className="text-2xl font-extrabold text-brand-900">{activeCount}</span>
          </div>
          <span className="rounded-full bg-brand-50 px-3 py-1 text-xs font-semibold text-brand-700">In Progress</span>
        </div>
        <div className="card flex items-center justify-between p-4">
          <div>
            <span className="text-xs text-slate-500 uppercase tracking-wider block">Total Bookings</span>
            <span className="text-2xl font-extrabold text-slate-900">{totalElements}</span>
          </div>
          <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700">All Time</span>
        </div>
        <div className="card flex items-center justify-between p-4">
          <div>
            <span className="text-xs text-slate-500 uppercase tracking-wider block">Address Book</span>
            <Link href="/account" className="text-sm font-semibold text-brand-600 hover:text-brand-700">
              Manage saved addresses &rarr;
            </Link>
          </div>
          <span className="rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-700">Saved Addresses</span>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="card space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-4">
          {/* Status Tabs */}
          <div className="flex flex-wrap gap-1.5" role="tablist">
            {STATUS_FILTERS.map((filter) => {
              const active = (currentStatus ?? "ALL") === filter.value;
              const nextUrl = new URLSearchParams();
              if (filter.value !== "ALL") nextUrl.set("status", filter.value);
              if (searchQuery) nextUrl.set("search", searchQuery);
              return (
                <Link
                  key={filter.value}
                  href={`/customer?${nextUrl.toString()}`}
                  className={`rounded-lg px-3 py-1.5 text-xs font-medium transition-colors ${
                    active
                      ? "bg-brand-600 text-white shadow-xs"
                      : "bg-slate-100 text-slate-600 hover:bg-slate-200"
                  }`}
                >
                  {filter.label}
                </Link>
              );
            })}
          </div>

          {/* Search Box */}
          <form method="GET" action="/customer" className="flex items-center gap-2">
            {currentStatus && <input type="hidden" name="status" value={currentStatus} />}
            <input
              type="text"
              name="search"
              defaultValue={searchQuery}
              placeholder="Search tracking #, name, city..."
              className="input text-xs py-1.5 px-3 max-w-xs"
            />
            <button type="submit" className="btn-secondary text-xs py-1.5 px-3">
              Search
            </button>
            {searchQuery && (
              <Link href={`/customer${currentStatus ? `?status=${currentStatus}` : ""}`} className="text-xs text-slate-500 hover:underline">
                Clear
              </Link>
            )}
          </form>
        </div>

        {/* Error Notification */}
        {serviceError && (
          <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
            <p className="font-semibold">Notice</p>
            <p>{serviceError}</p>
          </div>
        )}

        {/* Parcels Table / List */}
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm text-slate-600">
            <thead className="bg-slate-50 text-xs uppercase text-slate-500 border-b border-slate-200">
              <tr>
                <th scope="col" className="px-4 py-3 font-semibold">Tracking Number</th>
                <th scope="col" className="px-4 py-3 font-semibold">Status</th>
                <th scope="col" className="px-4 py-3 font-semibold">Destination &amp; Recipient</th>
                <th scope="col" className="px-4 py-3 font-semibold">Package Specs</th>
                <th scope="col" className="px-4 py-3 font-semibold">Fee</th>
                <th scope="col" className="px-4 py-3 font-semibold">Booked Date</th>
                <th scope="col" className="px-4 py-3 text-right font-semibold">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {parcels.length === 0 ? (
                <tr>
                  <td colSpan={7} className="py-12 text-center text-slate-500">
                    <div className="space-y-3">
                      <p className="text-base font-semibold text-slate-700">No deliveries found</p>
                      <p className="text-xs text-slate-500 max-w-sm mx-auto">
                        {currentStatus
                          ? `No parcels currently match status "${currentStatus}".`
                          : "You have not booked any parcels yet. Start by creating your first delivery!"}
                      </p>
                      <Link href="/customer/book" className="btn-primary inline-flex text-xs">
                        + Book Your First Delivery
                      </Link>
                    </div>
                  </td>
                </tr>
              ) : (
                parcels.map((parcel) => (
                  <tr key={parcel.id} className="hover:bg-slate-50/60 transition-colors">
                    {/* Tracking Number */}
                    <td className="px-4 py-3 font-mono font-bold text-slate-900">
                      <Link
                        href={`/customer/parcels/${parcel.id}`}
                        className="text-brand-600 hover:text-brand-800 hover:underline"
                      >
                        {parcel.trackingNumber}
                      </Link>
                    </td>

                    {/* Status Badge */}
                    <td className="px-4 py-3">
                      <span
                        className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold ${statusBadgeClass(
                          parcel.status as ParcelStatus,
                        )}`}
                      >
                        {statusLabel(parcel.status as ParcelStatus)}
                      </span>
                    </td>

                    {/* Destination & Recipient */}
                    <td className="px-4 py-3">
                      <div className="font-medium text-slate-900">{parcel.recipient.name}</div>
                      <div className="text-xs text-slate-500">
                        {parcel.dropoff.city}, {parcel.dropoff.district}
                      </div>
                    </td>

                    {/* Specs */}
                    <td className="px-4 py-3 text-xs">
                      <span className="font-medium text-slate-800">{parcel.weightKg} kg</span>
                      <span className="text-slate-400"> · </span>
                      <span>Class {parcel.sizeClass}</span>
                      <span className="text-slate-400"> · </span>
                      <span className="text-slate-500">{parcel.serviceLevel}</span>
                    </td>

                    {/* Fee */}
                    <td className="px-4 py-3 font-semibold text-slate-900">
                      {formatMoney(parcel.fee.amount, parcel.fee.currency)}
                    </td>

                    {/* Date */}
                    <td className="px-4 py-3 text-xs text-slate-500">
                      {new Date(parcel.createdAt).toLocaleDateString("en-LK", {
                        year: "numeric",
                        month: "short",
                        day: "numeric",
                      })}
                    </td>

                    {/* Actions */}
                    <td className="px-4 py-3 text-right space-x-2">
                      <Link
                        href={`/track?q=${parcel.trackingNumber}`}
                        className="text-xs font-medium text-brand-600 hover:text-brand-800 hover:underline"
                      >
                        Track
                      </Link>
                      <Link
                        href={`/customer/parcels/${parcel.id}`}
                        className="btn-secondary text-xs px-2.5 py-1"
                      >
                        Details
                      </Link>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex items-center justify-between pt-4 border-t border-slate-100 text-xs text-slate-600">
            <span>
              Page {currentPage + 1} of {totalPages} ({totalElements} total parcels)
            </span>
            <div className="flex gap-2">
              <Link
                href={`/customer?page=${Math.max(0, currentPage - 1)}${
                  currentStatus ? `&status=${currentStatus}` : ""
                }${searchQuery ? `&search=${searchQuery}` : ""}`}
                className={`btn-secondary text-xs py-1 px-3 ${
                  currentPage === 0 ? "pointer-events-none opacity-50" : ""
                }`}
              >
                &larr; Previous
              </Link>
              <Link
                href={`/customer?page=${Math.min(totalPages - 1, currentPage + 1)}${
                  currentStatus ? `&status=${currentStatus}` : ""
                }${searchQuery ? `&search=${searchQuery}` : ""}`}
                className={`btn-secondary text-xs py-1 px-3 ${
                  currentPage >= totalPages - 1 ? "pointer-events-none opacity-50" : ""
                }`}
              >
                Next &rarr;
              </Link>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
