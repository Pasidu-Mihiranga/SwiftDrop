import { Global, Inject, Module, type OnApplicationShutdown, type Provider } from "@nestjs/common";
import { createLogger } from "@swiftdrop/common";
import Redis from "ioredis";
import { env } from "../config/env";

export const REDIS = Symbol("REDIS");

const COMMAND_TIMEOUT_MS = 1000;

const redisProvider: Provider = {
  provide: REDIS,
  useFactory: () => {
    // Commands issued before the connection is ready are queued (the Socket.IO adapter subscribes at start-up).
    // A command that cannot finish within the timeout fails, so the rate limiter can fail open quickly.
    const client = new Redis(env.REDIS_URL, { maxRetriesPerRequest: 1, commandTimeout: COMMAND_TIMEOUT_MS });
    const logger = createLogger({ service: "tracking-service" });
    client.on("error", (error: Error) => logger.warn({ err: error }, "Redis connection error"));
    return client;
  },
};

/** One Redis client for the service (rate limits and the Socket.IO adapter). */
@Global()
@Module({ providers: [redisProvider], exports: [REDIS] })
export class RedisModule implements OnApplicationShutdown {
  constructor(@Inject(REDIS) private readonly redis: Redis) {}

  async onApplicationShutdown(): Promise<void> {
    this.redis.disconnect();
  }
}
