import "server-only";
import { gatewayFetch } from "@/lib/gateway";
import type { Merchant, MerchantPage, MerchantStatus } from "@/lib/merchant";
import { QUEUE_PAGE_SIZE } from "@/lib/merchant";
import {
  EXPORT_MAX_ROWS,
  EXPORT_PAGE_SIZE,
  ORDERS_PAGE_SIZE,
  ordersApiQuery,
  type MerchantOrder,
  type MerchantOrderPage,
  type OrderFilter,
} from "@/lib/merchant-orders";

/**
 * Server-side reads of merchant-service through the gateway, with the caller's own token. The
 * service decides what the caller may see; these only fetch and parse.
 */

/** The caller's latest application, or null when they have never applied. */
export async function fetchMyApplication(): Promise<Merchant | null> {
  const response = await gatewayFetch("/api/v1/merchants/me");
  if (response.status === 404) return null;
  if (!response.ok) {
    throw new Error(`Could not load your merchant application (HTTP ${response.status})`);
  }
  return (await response.json()) as Merchant;
}

/** One page of the admin approval queue. Throws, so the admin screen says plainly that it failed. */
export async function fetchMerchantQueue(status: MerchantStatus, page: number): Promise<MerchantPage> {
  const query = new URLSearchParams({ status, page: String(page), size: String(QUEUE_PAGE_SIZE) });
  const response = await gatewayFetch(`/api/v1/admin/merchants?${query}`);
  if (!response.ok) {
    throw new Error(`Could not load merchant applications (HTTP ${response.status})`);
  }
  return (await response.json()) as MerchantPage;
}

/**
 * One page of the caller's merchant's orders, newest first. parcel-service takes the merchant from
 * the token, so no merchant id is sent. Throws, so the dashboard shows its error state.
 */
export async function fetchMyOrders(filter: OrderFilter, page: number): Promise<MerchantOrderPage> {
  return readOrders(filter, page, ORDERS_PAGE_SIZE);
}

/**
 * Every order matching the filter, for the CSV export: read a page at a time, stopping at
 * EXPORT_MAX_ROWS. `truncated` says whether more orders matched than were read.
 */
export async function fetchAllMyOrders(filter: OrderFilter): Promise<{ orders: MerchantOrder[]; truncated: boolean }> {
  const orders: MerchantOrder[] = [];
  for (let page = 0; ; page++) {
    const result = await readOrders(filter, page, EXPORT_PAGE_SIZE);
    orders.push(...result.content);
    const last = page + 1 >= result.page.totalPages || result.content.length === 0;
    if (orders.length >= EXPORT_MAX_ROWS) {
      return { orders: orders.slice(0, EXPORT_MAX_ROWS), truncated: !last || orders.length > EXPORT_MAX_ROWS };
    }
    if (last) return { orders, truncated: false };
  }
}

async function readOrders(filter: OrderFilter, page: number, size: number): Promise<MerchantOrderPage> {
  const response = await gatewayFetch(`/api/v1/merchant/orders?${ordersApiQuery(filter, page, size)}`);
  if (!response.ok) {
    throw new Error(`Could not load your orders (HTTP ${response.status})`);
  }
  return (await response.json()) as MerchantOrderPage;
}
