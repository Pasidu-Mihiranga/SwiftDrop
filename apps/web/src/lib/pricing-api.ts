import "server-only";
import { gatewayFetch } from "@/lib/gateway";
import type {
  DistrictAssignment,
  MerchantAgreement,
  Quote,
  RateCard,
  RemoteAreaCharge,
  SizeClassRate,
  Zone,
} from "@/lib/pricing";

/**
 * Server-side reads of pricing-service through the gateway.
 *
 * Pages call these instead of hard-coding zones, rates or an API host. Coverage is public, so the
 * quote page works for a visitor who has never signed in; the admin reads carry the caller's token.
 */

export interface PricingPage<T> {
  content: T[];
  page: { page: number; size: number; totalElements: number; totalPages: number };
}

/** Public coverage. Returns an empty list rather than throwing, so the page can still render. */
export async function fetchActiveZones(): Promise<{ zones: Zone[]; error?: string }> {
  try {
    const response = await gatewayFetch("/api/v1/pricing/zones");
    if (!response.ok) {
      return { zones: [], error: "We could not load our service areas just now. Please try again shortly." };
    }
    return { zones: (await response.json()) as Zone[] };
  } catch {
    return { zones: [], error: "We could not reach the pricing service. Please try again shortly." };
  }
}

/** Admin reads. These throw, because an admin screen should say plainly that it failed. */
export async function fetchAdminZones(): Promise<Zone[]> {
  return readJson<Zone[]>("/api/v1/admin/pricing/zones", "zones");
}

export async function fetchDistrictAssignments(): Promise<DistrictAssignment[]> {
  return readJson<DistrictAssignment[]>("/api/v1/admin/pricing/districts", "district assignments");
}

export async function fetchSizeClasses(): Promise<SizeClassRate[]> {
  return readJson<SizeClassRate[]>("/api/v1/admin/pricing/size-classes", "size classes");
}

export async function fetchRateCards(search: string = ""): Promise<PricingPage<RateCard>> {
  return readJson<PricingPage<RateCard>>(`/api/v1/admin/pricing/rate-cards${search}`, "rate cards");
}

export async function fetchRemoteAreas(): Promise<RemoteAreaCharge[]> {
  return readJson<RemoteAreaCharge[]>("/api/v1/admin/pricing/remote-areas", "remote-area charges");
}

export async function fetchAgreementsInForce(): Promise<MerchantAgreement[]> {
  return readJson<MerchantAgreement[]>("/api/v1/admin/pricing/merchants", "merchant agreements");
}

export async function fetchMerchantAgreements(merchantId: string): Promise<MerchantAgreement[]> {
  return readJson<MerchantAgreement[]>(
    `/api/v1/admin/pricing/merchants/${encodeURIComponent(merchantId)}/agreements`,
    "merchant agreements",
  );
}

export async function fetchQuote(quoteId: string): Promise<Quote> {
  return readJson<Quote>(`/api/v1/pricing/quotes/${encodeURIComponent(quoteId)}`, "quote");
}

async function readJson<T>(path: string, what: string): Promise<T> {
  const response = await gatewayFetch(path);
  if (!response.ok) {
    throw new Error(`Could not load ${what} (HTTP ${response.status})`);
  }
  return (await response.json()) as T;
}
