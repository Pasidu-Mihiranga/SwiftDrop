import { Module } from "@nestjs/common";
import { TimelineModule } from "../timeline/timeline.module";
import { RateLimitGuard } from "./rate-limit.guard";
import { TrackingController } from "./tracking.controller";
import { TrackingQueryService } from "./tracking.query.service";

/** Public and owner tracking endpoints over the timeline read model. */
@Module({
  imports: [TimelineModule],
  controllers: [TrackingController],
  providers: [TrackingQueryService, RateLimitGuard],
})
export class TrackingModule {}
