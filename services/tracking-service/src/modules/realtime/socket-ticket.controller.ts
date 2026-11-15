import { Controller, HttpCode, Post } from "@nestjs/common";
import type { AuthenticatedUser } from "@swiftdrop/common";
import { CurrentUser } from "@swiftdrop/common/nest";
import { roomsForUser } from "./domain/rooms";
import { SocketTicketStore, TICKET_TTL_SECONDS } from "./socket-ticket.store";

/**
 * Issues a single-use ticket for the live socket, plus the rooms this user may join. Needs a valid
 * access token (the BFF sends it from the server-side session; the browser never sees it).
 */
@Controller("api/v1/tracking")
export class SocketTicketController {
  constructor(private readonly tickets: SocketTicketStore) {}

  @Post("socket-tickets")
  @HttpCode(201)
  async issue(@CurrentUser() user: AuthenticatedUser) {
    return {
      ticket: await this.tickets.issue(user),
      expiresIn: TICKET_TTL_SECONDS,
      rooms: roomsForUser(user),
    };
  }
}
