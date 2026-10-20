import { type DynamicModule, Global, type MiddlewareConsumer, Module, type NestModule } from "@nestjs/common";
import { APP_FILTER, APP_GUARD } from "@nestjs/core";
import type { JWTVerifyGetKey } from "jose";
import { createJwtVerifier, remoteJwks } from "../auth/jwt";
import { createLogger } from "../logging/logger";
import { JWT_VERIFIER, JwtAuthGuard } from "./auth";
import { OpsController, RequestContextMiddleware } from "./observability";
import { ProblemDetailsFilter } from "./problem.filter";
import { LOGGER } from "./tokens";

export interface SwiftDropCommonOptions {
  service: string;
  /** Token issuer, e.g. http://localhost:8180/realms/swiftdrop */
  issuer: string;
  /** JWKS endpoint reachable from this service (internal network). */
  jwksUri: string;
  audience?: string;
  /** Override the key source (tests). */
  keys?: JWTVerifyGetKey;
}

/**
 * One import gives a Nest service the SwiftDrop conventions: JSON logger with PII masking,
 * global JWT guard (@Public / @RequireRoles), problem+json errors, correlation ids,
 * /metrics and /health/live.
 */
@Global()
@Module({})
export class SwiftDropCommonModule implements NestModule {
  static forRoot(options: SwiftDropCommonOptions): DynamicModule {
    return {
      module: SwiftDropCommonModule,
      controllers: [OpsController],
      providers: [
        { provide: LOGGER, useValue: createLogger({ service: options.service }) },
        {
          provide: JWT_VERIFIER,
          useValue: createJwtVerifier({
            issuer: options.issuer,
            audience: options.audience ?? "swiftdrop-api",
            keys: options.keys ?? remoteJwks(options.jwksUri),
          }),
        },
        { provide: APP_GUARD, useClass: JwtAuthGuard },
        { provide: APP_FILTER, useClass: ProblemDetailsFilter },
      ],
      exports: [LOGGER, JWT_VERIFIER],
    };
  }

  configure(consumer: MiddlewareConsumer): void {
    // "{*splat}" = every route (Express 5 / path-to-regexp v8 wildcard syntax)
    consumer.apply(RequestContextMiddleware).forRoutes("{*splat}");
  }
}
