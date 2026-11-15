import "server-only";
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

/**
 * Reads the (server-side only) Keycloak access token from the encrypted session cookie.
 *
 * The cookie is only rewritten with a refreshed token when Auth.js itself handles a request
 * (middleware, a route handler, a Server Action) — a Server Component cannot set cookies, so a
 * page that only calls {@link gatewayFetch} can sit on a stale cookie indefinitely. Rather than
 * send that stale token to the gateway and let it fail with 401 once Keycloak's (short-lived)
 * access token has expired, this refreshes it here and uses the new one for the outgoing call.
 * The refreshed token is not written back to the cookie; the next page that reads it refreshes
 * again the same way, which costs one extra round trip to Keycloak but never a failed request.
 */
export async function accessToken(requestHeaders?: Headers): Promise<string | undefined> {
  const token = await sessionToken(requestHeaders);
  if (!token?.accessToken) return undefined;
  const expiresAt = token.expiresAt ?? 0;
  if (Date.now() / 1000 < expiresAt - REFRESH_SKEW_SECONDS) return token.accessToken;
  if (!token.refreshToken) return token.accessToken;
  const refreshed = await refreshKeycloakToken(keycloakConfig(), token.refreshToken);
  // A refresh failure (session ended in Keycloak) is not this function's call to make: send the
  // token we have and let the caller see the gateway's 401, which is what actually happened.
  return refreshed?.accessToken ?? token.accessToken;
}

/** The Keycloak ID token, needed to end the Keycloak session on sign-out. */
export async function idToken(): Promise<string | undefined> {
  return (await sessionToken())?.idToken;
}

/** Server components / actions call backend APIs through the gateway with the user's token. */
export async function gatewayFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const token = await accessToken();
  return fetch(`${gatewayUrl()}${path}`, {
    ...init,
    cache: "no-store",
    headers: { ...(init.headers ?? {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
  });
}
