import { Module } from "@nestjs/common";
import { SwiftDropCommonModule } from "@swiftdrop/common/nest";
import { ConsumersModule } from "./consumers/consumers.module";
import { env } from "./config/env";
import { MongoModule } from "./infrastructure/mongo.module";
import { MetaModule } from "./modules/meta/meta.module";
import { TimelineModule } from "./modules/timeline/timeline.module";

@Module({
  imports: [
    // JWT guard, problem+json errors, correlation ids, JSON logging, /metrics and /health/live
    SwiftDropCommonModule.forRoot({
      service: "tracking-service",
      issuer: env.KEYCLOAK_ISSUER,
      jwksUri: env.KEYCLOAK_JWKS_URI,
    }),
    MongoModule,
    TimelineModule,
    ConsumersModule,
    MetaModule,
  ],
})
export class AppModule {}
