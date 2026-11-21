import { env } from "./env";

/** Fails fast, with a hint, when the stack is not running, instead of 30 timeouts later. */
export default async function globalSetup() {
  const checks = [
    ["web app", `${env.webUrl}/`],
    ["api-gateway", `${env.gatewayUrl}/actuator/health`],
    ["Keycloak", `${env.keycloakUrl}/realms/${env.realm}/.well-known/openid-configuration`],
  ] as const;
  for (const [name, url] of checks) {
    let ok = false;
    for (let attempt = 0; attempt < 30 && !ok; attempt++) {
      ok = await fetch(url).then((r) => r.ok, () => false);
      if (!ok) await new Promise((resolve) => setTimeout(resolve, 2_000));
    }
    if (!ok) throw new Error(`${name} is not reachable at ${url}. Start the stack with "pnpm stack:up" first.`);
  }
  if (!env.seedPassword) throw new Error("KC_SEED_USER_PASSWORD is not set; run pnpm env:init and restart Keycloak.");
}
