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

/** "true"/"false" from the environment; z.coerce.boolean would read "false" as true. */
function flag(defaultValue: boolean) {
  return z.enum(["true", "false"]).default(defaultValue ? "true" : "false").transform((value) => value === "true");
}

/** Fail fast at startup if configuration is missing or malformed (12-factor config). */
const schema = z.object({
  PORT: z.coerce.number().int().positive().default(8092),
  KEYCLOAK_ISSUER: z.url().default(`http://localhost:${keycloakPort}/realms/swiftdrop`),
  KEYCLOAK_JWKS_URI: z.url().default(`http://localhost:${keycloakPort}/realms/swiftdrop/protocol/openid-connect/certs`),
  KAFKA_BROKERS: z.string().min(1).default(`localhost:${kafkaPort}`),
  MONGO_URI: z.string().min(1).default(`mongodb://localhost:${process.env.MONGO_HOST_PORT ?? "27017"}/webhooks`),
  REDIS_URL: z.string().min(1).default(`redis://localhost:${process.env.REDIS_HOST_PORT ?? "6379"}`),
  /** base64 32-byte AES key that encrypts signing secrets at rest; start-up fails without it */
  WEBHOOK_SECRET_KEY: z.string().min(1),
  /** Retry delays in seconds; shorten them for the demo, e.g. "5,10,20,40,60,90" */
  WEBHOOK_BACKOFF_SECONDS: z.string().default("30,120,600,1800,7200,21600")
    .transform((value) => value.split(",").map((part) => Number(part.trim())))
    .pipe(z.array(z.number().nonnegative()).min(1)),
  WEBHOOK_TIMEOUT_MS: z.coerce.number().int().positive().default(10_000),
  /** Plain http and private addresses are for the local demo network only (merchant simulator). */
  WEBHOOK_ALLOW_HTTP: flag(false),
  WEBHOOK_ALLOW_PRIVATE_NETWORKS: flag(false),
  WEBHOOK_WORKER_ENABLED: flag(true),
  WEBHOOK_CONSUMERS_ENABLED: flag(true),
  /** Where the consumer template parks an event its handler cannot process */
  WEBHOOK_POISON_TOPIC: z.string().min(1).default("swiftdrop.webhook.dead-letter.v1"),
});

export type Env = z.infer<typeof schema>;

export function loadEnv(source: NodeJS.ProcessEnv = process.env): Env {
  return schema.parse(source);
}

let cached: Env | undefined;

/** Loaded on first use, so a test can import modules without a full environment. */
export function env(): Env {
  cached ??= loadEnv();
  return cached;
}
