import type { ParcelStatus } from "@swiftdrop/common";

/** One status change on a parcel, as shown on the tracking page (city only, never a street). */
export interface TimelineEvent {
  eventId: string;
  status: ParcelStatus;
  city: string | null;
  /** When the change happened (changedAt from the event), ISO-8601. */
  at: string;
}

/**
 * Read model for one parcel. Holds no phone number or street address: the recipient is kept only as a
 * masked name, and locations only as city names.
 */
export interface Timeline {
  trackingNumber: string;
  parcelId: string;
  /** Users allowed to see the full timeline: the customer and the assigned courier. */
  ownerIds: string[];
  customerId: string | null;
  courierId: string | null;
  /** Merchant entity id; the merchant is authorised by the merchant_id claim, not by sub. */
  merchantId: string | null;
  fromCity: string | null;
  toCity: string | null;
  recipientMasked: string | null;
  currentStatus: ParcelStatus | null;
  lastChangedAt: string | null;
  /** Sorted by `at`, oldest first. */
  events: TimelineEvent[];
  /** eventId of every event already applied, so a redelivered event changes nothing. */
  appliedEventIds: string[];
  /** Incremented on every change; used for optimistic concurrency when saving. */
  version: number;
}

export function emptyTimeline(trackingNumber: string, parcelId: string): Timeline {
  return {
    trackingNumber,
    parcelId,
    ownerIds: [],
    customerId: null,
    courierId: null,
    merchantId: null,
    fromCity: null,
    toCity: null,
    recipientMasked: null,
    currentStatus: null,
    lastChangedAt: null,
    events: [],
    appliedEventIds: [],
    version: 0,
  };
}

/** Thrown when a save finds the stored timeline changed since it was read; the caller reloads and retries. */
export class VersionConflictError extends Error {
  constructor(trackingNumber: string) {
    super(`Timeline ${trackingNumber} changed while it was being updated`);
    this.name = "VersionConflictError";
  }
}
