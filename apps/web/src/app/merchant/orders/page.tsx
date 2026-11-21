import Link from "next/link";
import { MerchantNav } from "@/components/merchant-nav";
import { formatDateTime } from "@/lib/merchant";
import { fetchMyOrders } from "@/lib/merchant-api";
import {
  ORDER_REF_MAX_LENGTH,
  PARCEL_STATUSES,
  filterSearch,
  formatFee,
  parcelStatusLabel,
  parcelStatusPillClass,
  parseOrdersQuery,
  type MerchantOrder,
  type MerchantOrderPage,
  type OrderFilter,
} from "@/lib/merchant-orders";

export const metadata = { title: "Orders" };

/**
 * The merchant's orders (M7): everything its system sent through the merchant API, newest first,
 * filtered by status and the day it was created, with a CSV export of the same selection.
 * parcel-service only ever returns the caller's own merchant's orders.
 */
export default async function MerchantOrdersPage({
  searchParams,
}: {
  searchParams: Promise<{ status?: string; from?: string; to?: string; orderRef?: string; page?: string }>;
}) {
  const { filter, page, error } = parseOrdersQuery(await searchParams);

  let orders: MerchantOrderPage | null = null;
  let loadError: string | null = null;
  if (!error) {
    try {
      orders = await fetchMyOrders(filter, page);
    } catch {
      loadError = "We could not load your orders just now. Please try again shortly.";
    }
  }
  const filtered = Boolean(filter.status || filter.from || filter.to || filter.orderRef);

  return (
    <div className="space-y-6">
      <MerchantNav current="/merchant/orders" />
      <div className="space-y-1">
        <h1 className="text-2xl font-bold">Orders</h1>
        <p className="text-slate-600">Every delivery your store has sent to SwiftDrop through the merchant API.</p>
      </div>

      <FilterForm filter={filter} />

      <section className="card space-y-4" aria-label="Order list">
        {error ? <p className="notice-error" role="alert">{error}</p> : null}
        {loadError ? <p className="notice-error" role="alert">{loadError}</p> : null}

        {orders ? (
          <>
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="text-sm text-slate-600">
                {orders.page.totalElements} {orders.page.totalElements === 1 ? "order" : "orders"}
                {filtered ? " match these filters" : ""}
              </p>
              {orders.page.totalElements > 0 ? (
                // A plain link: the browser downloads the file the route handler builds.
                <a className="btn-secondary" href={`/merchant/orders/export${filterSearch(filter)}`} download>
                  Download CSV
                </a>
              ) : null}
            </div>

            {orders.content.length === 0 ? (
              <p className="notice-info">
                {filtered
                  ? "No orders match these filters."
                  : "No orders yet. Orders appear here as soon as your system creates them through the API."}
              </p>
            ) : (
              <OrderTable orders={orders.content} />
            )}

            {orders.page.totalPages > 1 ? (
              <nav aria-label="Pages" className="flex items-center gap-2 text-sm">
                <PageLink filter={filter} page={orders.page.page - 1} disabled={orders.page.page === 0} label="Previous" />
                <span className="text-slate-600">
                  Page {orders.page.page + 1} of {orders.page.totalPages}
                </span>
                <PageLink
                  filter={filter}
                  page={orders.page.page + 1}
                  disabled={orders.page.page + 1 >= orders.page.totalPages}
                  label="Next"
                />
              </nav>
            ) : null}
          </>
        ) : null}
      </section>
    </div>
  );
}

/**
 * A GET form, so the filters live in the address bar and can be bookmarked or shared. Applying it
 * starts again from the first page, because the page number is not one of its fields.
 */
function FilterForm({ filter }: { filter: OrderFilter }) {
  return (
    <form
      method="get"
      action="/merchant/orders"
      className="card grid gap-4 sm:grid-cols-2 lg:grid-cols-5 lg:items-end"
      aria-label="Filter orders"
    >
      <div className="space-y-1">
        <label className="block text-sm font-medium text-slate-700" htmlFor="orderRef">Order reference</label>
        <input
          id="orderRef"
          name="orderRef"
          type="search"
          className="input"
          placeholder="e.g. WEB-1001"
          maxLength={ORDER_REF_MAX_LENGTH}
          defaultValue={filter.orderRef ?? ""}
          aria-describedby="orderRef-hint"
        />
        <p id="orderRef-hint" className="text-xs text-slate-500">Part of your own order number</p>
      </div>
      <div className="space-y-1">
        <label className="block text-sm font-medium text-slate-700" htmlFor="status">Status</label>
        <select id="status" name="status" className="input" defaultValue={filter.status ?? ""}>
          <option value="">Any status</option>
          {PARCEL_STATUSES.map((status) => (
            <option key={status} value={status}>{parcelStatusLabel(status)}</option>
          ))}
        </select>
      </div>
      <div className="space-y-1">
        <label className="block text-sm font-medium text-slate-700" htmlFor="from">Created from</label>
        <input id="from" name="from" type="date" className="input" defaultValue={filter.from ?? ""} />
      </div>
      <div className="space-y-1">
        <label className="block text-sm font-medium text-slate-700" htmlFor="to">Created to</label>
        <input id="to" name="to" type="date" className="input" defaultValue={filter.to ?? ""} />
      </div>
      <div className="flex gap-2">
        <button type="submit" className="btn-primary">Apply</button>
        <Link href="/merchant/orders" className="btn-secondary">Clear</Link>
      </div>
    </form>
  );
}

function OrderTable({ orders }: { orders: MerchantOrder[] }) {
  return (
    <div className="overflow-x-auto">
      <table className="min-w-full text-left text-sm">
        <thead className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
          <tr>
            <th scope="col" className="py-2 pr-4">Tracking number</th>
            <th scope="col" className="py-2 pr-4">Order ref</th>
            <th scope="col" className="py-2 pr-4">Status</th>
            <th scope="col" className="py-2 pr-4">Recipient</th>
            <th scope="col" className="py-2 pr-4">Route</th>
            <th scope="col" className="py-2 pr-4">Parcel</th>
            <th scope="col" className="py-2 pr-4 text-right">Fee</th>
            <th scope="col" className="py-2">Created</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {orders.map((order) => (
            <tr key={order.id}>
              <td className="py-2 pr-4 font-mono">
                <Link className="text-brand-700 hover:underline" href={`/track/${order.trackingNumber}`}>
                  {order.trackingNumber}
                </Link>
              </td>
              <td className="py-2 pr-4">{order.merchantOrderRef}</td>
              <td className="py-2 pr-4">
                <span className={parcelStatusPillClass(order.status)}>{parcelStatusLabel(order.status)}</span>
              </td>
              <td className="py-2 pr-4">{order.recipient.name}</td>
              <td className="py-2 pr-4 whitespace-nowrap">
                {order.pickup.city} → {order.dropoff.city}
              </td>
              <td className="py-2 pr-4 whitespace-nowrap">
                {order.sizeClass}, {order.weightKg} kg
              </td>
              <td className="py-2 pr-4 text-right whitespace-nowrap">{formatFee(order.fee)}</td>
              <td className="py-2 whitespace-nowrap text-slate-600">{formatDateTime(order.createdAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function PageLink({ filter, page, disabled, label }: {
  filter: OrderFilter;
  page: number;
  disabled: boolean;
  label: string;
}) {
  if (disabled) {
    return <span className="btn-secondary opacity-50" aria-disabled="true">{label}</span>;
  }
  return (
    <Link className="btn-secondary" href={`/merchant/orders${filterSearch(filter, page)}`}>
      {label}
    </Link>
  );
}
