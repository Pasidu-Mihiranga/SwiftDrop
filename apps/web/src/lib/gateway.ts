import "server-only";
import { getToken } from "next-auth/jwt";
import { headers } from "next/headers";
import { gatewayUrl } from "@/lib/keycloak";

/** Reads the (server-side only) Keycloak access token from the encrypted session cookie. */
export async function accessToken(requestHeaders?: Headers): Promise<string | undefined> {
  const token = await getToken({
    req: { headers: requestHeaders ?? (await headers()) },
    secret: process.env.AUTH_SECRET,
    secureCookie: (process.env.AUTH_URL ?? "").startsWith("https://"),
  });
  return token?.accessToken;
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
