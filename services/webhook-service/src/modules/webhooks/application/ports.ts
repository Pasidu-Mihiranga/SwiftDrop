import type { WebhookDeliveryDeadLettered } from "@swiftdrop/common";
import type { BackoffSchedule } from "../domain/backoff";
import type { UrlPolicy } from "../domain/url-policy";
import type { DeliveryAttempt, DeliveryStatus, WebhookDelivery, WebhookEndpoint, WebhookEventType } from "../domain/types";

/** Injection tokens for the outbound ports; the infrastructure layer provides the implementations. */
export const ENDPOINTS = Symbol("WEBHOOK_ENDPOINTS");
export const DELIVERIES = Symbol("WEBHOOK_DELIVERIES");
export const MERCHANT_STATES = Symbol("WEBHOOK_MERCHANT_STATES");
export const PARCEL_REFS = Symbol("WEBHOOK_PARCEL_REFS");
export const DELIVERY_QUEUE = Symbol("WEBHOOK_DELIVERY_QUEUE");
export const DEAD_LETTERS = Symbol("WEBHOOK_DEAD_LETTERS");
export const SENDER = Symbol("WEBHOOK_SENDER");
export const SECRET_CIPHER = Symbol("WEBHOOK_SECRET_CIPHER");
export const WEBHOOK_SETTINGS = Symbol("WEBHOOK_SETTINGS");

export interface EndpointRepository {
  insert(endpoint: WebhookEndpoint): Promise<void>;
  findById(id: string): Promise<WebhookEndpoint | null>;
  /** Null for another merchant's endpoint, so callers answer 404 without revealing it exists. */
  findOwned(id: string, merchantId: string): Promise<WebhookEndpoint | null>;
  listByMerchant(merchantId: string): Promise<WebhookEndpoint[]>;
  findSubscribed(merchantId: string, eventType: WebhookEventType): Promise<WebhookEndpoint[]>;
  deleteOwned(id: string, merchantId: string): Promise<boolean>;
}

export interface DeliveryPage {
  items: WebhookDelivery[];
  total: number;
}

export interface DeliveryRepository {
  /** False when this endpoint already has a delivery for this event (a redelivered Kafka event). */
  insertPending(delivery: WebhookDelivery): Promise<boolean>;
  findById(id: string): Promise<WebhookDelivery | null>;
  list(filter: { merchantId?: string; status?: DeliveryStatus }, page: number, size: number): Promise<DeliveryPage>;
  findPendingByMerchant(merchantId: string): Promise<WebhookDelivery[]>;
  /** Changes the status and other fields, appending the attempt when one is given. */
  update(id: string, change: DeliveryChange, attempt?: DeliveryAttempt): Promise<void>;
}

export interface DeliveryChange {
  status: DeliveryStatus;
  nextRetryAt?: Date | null;
  deliveredAt?: Date;
  deadLetteredAt?: Date;
}

/** Merchant status projected from MerchantLifecycleChanged; deliveries to a suspended merchant are held. */
export interface MerchantStateRepository {
  isSuspended(merchantId: string): Promise<boolean>;
  save(merchantId: string, status: string, changedAt: Date): Promise<void>;
}

/** ParcelStatusChanged has no merchantOrderRef, so it is remembered from ParcelCreated. */
export interface ParcelRefRepository {
  save(parcelId: string, merchantOrderRef: string): Promise<void>;
  findMerchantOrderRef(parcelId: string): Promise<string | null>;
}

export interface DeliveryQueue {
  enqueue(deliveryId: string): Promise<void>;
}

export interface DeadLetterPublisher {
  publish(event: WebhookDeliveryDeadLettered): Promise<void>;
}

export interface SendResult {
  httpStatus?: number;
  latencyMs: number;
  error?: string;
}

export interface WebhookSender {
  post(url: string, body: string, headers: Record<string, string>, timeoutMs: number): Promise<SendResult>;
}

export interface SecretCipher {
  encrypt(plainText: string): string;
  decrypt(stored: string): string;
}

export interface WebhookSettings {
  backoff: BackoffSchedule;
  timeoutMs: number;
  urlPolicy: UrlPolicy;
  now: () => Date;
}
