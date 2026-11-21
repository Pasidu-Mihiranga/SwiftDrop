import { type CanActivate, type ExecutionContext, Inject, Injectable } from "@nestjs/common";
import { ApiError, type Logger } from "@swiftdrop/common";
import { LOGGER } from "@swiftdrop/common/nest";
import type Redis from "ioredis";
import { env } from "../../config/env";
import { REDIS } from "../../infrastructure/redis.module";
import { countRateLimited } from "../../observability/tracking-metrics";

const WINDOW_SECONDS = 60;

interface RequestLike {
  headers: Record<string, string | string[] | undefined>;
  ip?: string;
  socket?: { remoteAddress?: string };
}

/** The client address: first hop of X-Forwarded-For (set by the gateway), else the socket address. */
export function clientIp(req: RequestLike): string {
  const forwarded = req.headers["x-forwarded-for"];
  const first = (Array.isArray(forwarded) ? forwarded[0] : forwarded)?.split(",")[0]?.trim();
  return first || req.ip || req.socket?.remoteAddress || "unknown";
}

/**
 * Fixed-window limit per client IP on public tracking lookups (enumeration protection).
 * If Redis is unavailable the request is allowed and a warning is logged, so the public page stays up.
 */
@Injectable()
export class RateLimitGuard implements CanActivate {
  constructor(
    @Inject(REDIS) private readonly redis: Redis,
    @Inject(LOGGER) private readonly logger: Logger,
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const http = context.switchToHttp();
    const req = http.getRequest<RequestLike>();
    const res = http.getResponse<{ setHeader(name: string, value: string): unknown }>();
    const key = `ratelimit:public-tracking:${clientIp(req)}`;

    let count: number;
    try {
      count = await this.redis.incr(key);
      if (count === 1) await this.redis.expire(key, WINDOW_SECONDS);
    } catch (error) {
      this.logger.warn({ err: error }, "Rate limit store unavailable; allowing the lookup");
      return true;
    }

    if (count > env.RATE_LIMIT_PUBLIC_PER_MIN) {
      countRateLimited();
      res.setHeader("Retry-After", String(WINDOW_SECONDS));
      throw new ApiError(429, "RATE_LIMITED", "Too many lookups. Try again in a minute.");
    }
    return true;
  }
}
