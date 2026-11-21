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
const mongoUser = process.env.MONGO_ROOT_USER;
const mongoPass = process.env.MONGO_ROOT_PASSWORD;
const mongoDefault =
  mongoUser && mongoPass
    ? `mongodb://${mongoUser}:${mongoPass}@localhost:${mongoPort}/tracking?authSource=admin`
    : `mongodb://localhost:${mongoPort}/tracking`;
const redisPort = process.env.REDIS_HOST_PORT ?? "6380";
const redisPass = process.env.REDIS_PASSWORD;
const redisDefault = redisPass ? `redis://:${redisPass}@localhost:${redisPort}` : `redis://localhost:${redisPort}`;

/** Fail fast at startup if configuration is missing or malformed (12-factor config). */
const schema = z.object({
  PORT: z.coerce.number().int().positive().default(8090),
  KEYCLOAK_ISSUER: z.url().default(`http://localhost:${keycloakPort}/realms/swiftdrop`),
  KEYCLOAK_JWKS_URI: z.url().default(`http://localhost:${keycloakPort}/realms/swiftdrop/protocol/openid-connect/certs`),
  KAFKA_BROKERS: z.string().min(1).default(`localhost:${kafkaPort}`),
  MONGO_URI: z
    .string()
    .regex(/^mongodb(\+srv)?:\/\//, "MONGO_URI must start with mongodb:// or mongodb+srv://")
    .default(mongoDefault),
  /** Set to false to run the service without consuming Kafka (unit tests, local API work). */
  KAFKA_CONSUMER_ENABLED: z
    .enum(["true", "false"])
    .default("true")
    .transform((value) => value === "true"),
  /** Public tracking lookups allowed per client IP per minute (enumeration protection). */
  RATE_LIMIT_PUBLIC_PER_MIN: z.coerce.number().int().positive().default(60),
  REDIS_URL: z
    .string()
    .regex(/^rediss?:\/\//, "REDIS_URL must start with redis:// or rediss://")
    .default(redisDefault),
});

export type Env = z.infer<typeof schema>;

export function loadEnv(source: NodeJS.ProcessEnv = process.env): Env {
  return schema.parse(source);
}

export const env = loadEnv();
