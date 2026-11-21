import { Module } from "@nestjs/common";
import { TimelineModule } from "../modules/timeline/timeline.module";
import { ParcelEventsConsumer } from "./parcel-events.consumer";

/** Kafka consumers that feed the timeline read model. */
@Module({
  imports: [TimelineModule],
  providers: [ParcelEventsConsumer],
})
export class ConsumersModule {}
