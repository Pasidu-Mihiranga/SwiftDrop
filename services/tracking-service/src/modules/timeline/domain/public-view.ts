import type { ParcelStatus } from "@swiftdrop/common";
import type { Timeline } from "./timeline";

/** Shape returned by GET /api/v1/tracking/public/{trackingNumber}. Contains no personal data beyond a masked name. */
export interface PublicTrackingView {
  trackingNumber: string;
  currentStatus: ParcelStatus | null;
  from: string | null;
  to: string | null;
  recipient: string | null;
  events: { status: ParcelStatus; city: string | null; at: string }[];
}

export function toPublicView(timeline: Timeline): PublicTrackingView {
  return {
    trackingNumber: timeline.trackingNumber,
    currentStatus: timeline.currentStatus,
    from: timeline.fromCity,
    to: timeline.toCity,
    recipient: timeline.recipientMasked,
    events: timeline.events.map((event) => ({ status: event.status, city: event.city, at: event.at })),
  };
}
