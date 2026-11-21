import { randomBytes } from "node:crypto";
import { Inject, Injectable } from "@nestjs/common";
import type { AuthenticatedUser, Role } from "@swiftdrop/common";
import type Redis from "ioredis";
import { REDIS } from "../../infrastructure/redis.module";

export const TICKET_TTL_SECONDS = 60;
const KEY_PREFIX = "socket-ticket:";

/** What a ticket carries: identifiers and roles only, no name or e-mail. */
interface TicketPayload {
  id: string;
  roles: Role[];
  merchantId?: string;
}

/**
 * Single-use tickets for the live socket. The browser holds the ticket, never the access token: the
 * ticket lives 60 seconds in Redis and is removed the moment the socket handshake uses it (GETDEL).
 */
@Injectable()
export class SocketTicketStore {
  constructor(@Inject(REDIS) private readonly redis: Redis) {}

  async issue(user: AuthenticatedUser): Promise<string> {
    const ticket = randomBytes(32).toString("base64url");
    const payload: TicketPayload = { id: user.id, roles: user.roles, merchantId: user.merchantId };
    await this.redis.set(`${KEY_PREFIX}${ticket}`, JSON.stringify(payload), "EX", TICKET_TTL_SECONDS);
    return ticket;
  }

  /** Returns the user the ticket was issued to, or null when it is unknown, expired or already used. */
  async consume(ticket: string): Promise<AuthenticatedUser | null> {
    const raw = await this.redis.getdel(`${KEY_PREFIX}${ticket}`);
    if (!raw) return null;
    const payload = JSON.parse(raw) as TicketPayload;
    return { id: payload.id, roles: payload.roles, merchantId: payload.merchantId };
  }
}
