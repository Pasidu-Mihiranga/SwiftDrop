import "server-only";
import { gatewayFetch } from "@/lib/gateway";
import type { Merchant, MerchantPage, MerchantStatus } from "@/lib/merchant";
import { QUEUE_PAGE_SIZE } from "@/lib/merchant";

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
