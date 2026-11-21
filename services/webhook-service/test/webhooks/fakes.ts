import type {
  DeadLetterPublisher, DeliveryChange, DeliveryPage, DeliveryQueue, DeliveryRepository, EndpointRepository,
  MerchantStateRepository, ParcelRefRepository, SendResult, WebhookSender, WebhookSettings,
} from "../../src/modules/webhooks/application/ports";
import type { WebhookDeliveryDeadLettered } from "@swiftdrop/common";
import { BackoffSchedule } from "../../src/modules/webhooks/domain/backoff";
import type { DeliveryAttempt, DeliveryStatus, WebhookDelivery, WebhookEndpoint, WebhookEventType } from "../../src/modules/webhooks/domain/types";
import { UrlPolicy } from "../../src/modules/webhooks/domain/url-policy";
import { AesSecretCipher } from "../../src/modules/webhooks/infrastructure/aes-secret-cipher";

/** Test-only key: 32 bytes, base64. */
export const TEST_KEY = Buffer.from("0123456789abcdef0123456789abcdef").toString("base64");
export const NOW = new Date("2026-11-14T08:00:00Z");

export class InMemoryEndpoints implements EndpointRepository {
  readonly rows = new Map<string, WebhookEndpoint>();
  async insert(endpoint: WebhookEndpoint) { this.rows.set(endpoint.id, endpoint); }
  async findById(id: string) { return this.rows.get(id) ?? null; }
  async findOwned(id: string, merchantId: string) { const e = this.rows.get(id); return e?.merchantId === merchantId ? e : null; }
  async listByMerchant(merchantId: string) { return [...this.rows.values()].filter((e) => e.merchantId === merchantId); }
  async findSubscribed(merchantId: string, type: WebhookEventType) {
    return [...this.rows.values()].filter((e) => e.merchantId === merchantId && e.active && e.eventTypes.includes(type));
  }
  async deleteOwned(id: string, merchantId: string) { return (await this.findOwned(id, merchantId)) ? this.rows.delete(id) : false; }
}

export class InMemoryDeliveries implements DeliveryRepository {
  readonly rows = new Map<string, WebhookDelivery>();
  async insertPending(delivery: WebhookDelivery) {
    if ([...this.rows.values()].some((d) => d.endpointId === delivery.endpointId && d.eventId === delivery.eventId)) return false;
    this.rows.set(delivery.id, { ...delivery, attempts: [...delivery.attempts] });
    return true;
  }
  async findById(id: string) { const d = this.rows.get(id); return d ? { ...d, attempts: [...d.attempts] } : null; }
  async list(filter: { merchantId?: string; status?: DeliveryStatus }, page: number, size: number): Promise<DeliveryPage> {
    const all = [...this.rows.values()].filter((d) => (!filter.merchantId || d.merchantId === filter.merchantId)
      && (!filter.status || d.status === filter.status));
    return { items: all.slice(page * size, page * size + size), total: all.length };
  }
  async findPendingByMerchant(merchantId: string) {
    return [...this.rows.values()].filter((d) => d.merchantId === merchantId && d.status === "PENDING");
  }
  async update(id: string, change: DeliveryChange, attempt?: DeliveryAttempt) {
    const d = this.rows.get(id)!;
    const { nextRetryAt, ...rest } = change;
    this.rows.set(id, { ...d, ...rest, nextRetryAt: nextRetryAt ?? undefined, attempts: attempt ? [...d.attempts, attempt] : d.attempts });
  }
}

export class InMemoryMerchants implements MerchantStateRepository {
  readonly statuses = new Map<string, string>();
  async isSuspended(merchantId: string) { return this.statuses.get(merchantId) === "SUSPENDED"; }
  async save(merchantId: string, status: string) { this.statuses.set(merchantId, status); }
}

export class InMemoryParcelRefs implements ParcelRefRepository {
  readonly refs = new Map<string, string>();
  async save(parcelId: string, ref: string) { this.refs.set(parcelId, ref); }
  async findMerchantOrderRef(parcelId: string) { return this.refs.get(parcelId) ?? null; }
}

export class RecordingQueue implements DeliveryQueue {
  readonly queued: string[] = [];
  async enqueue(deliveryId: string) { this.queued.push(deliveryId); }
}

export class RecordingDeadLetters implements DeadLetterPublisher {
  readonly published: WebhookDeliveryDeadLettered[] = [];
  async publish(event: WebhookDeliveryDeadLettered) { this.published.push(event); }
}

/** Answers with the next scripted result and records what was sent. */
export class ScriptedSender implements WebhookSender {
  readonly sent: { url: string; body: string; headers: Record<string, string> }[] = [];
  constructor(private readonly results: SendResult[] = [{ httpStatus: 200, latencyMs: 12 }]) {}
  async post(url: string, body: string, headers: Record<string, string>) {
    this.sent.push({ url, body, headers });
    return this.results.length > 1 ? this.results.shift()! : this.results[0];
  }
}

export function testSettings(overrides: Partial<WebhookSettings> = {}): WebhookSettings {
  return {
    backoff: new BackoffSchedule([30, 120, 600, 1800, 7200, 21600]),
    timeoutMs: 10_000,
    // Public example address, so no DNS is needed.
    urlPolicy: new UrlPolicy({ allowHttp: false, allowPrivateNetworks: false, lookup: async () => ["93.184.216.34"] }),
    now: () => NOW,
    ...overrides,
  };
}

export function fakes() {
  return {
    endpoints: new InMemoryEndpoints(),
    deliveries: new InMemoryDeliveries(),
    merchants: new InMemoryMerchants(),
    parcelRefs: new InMemoryParcelRefs(),
    queue: new RecordingQueue(),
    deadLetters: new RecordingDeadLetters(),
    cipher: new AesSecretCipher(TEST_KEY),
  };
}
