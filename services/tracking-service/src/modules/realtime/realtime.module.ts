import { Global, Module } from "@nestjs/common";
import { TIMELINE_BROADCASTER } from "../timeline/timeline.broadcast.port";
import { SocketTicketController } from "./socket-ticket.controller";
import { SocketTicketStore } from "./socket-ticket.store";
import { REALTIME_ADAPTER, TrackingGateway } from "./tracking.gateway";

/**
 * Live updates. Global so the Kafka consumers can inject the broadcaster port without importing this module.
 */
@Global()
@Module({
  controllers: [SocketTicketController],
  providers: [
    SocketTicketStore,
    TrackingGateway,
    { provide: REALTIME_ADAPTER, useValue: true },
    { provide: TIMELINE_BROADCASTER, useExisting: TrackingGateway },
  ],
  exports: [TIMELINE_BROADCASTER],
})
export class RealtimeModule {}
