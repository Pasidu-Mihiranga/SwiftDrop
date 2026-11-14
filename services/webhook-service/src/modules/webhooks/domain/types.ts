/** Event types a merchant can subscribe to, as published in contracts/openapi/webhook-service.v1.yaml. */
export const WEBHOOK_EVENT_TYPES = ["parcel.created", "parcel.status_changed"] as const;
export type WebhookEventType = (typeof WEBHOOK_EVENT_TYPES)[number];
/** A signed test ping is delivered like any other webhook but never subscribed to. */
export const TEST_EVENT_TYPE = "webhook.test";
export type DeliveryEventType = WebhookEventType | typeof TEST_EVENT_TYPE;

export type DeliveryStatus = "PENDING" | "DELIVERED" | "RETRYING" | "DEAD";

export interface WebhookEndpoint {
  id: string;
  merchantId: string;
  url: string;
  eventTypes: WebhookEventType[];
  /** AES-256-GCM ciphertext; the plain secret is shown to the merchant once and kept nowhere else. */
  secretEnc: string;
  active: boolean;
  createdAt: Date;
}

/** One POST to the merchant. httpStatus is absent when no answer came back (timeout, refused, blocked). */
export interface DeliveryAttempt {
  at: Date;
  httpStatus?: number;
  latencyMs: number;
  error?: string;
}

export interface WebhookDelivery {
  id: string;
  merchantId: string;
  endpointId: string;
  /** The event that caused the delivery; with endpointId it is unique, so a redelivered event is not sent twice. */
  eventId: string;
  eventType: DeliveryEventType;
  /** Kafka event type, e.g. ParcelStatusChanged; "WebhookTest" for a ping. */
  sourceEventType: string;
  /** Allow-listed data only: no names, phone numbers, notes or street addresses. */
  payload: Record<string, unknown>;
  url: string;
  status: DeliveryStatus;
  attempts: DeliveryAttempt[];
  nextRetryAt?: Date;
  createdAt: Date;
  deliveredAt?: Date;
  deadLetteredAt?: Date;
}
