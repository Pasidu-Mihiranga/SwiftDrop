import { existsSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

// The same .env the stack was started from, so ports and demo passwords always match.
const rootEnv = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../../.env");
if (existsSync(rootEnv)) process.loadEnvFile(rootEnv);

const port = (name: string, fallback: number) => Number(process.env[name] ?? fallback);

export const env = {
  webUrl: process.env.E2E_WEB_URL ?? `http://localhost:${port("WEB_HOST_PORT", 3000)}`,
  gatewayUrl: process.env.E2E_GATEWAY_URL ?? `http://localhost:${port("GATEWAY_HOST_PORT", 8080)}`,
  keycloakUrl: process.env.KEYCLOAK_PUBLIC_URL ?? `http://localhost:${port("KEYCLOAK_HOST_PORT", 8180)}`,
  realm: process.env.KEYCLOAK_REALM ?? "swiftdrop",
  mailpitUrl: process.env.E2E_MAILPIT_URL ?? `http://localhost:${port("MAILPIT_UI_HOST_PORT", 8025)}`,
  seedPassword: process.env.KC_SEED_USER_PASSWORD ?? "",
};

/** Demo users from infra/keycloak/realm-swiftdrop.json (fixed ids). */
export const users = {
  admin: { email: "admin@swiftdrop.lk", id: "10000000-0000-4000-8000-000000000001" },
  dispatcher: { email: "dispatcher@swiftdrop.lk", id: "10000000-0000-4000-8000-000000000002" },
  courier: { email: "courier1@swiftdrop.lk", id: "10000000-0000-4000-8000-000000000003" },
  customer: { email: "customer@swiftdrop.lk", id: "10000000-0000-4000-8000-000000000005" },
  merchant: { email: "merchant@swiftdrop.lk", id: "10000000-0000-4000-8000-000000000006" },
} as const;

export type DemoUser = (typeof users)[keyof typeof users];
