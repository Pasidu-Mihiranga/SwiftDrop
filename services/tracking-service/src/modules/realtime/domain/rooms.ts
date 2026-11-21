import type { AuthenticatedUser } from "@swiftdrop/common";
import type { Timeline } from "../../timeline/domain/timeline";

/**
 * Socket.IO rooms. Who may join which room is decided here, in one place:
 * - tracking:<trackingNumber>  public, masked view (anyone, including anonymous sockets)
 * - user:<sub>                 the customer or courier with that sub (own room only)
 * - merchant:<merchantId>      the merchant with the matching merchant_id claim
 * - dispatch                   DISPATCHER or ADMIN
 */
const TRACKING_ROOM = /^tracking:(SD[0-9A-Z]{8,20})$/;

export const rooms = {
  tracking: (trackingNumber: string) => `tracking:${trackingNumber}`,
  user: (sub: string) => `user:${sub}`,
  merchant: (merchantId: string) => `merchant:${merchantId}`,
  dispatch: "dispatch",
} as const;

export function canJoin(room: string, user?: AuthenticatedUser): boolean {
  if (TRACKING_ROOM.test(room)) return true;
  if (room === rooms.dispatch) return Boolean(user?.roles.some((role) => role === "DISPATCHER" || role === "ADMIN"));
  if (room.startsWith("user:")) return Boolean(user) && room === rooms.user(user!.id);
  if (room.startsWith("merchant:")) return Boolean(user?.merchantId) && room === rooms.merchant(user!.merchantId!);
  return false;
}

/** Rooms a signed-in user may join: their own user room, their merchant room, and dispatch for staff. */
export function roomsForUser(user: AuthenticatedUser): string[] {
  const list = [rooms.user(user.id)];
  if (user.merchantId) list.push(rooms.merchant(user.merchantId));
  if (user.roles.some((role) => role === "DISPATCHER" || role === "ADMIN")) list.push(rooms.dispatch);
  return list;
}

/** Every room that should hear about a change to this timeline. */
export function roomsFor(timeline: Timeline): string[] {
  const list = [rooms.tracking(timeline.trackingNumber), ...timeline.ownerIds.map(rooms.user), rooms.dispatch];
  if (timeline.merchantId) list.push(rooms.merchant(timeline.merchantId));
  return list;
}
