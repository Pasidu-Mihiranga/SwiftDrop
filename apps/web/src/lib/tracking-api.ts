import "server-only";
import { gatewayUrl } from "@/lib/keycloak";
import type { TrackingView } from "@/lib/tracking";

/** Result of a public lookup, as the tracking page needs to show it. */
export type PublicLookup =
  | { kind: "found"; view: TrackingView }
  | { kind: "not-found" }
  | { kind: "busy" }
  | { kind: "unavailable" };

/**
 * Server-side call to the public tracking endpoint through the gateway. No token is sent: the public
 * view needs none. Runs on the server only, so the gateway URL is never shipped to the browser.
 */
export async function fetchPublicTracking(trackingNumber: string, fetchImpl: typeof fetch = fetch): Promise<PublicLookup> {
  let res: Response;
  try {
    res = await fetchImpl(`${gatewayUrl()}/api/v1/tracking/public/${encodeURIComponent(trackingNumber)}`, {
      cache: "no-store",
      headers: { accept: "application/json" },
    });
  } catch {
    return { kind: "unavailable" };
  }
  if (res.status === 404) return { kind: "not-found" };
  if (res.status === 429) return { kind: "busy" };
  if (!res.ok) return { kind: "unavailable" };
  return { kind: "found", view: (await res.json()) as TrackingView };
}
