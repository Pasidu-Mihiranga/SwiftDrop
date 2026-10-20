/**
 * TypeScript mirror of the event contracts in /contracts/events (JSON Schema is the source of truth;
 * `validateEvent` checks real payloads against it).
 */
import { randomUUID } from "node:crypto";

export type ParcelStatus = "CREATED" | "PICKED_UP" | "IN_TRANSIT" | "OUT_FOR_DELIVERY" | "DELIVERED" | "FAILED" | "CANCELLED";
export type FailureReason = "RECIPIENT_UNAVAILABLE" | "WRONG_ADDRESS" | "REFUSED" | "DAMAGED" | "OTHER";
export type ActorRole = "CUSTOMER" | "MERCHANT" | "MERCHANT_API" | "COURIER" | "DISPATCHER" | "ADMIN" | "SYSTEM";
export type MerchantStatus = "PENDING" | "APPROVED" | "REJECTED" | "SUSPENDED";

export interface Location {
  city: string;
  district: string;
  zoneCode?: string | null;
}

export interface Money {
  amount: number;
  currency: string;
}

export interface Actor {
  userId: string;
  role: ActorRole;
}

export interface EventEnvelope<T> {
  eventId: string;
  eventType: string;
  eventVersion: number;
  occurredAt: string;
  correlationId?: string | null;
  producer: string;
  data: T;
}

export interface ParcelCreated {
  parcelId: string;
  trackingNumber: string;
  source: "CUSTOMER" | "MERCHANT";
  customerId?: string | null;
  merchantId?: string | null;
  merchantOrderRef?: string | null;
  pickup: Location;
  dropoff: Location;
  sizeClass: "S" | "M" | "L" | "XL";
  weightKg: number;
  serviceLevel: "STANDARD" | "EXPRESS";
  fee: Money;
  contacts: {
    senderName?: string | null;
    senderEmail?: string | null;
    senderPhone?: string | null;
    recipientName: string;
    recipientPhone: string;
  };
  createdAt: string;
}

export interface ParcelStatusChanged {
  parcelId: string;
  trackingNumber: string;
  customerId?: string | null;
  merchantId?: string | null;
  courierId?: string | null;
  fromStatus?: ParcelStatus | null;
  toStatus: ParcelStatus;
  note?: string | null;
  failureReason?: FailureReason | null;
  proof?: { type: "NAME" | "PHOTO" | "SIGNATURE"; receivedBy?: string | null; proofId?: string | null } | null;
  location?: Location | null;
  changedBy: Actor;
  changedAt: string;
}

export interface ParcelAssigned {
  assignmentId: string;
  parcelId: string;
  trackingNumber: string;
  courierId: string;
  previousCourierId?: string | null;
  runDate: string;
  assignedBy: Actor;
  assignedAt: string;
}

export interface MerchantLifecycleChanged {
  merchantId: string;
  businessName: string;
  ownerUserId: string;
  contactEmail: string;
  previousStatus?: MerchantStatus | null;
  status: MerchantStatus;
  reason?: string | null;
  changedBy: Actor;
  changedAt: string;
}

export interface WebhookDeliveryDeadLettered {
  deliveryId: string;
  merchantId: string;
  endpointId: string;
  sourceEventId: string;
  sourceEventType: string;
  attempts: number;
  lastHttpStatus?: number | null;
  lastError: string;
  deadLetteredAt: string;
}

/** Event type name → payload, used to type handlers. */
export interface EventPayloads {
  ParcelCreated: ParcelCreated;
  ParcelStatusChanged: ParcelStatusChanged;
  ParcelAssigned: ParcelAssigned;
  MerchantLifecycleChanged: MerchantLifecycleChanged;
  WebhookDeliveryDeadLettered: WebhookDeliveryDeadLettered;
}

export function createEnvelope<K extends keyof EventPayloads>(
  eventType: K,
  data: EventPayloads[K],
  producer: string,
  correlationId?: string | null,
): EventEnvelope<EventPayloads[K]> {
  return {
    eventId: randomUUID(),
    eventType,
    eventVersion: 1,
    occurredAt: new Date().toISOString(),
    correlationId: correlationId ?? null,
    producer,
    data,
  };
}
