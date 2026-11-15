import { Global, Module, type OnApplicationShutdown, type Provider } from "@nestjs/common";
import Redis from "ioredis";
import { env } from "../config/env";

export const REDIS = Symbol("REDIS");

const redisProvider: Provider = {
  provide: REDIS,
  // Fail fast instead of queueing commands while Redis is down: the rate limiter then allows the request.
  useFactory: () => new Redis(env.REDIS_URL, { maxRetriesPerRequest: 1, enableOfflineQueue: false }),
};

/** One Redis client for the service (rate limits and the Socket.IO adapter). */
@Global()
@Module({ providers: [redisProvider], exports: [REDIS] })
export class RedisModule implements OnApplicationShutdown {
  constructor(private readonly redis: Redis) {}

  async onApplicationShutdown(): Promise<void> {
    this.redis.disconnect();
  }
}
