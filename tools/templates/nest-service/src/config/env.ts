import { existsSync } from "node:fs";
import { z } from "zod";

// Local development: read the repository's .env (containers get real environment variables instead).
// process.loadEnvFile never overrides variables that are already set.
for (const file of [".env", "../../.env"]) {
  if (existsSync(file)) {
    process.loadEnvFile(file);
    break;
  }
}

const keycloakPort = process.env.KEYCLOAK_HOST_PORT ?? "8180";
const kafkaPort = process.env.KAFKA_HOST_PORT ?? "9092";

/** Fail fast at startup if configuration is missing or malformed (12-factor config). */
const schema = z.object({
  PORT: z.coerce.number().int().positive().default(__port__),
  KEYCLOAK_ISSUER: z.url().default(`http://localhost:${keycloakPort}/realms/swiftdrop`),
  KEYCLOAK_JWKS_URI: z.url().default(`http://localhost:${keycloakPort}/realms/swiftdrop/protocol/openid-connect/certs`),
  KAFKA_BROKERS: z.string().min(1).default(`localhost:${kafkaPort}`),
});

export type Env = z.infer<typeof schema>;

export function loadEnv(source: NodeJS.ProcessEnv = process.env): Env {
  return schema.parse(source);
}

export const env = loadEnv();
