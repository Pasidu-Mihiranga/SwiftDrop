import { maskName, type ParcelAssigned, type ParcelCreated, type ParcelStatusChanged } from "@swiftdrop/common";
import { emptyTimeline, type Timeline, type TimelineEvent } from "./timeline";

/**
 * Pure projection rules: each apply returns the next timeline and never mutates its input.
 * Rules: an event id is applied at most once; events stay sorted by time, so an event that
 * arrives late is placed correctly and never moves currentStatus backwards; an event for a parcel
 * not yet created starts a timeline that the ParcelCreated event completes later.
 */

export function applyCreated(current: Timeline | null, event: ParcelCreated, eventId: string): Timeline {
  const base = current ?? emptyTimeline(event.trackingNumber, event.parcelId);
  if (base.appliedEventIds.includes(eventId)) return base;
  const withDetails: Timeline = {
    ...base,
    customerId: event.customerId ?? base.customerId,
    merchantId: event.merchantId ?? base.merchantId,
    fromCity: event.pickup.city,
    toCity: event.dropoff.city,
    recipientMasked: maskName(event.contacts.recipientName),
  };
  return recordEvent(withDetails, eventId, { eventId, status: "CREATED", city: event.pickup.city, at: event.createdAt });
}

export function applyStatus(current: Timeline | null, event: ParcelStatusChanged, eventId: string): Timeline {
  const base = current ?? emptyTimeline(event.trackingNumber, event.parcelId);
  if (base.appliedEventIds.includes(eventId)) return base;
  const withOwners: Timeline = {
    ...base,
    customerId: event.customerId ?? base.customerId,
    merchantId: event.merchantId ?? base.merchantId,
    courierId: event.courierId ?? base.courierId,
  };
  return recordEvent(withOwners, eventId, {
    eventId,
    status: event.toStatus,
    city: event.location?.city ?? null,
    at: event.changedAt,
  });
}

export function applyAssigned(current: Timeline | null, event: ParcelAssigned, eventId: string): Timeline {
  const base = current ?? emptyTimeline(event.trackingNumber, event.parcelId);
  if (base.appliedEventIds.includes(eventId)) return base;
  return refresh({
    ...base,
    courierId: event.courierId,
    appliedEventIds: [...base.appliedEventIds, eventId],
    version: base.version + 1,
  });
}

function recordEvent(base: Timeline, eventId: string, next: TimelineEvent): Timeline {
  const events = [...base.events, next].sort((a, b) => a.at.localeCompare(b.at));
  return refresh({ ...base, events, appliedEventIds: [...base.appliedEventIds, eventId], version: base.version + 1 });
}

/** Derives the fields that depend on the event list and the owners. */
function refresh(timeline: Timeline): Timeline {
  const latest = timeline.events.at(-1) ?? null;
  const ownerIds = [timeline.customerId, timeline.courierId].filter((id): id is string => Boolean(id));
  return {
    ...timeline,
    ownerIds: [...new Set(ownerIds)],
    currentStatus: latest?.status ?? null,
    lastChangedAt: latest?.at ?? null,
  };
}
