import { Module, type Provider } from "@nestjs/common";
import { type Connection, type Model } from "mongoose";
import { MONGO_CONNECTION } from "../../infrastructure/mongo.module";
import type { Timeline } from "./domain/timeline";
import { MongoTimelineRepository } from "./infrastructure/mongo-timeline.repository";
import { timelineSchema } from "./infrastructure/timeline.schema";
import { TIMELINE_BROADCASTER, type TimelineBroadcaster } from "./timeline.broadcast.port";
import { TIMELINE_REPOSITORY } from "./timeline.repository.port";
import { TimelineService } from "./timeline.service";

export const TIMELINE_MODEL = Symbol("TIMELINE_MODEL");

const modelProvider: Provider = {
  provide: TIMELINE_MODEL,
  inject: [MONGO_CONNECTION],
  useFactory: (connection: Connection): Model<Timeline> => connection.model<Timeline>("Timeline", timelineSchema),
};

const repositoryProvider: Provider = {
  provide: TIMELINE_REPOSITORY,
  inject: [TIMELINE_MODEL],
  useFactory: (model: Model<Timeline>) => new MongoTimelineRepository(model),
};

/** No-op until the Socket.IO gateway is added in F5. */
const broadcasterProvider: Provider = {
  provide: TIMELINE_BROADCASTER,
  useValue: { timelineChanged: async () => undefined } satisfies TimelineBroadcaster,
};

/** Timeline read model: projection rules, the application service and the MongoDB adapter. */
@Module({
  providers: [modelProvider, repositoryProvider, broadcasterProvider, TimelineService],
  exports: [TimelineService, TIMELINE_REPOSITORY, TIMELINE_BROADCASTER],
})
export class TimelineModule {}
