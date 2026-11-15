import { Module } from "@nestjs/common";
import { SwiftDropCommonModule } from "@swiftdrop/common/nest";
import { env } from "./config/env";
import { RedisModule } from "./infrastructure/redis.module";
import { MetaModule } from "./modules/meta/meta.module";
import { TimelineModule } from "./modules/timeline/timeline.module";
import { TrackingModule } from "./modules/tracking/tracking.module";

@Module({
  imports: [
    // JWT guard, problem+json errors, correlation ids, JSON logging, /metrics and /health/live
    SwiftDropCommonModule.forRoot({
      service: "tracking-service",
      issuer: env.KEYCLOAK_ISSUER,
      jwksUri: env.KEYCLOAK_JWKS_URI,
    }),
    RedisModule,
    TimelineModule,
    TrackingModule,
    MetaModule,
  ],
})
export class AppModule {}
