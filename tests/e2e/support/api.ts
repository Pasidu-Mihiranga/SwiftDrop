import { type APIRequestContext, type Page, expect, request } from "@playwright/test";
import { type DemoUser, env } from "./env";

/**
 * A demo user's access token from the DEV ONLY swiftdrop-test client (password grant), for
 * driving backend steps of a flow whose screen is not built yet. UI steps sign in through the
 * real login page instead (see signIn).
 */
export async function tokenFor(user: DemoUser): Promise<string> {
  const response = await fetch(`${env.keycloakUrl}/realms/${env.realm}/protocol/openid-connect/token`, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "password",
      client_id: "swiftdrop-test",
      username: user.email,
      password: env.seedPassword,
      scope: "openid",
    }),
  });
  if (!response.ok) throw new Error(`Keycloak refused ${user.email}: ${response.status}`);
  return ((await response.json()) as { access_token: string }).access_token;
}

/**
 * A demo user's id as the services see it: the token's subject. The realm file gives the demo users
 * fixed ids, but Keycloak never re-imports a realm it already has, so a local Keycloak created
 * before those ids existed keeps its own. Reading the subject works in both cases.
 */
export async function userIdOf(user: DemoUser): Promise<string> {
  const payload = (await tokenFor(user)).split(".")[1];
  return (JSON.parse(Buffer.from(payload, "base64url").toString("utf8")) as { sub: string }).sub;
}

/** The gateway as one user (or anonymous): every call goes through routing, JWT checks and roles. */
export async function gatewayAs(user?: DemoUser): Promise<APIRequestContext> {
  const headers: Record<string, string> = { accept: "application/json" };
  if (user) headers.authorization = `Bearer ${await tokenFor(user)}`;
  return request.newContext({ baseURL: env.gatewayUrl, extraHTTPHeaders: headers });
}

/** True when the service behind a path answers at all (any status but a gateway 404/502/503/504). */
export async function deployed(api: APIRequestContext, path: string): Promise<boolean> {
  const response = await api.get(path, { failOnStatusCode: false });
  return ![404, 502, 503, 504].includes(response.status());
}

/** Signs in through the web app and Keycloak's own login form, as a person would. */
export async function signIn(page: Page, user: DemoUser, callbackUrl = "/dashboard"): Promise<void> {
  await page.goto(`/login?callbackUrl=${encodeURIComponent(callbackUrl)}`);
  await page.getByRole("button", { name: "Sign in with email" }).click();
  await page.locator("#username").fill(user.email);
  await page.locator("#password").fill(env.seedPassword);
  await page.locator("#kc-login").click();
  const target = new URL(callbackUrl, env.webUrl).pathname;
  await page.waitForURL((url) => url.origin === new URL(env.webUrl).origin && url.pathname === target);
}

/** A real quote from pricing-service, the way the booking wizard gets one. */
export async function quote(api: APIRequestContext, from = "Colombo", to = "Kandy", weightKg = 2) {
  const response = await api.post("/api/v1/pricing/quotes", {
    data: { fromDistrict: from, toDistrict: to, weightKg, sizeClass: "S", serviceLevel: "STANDARD" },
  });
  expect(response.status(), await response.text()).toBe(200);
  return (await response.json()) as { quoteId: string; fee: { amount: number; currency: string } };
}

/** Today in Sri Lanka, the business day the reports count in. */
export function businessToday(): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Colombo" }).format(new Date());
}
