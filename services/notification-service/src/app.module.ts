import { Module } from "@nestjs/common";
import { SwiftDropCommonModule } from "@swiftdrop/common/nest";
import { env } from "./config/env";
import { MetaModule } from "./modules/meta/meta.module";

@Module({
  imports: [
    // JWT guard, problem+json errors, correlation ids, JSON logging, /metrics and /health/live
    SwiftDropCommonModule.forRoot({
      service: "notification-service",
      issuer: env.KEYCLOAK_ISSUER,
      jwksUri: env.KEYCLOAK_JWKS_URI,
    }),
    MetaModule,
  ],
})
export class AppModule {}
