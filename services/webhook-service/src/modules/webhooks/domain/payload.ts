import type { ParcelCreated, ParcelStatusChanged } from "@swiftdrop/common";

/**
 * Builds webhook data from an allow-list. Copying named fields, rather than deleting known personal
 * ones, means a field added to an event later is never sent to merchants by accident. Contacts,
 * names, notes, courier and customer ids, and proof.receivedBy are never copied.
 */
export function parcelCreatedData(event: ParcelCreated): Record<string, unknown> {
  return {
    parcelId: event.parcelId,
    trackingNumber: event.trackingNumber,
    merchantOrderRef: event.merchantOrderRef ?? null,
    status: "CREATED",
    sizeClass: event.sizeClass,
    weightKg: event.weightKg,
    serviceLevel: event.serviceLevel,
    fee: { amount: event.fee.amount, currency: event.fee.currency },
    pickup: { city: event.pickup.city, district: event.pickup.district },
    dropoff: { city: event.dropoff.city, district: event.dropoff.district },
    createdAt: event.createdAt,
  };
}

/** @param merchantOrderRef not on the status event; remembered from ParcelCreated */
export function parcelStatusChangedData(event: ParcelStatusChanged, merchantOrderRef: string | null): Record<string, unknown> {
  return {
    parcelId: event.parcelId,
    trackingNumber: event.trackingNumber,
    merchantOrderRef,
    fromStatus: event.fromStatus ?? null,
    status: event.toStatus,
    failureReason: event.failureReason ?? null,
    proofType: event.proof?.type ?? null,
    changedAt: event.changedAt,
  };
}
