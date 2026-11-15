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
const mongoPort = process.env.MONGO_HOST_PORT ?? "27018";
const mongoUser = process.env.MONGO_ROOT_USER ?? "swiftdrop";
const mongoPass = process.env.MONGO_ROOT_PASSWORD ?? "secret";
const smtpPort = process.env.MAILPIT_SMTP_HOST_PORT ?? "1026";

/** Fail fast at startup if configuration is missing or malformed (12-factor config). */
const schema = z.object({
  PORT: z.coerce.number().int().positive().default(8091),
  KEYCLOAK_ISSUER: z.url().default(`http://localhost:${keycloakPort}/realms/swiftdrop`),
  KEYCLOAK_JWKS_URI: z.url().default(`http://localhost:${keycloakPort}/realms/swiftdrop/protocol/openid-connect/certs`),
  KAFKA_BROKERS: z.string().min(1).default(`localhost:${kafkaPort}`),
  MONGO_URI: z.string().default(
    process.env.MONGO_URI ?? `mongodb://${mongoUser}:${mongoPass}@localhost:${mongoPort}/notifications?authSource=admin`
  ),
  SMTP_HOST: z.string().default(process.env.SMTP_HOST ?? "localhost"),
  SMTP_PORT: z.coerce.number().int().default(Number(smtpPort)),
  SMTP_FROM: z.string().default("SwiftDrop <notifications@swiftdrop.lk>"),
  FRONTEND_URL: z.string().default("http://localhost:3000"),
});

export type Env = z.infer<typeof schema>;

export function loadEnv(source: NodeJS.ProcessEnv = process.env): Env {
  return schema.parse(source);
}

export const env = loadEnv();
