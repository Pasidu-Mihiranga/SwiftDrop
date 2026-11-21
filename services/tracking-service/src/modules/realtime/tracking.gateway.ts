import { Inject, Injectable, Optional } from "@nestjs/common";
import { ConnectedSocket, MessageBody, OnGatewayInit, SubscribeMessage, WebSocketGateway, WebSocketServer } from "@nestjs/websockets";
import { createAdapter } from "@socket.io/redis-adapter";
import { type AuthenticatedUser, type JwtVerifier, type Logger } from "@swiftdrop/common";
import { JWT_VERIFIER, LOGGER } from "@swiftdrop/common/nest";
import type Redis from "ioredis";
import type { Server, Socket } from "socket.io";
import { REDIS } from "../../infrastructure/redis.module";
import { toPublicView } from "../timeline/domain/public-view";
import type { Timeline } from "../timeline/domain/timeline";
import type { TimelineBroadcaster } from "../timeline/timeline.broadcast.port";
import { canJoin, roomsFor } from "./domain/rooms";
import { SocketTicketStore } from "./socket-ticket.store";
import { socketClosed, socketOpened } from "../../observability/tracking-metrics";

export const REALTIME_ADAPTER = Symbol("REALTIME_ADAPTER");
export const PARCEL_STATUS_EVENT = "parcel.status";

interface JoinRequest {
  room?: unknown;
}

/**
 * Live updates over Socket.IO at /ws/tracking. The token is checked once, in the handshake; anonymous
 * sockets may join public tracking rooms only. Replicas share rooms through the Redis adapter.
 */
@WebSocketGateway({ path: "/ws/tracking" })
@Injectable()
export class TrackingGateway implements OnGatewayInit, TimelineBroadcaster {
  @WebSocketServer()
  private server!: Server;

  constructor(
    @Inject(JWT_VERIFIER) private readonly verify: JwtVerifier,
    @Inject(REDIS) private readonly redis: Redis,
    @Inject(LOGGER) private readonly logger: Logger,
    private readonly tickets: SocketTicketStore,
    @Optional() @Inject(REALTIME_ADAPTER) private readonly useRedisAdapter: boolean = true,
  ) {}

  afterInit(server: Server): void {
    server.use(async (socket, next) => {
      const auth = socket.handshake.auth as { token?: unknown; ticket?: unknown } | undefined;
      try {
        if (typeof auth?.token === "string" && auth.token.length > 0) {
          socket.data.user = await this.verify(auth.token);
          return next();
        }
        if (typeof auth?.ticket === "string" && auth.ticket.length > 0) {
          const user = await this.tickets.consume(auth.ticket); // single use: removed on this call
          if (!user) return next(new Error("UNAUTHORIZED"));
          socket.data.user = user;
          return next();
        }
        return next(); // anonymous: public rooms only
      } catch {
        return next(new Error("UNAUTHORIZED"));
      }
    });
    server.on("connection", (socket) => {
      socketOpened();
      socket.on("disconnect", () => socketClosed());
    });
    if (this.useRedisAdapter) {
      server.adapter(createAdapter(this.redis, this.redis.duplicate()));
      this.logger.info("Socket.IO Redis adapter enabled for /ws/tracking");
    }
  }

  /** Client sends { room } and receives { ok: true } or { ok: false, error: "forbidden" }. */
  @SubscribeMessage("join")
  join(@ConnectedSocket() client: Socket, @MessageBody() body: JoinRequest): { ok: boolean; error?: string } {
    const room = typeof body?.room === "string" ? body.room : "";
    const user = client.data.user as AuthenticatedUser | undefined;
    if (!canJoin(room, user)) return { ok: false, error: "forbidden" };
    void client.join(room);
    return { ok: true };
  }

  /** Pushes the masked view to every room that should hear about this timeline. */
  async timelineChanged(timeline: Timeline): Promise<void> {
    this.server.to(roomsFor(timeline)).emit(PARCEL_STATUS_EVENT, toPublicView(timeline));
  }
}
