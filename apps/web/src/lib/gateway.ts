import "server-only";
import { cache } from "react";
import { getToken } from "next-auth/jwt";
import { headers } from "next/headers";
import { gatewayUrl, keycloakConfig, refreshKeycloakToken } from "@/lib/keycloak";

/** Decrypts the (server-side only) session cookie that holds the Keycloak tokens. */
async function sessionToken(requestHeaders?: Headers) {
  return getToken({
    req: { headers: requestHeaders ?? (await headers()) },
    secret: process.env.AUTH_SECRET,
    secureCookie: (process.env.AUTH_URL ?? "").startsWith("https://"),
  });
}

/** The access token is due for a refresh once this close to actually expiring. */
const REFRESH_SKEW_SECONDS = 30;

async function resolveAccessToken(requestHeaders?: Headers): Promise<string | undefined> {
  const token = await sessionToken(requestHeaders);
  if (!token?.accessToken) return undefined;
  const expiresAt = token.expiresAt ?? 0;
  if (Date.now() / 1000 < expiresAt - REFRESH_SKEW_SECONDS) return token.accessToken;
  if (!token.refreshToken) return undefined;
  const refreshed = await refreshKeycloakToken(keycloakConfig(), token.refreshToken);
  return refreshed?.accessToken;
}

/** Deduplicated per Server Component render so parallel gatewayFetch calls refresh at most once. */
const cachedAccessToken = cache(() => resolveAccessToken());

/**
 * Reads the (server-side only) Keycloak access token from the encrypted session cookie.
 *
 * The cookie is only rewritten with a refreshed token when Auth.js itself handles a request
 * (middleware, a route handler, a Server Action) — a Server Component cannot set cookies, so a
 * page that only calls {@link gatewayFetch} can sit on a stale cookie indefinitely. Rather than
 * send that stale token to the gateway and let it fail with 401 once Keycloak's (short-lived)
 * access token has expired, this refreshes it here and uses the new one for the outgoing call.
 * Per-request memoization ensures a page with multiple parallel fetches only refreshes once.
 */
export async function accessToken(requestHeaders?: Headers): Promise<string | undefined> {
  return requestHeaders ? resolveAccessToken(requestHeaders) : cachedAccessToken();
}

/** The Keycloak ID token, needed to end the Keycloak session on sign-out. */
export async function idToken(): Promise<string | undefined> {
  return (await sessionToken())?.idToken;
}

export interface GatewayFetchOptions extends RequestInit {
  anonymous?: boolean;
}

/** Server components / actions call backend APIs through the gateway with the user's token. */
export async function gatewayFetch(path: string, init: GatewayFetchOptions = {}): Promise<Response> {
  const isPublic =
    init.anonymous ||
    path.startsWith("/api/v1/pricing/zones") ||
    path.startsWith("/api/v1/pricing/special-handling") ||
    path.startsWith("/api/v1/pricing/locate");

  const [token, reqHeaders] = await Promise.all([
    isPublic ? Promise.resolve(undefined) : accessToken(),
    headers(),
  ]);
  const ip = visitorIp(reqHeaders);
  const hasRevalidate = Boolean((init as RequestInit & { next?: { revalidate?: number | false } }).next?.revalidate);
  return fetch(`${gatewayUrl()}${path}`, {
    ...init,
    ...(hasRevalidate ? {} : { cache: "no-store" }),
    headers: {
      ...(init.headers ?? {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      // The gateway rate-limits anonymous callers by IP; without this every visitor would share this server's.
      ...(ip ? { "X-Forwarded-For": ip } : {}),
    },
  });
}

/** The visitor's address as this server received it (first X-Forwarded-For hop, else X-Real-IP). */
export function visitorIp(requestHeaders: Headers): string | undefined {
  const forwarded = requestHeaders.get("x-forwarded-for")?.split(",")[0]?.trim();
  const ip = forwarded || requestHeaders.get("x-real-ip")?.trim();
  return ip && /^[0-9a-fA-F:.]{2,45}$/.test(ip) ? ip : undefined;
}
