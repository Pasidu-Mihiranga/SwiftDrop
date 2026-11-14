import { Inject, Injectable } from "@nestjs/common";
import { signatureHeader, SIGNATURE_HEADER } from "../domain/signer";
import type { DeliveryAttempt, WebhookDelivery } from "../domain/types";
import {
  DEAD_LETTERS, DELIVERIES, ENDPOINTS, MERCHANT_STATES, SECRET_CIPHER, SENDER, WEBHOOK_SETTINGS,
  type DeadLetterPublisher, type DeliveryRepository, type EndpointRepository, type MerchantStateRepository,
  type SecretCipher, type SendResult, type WebhookSender, type WebhookSettings,
} from "./ports";

export type ProcessOutcome = "delivered" | "dead" | "held" | "skipped";

/** Thrown after a failed attempt that still has retries left, so the queue schedules the next one. */
export class RetryLaterError extends Error {
  constructor(readonly deliveryId: string, readonly retryInMs: number) {
    super(`Delivery ${deliveryId} failed; retrying in ${retryInMs} ms`);
    this.name = "RetryLaterError";
  }
}

const MAX_ERROR_LENGTH = 500;

/**
 * Makes one attempt at a delivery: re-check the URL, sign with a fresh timestamp, POST with a timeout,
 * record the attempt, then mark it DELIVERED, schedule a retry, or dead-letter it after the last attempt.
 */
@Injectable()
export class DeliveryProcessor {
  constructor(
    @Inject(DELIVERIES) private readonly deliveries: DeliveryRepository,
    @Inject(ENDPOINTS) private readonly endpoints: EndpointRepository,
    @Inject(MERCHANT_STATES) private readonly merchants: MerchantStateRepository,
    @Inject(SENDER) private readonly sender: WebhookSender,
    @Inject(SECRET_CIPHER) private readonly cipher: SecretCipher,
    @Inject(DEAD_LETTERS) private readonly deadLetters: DeadLetterPublisher,
    @Inject(WEBHOOK_SETTINGS) private readonly settings: WebhookSettings,
  ) {}

  /**
   * @param attempt 1 for the first try of this job; a replay starts a new job at 1
   * @throws RetryLaterError when the attempt failed and another is due
   */
  async process(deliveryId: string, attempt: number): Promise<ProcessOutcome> {
    const delivery = await this.deliveries.findById(deliveryId);
    if (!delivery || delivery.status === "DELIVERED" || delivery.status === "DEAD") return "skipped";
    if (await this.merchants.isSuspended(delivery.merchantId)) return "held";

    const endpoint = await this.endpoints.findById(delivery.endpointId);
    const result = endpoint?.active
      ? await this.send(delivery, this.cipher.decrypt(endpoint.secretEnc), endpoint.url)
      : { latencyMs: 0, error: "The endpoint was removed" };
    const at = this.settings.now();
    const record: DeliveryAttempt = { at, latencyMs: result.latencyMs, ...pick(result) };

    if (result.httpStatus !== undefined && result.httpStatus >= 200 && result.httpStatus < 300) {
      await this.deliveries.update(delivery.id, { status: "DELIVERED", nextRetryAt: null, deliveredAt: at }, record);
      return "delivered";
    }
    if (!endpoint?.active || attempt >= this.settings.backoff.maxAttempts) {
      await this.deliveries.update(delivery.id, { status: "DEAD", nextRetryAt: null, deadLetteredAt: at }, record);
      await this.deadLetters.publish({
        deliveryId: delivery.id, merchantId: delivery.merchantId, endpointId: delivery.endpointId,
        sourceEventId: delivery.eventId, sourceEventType: delivery.sourceEventType,
        attempts: delivery.attempts.length + 1, lastHttpStatus: result.httpStatus ?? null,
        lastError: describe(result), deadLetteredAt: at.toISOString(),
      });
      return "dead";
    }
    const retryInMs = this.settings.backoff.delayMs(attempt);
    await this.deliveries.update(delivery.id, { status: "RETRYING", nextRetryAt: new Date(at.getTime() + retryInMs) }, record);
    throw new RetryLaterError(delivery.id, retryInMs);
  }

  private async send(delivery: WebhookDelivery, secret: string, url: string): Promise<SendResult> {
    try {
      await this.settings.urlPolicy.assertAllowed(url);
    } catch {
      return { latencyMs: 0, error: "The URL now points to a private or reserved address" };
    }
    // The id stays the same on every attempt so a receiver can drop duplicates.
    const body = JSON.stringify({ id: delivery.id, type: delivery.eventType, createdAt: delivery.createdAt.toISOString(),
      data: delivery.payload });
    const timestamp = Math.floor(this.settings.now().getTime() / 1000);
    return this.sender.post(url, body, {
      "Content-Type": "application/json",
      "User-Agent": "SwiftDrop-Webhooks/1.0",
      [SIGNATURE_HEADER]: signatureHeader(secret, timestamp, body),
    }, this.settings.timeoutMs);
  }
}

function pick(result: SendResult): Pick<DeliveryAttempt, "httpStatus" | "error"> {
  return {
    ...(result.httpStatus !== undefined ? { httpStatus: result.httpStatus } : {}),
    ...(result.error ? { error: result.error.slice(0, MAX_ERROR_LENGTH) } : {}),
  };
}

function describe(result: SendResult): string {
  if (result.error) return result.error.slice(0, MAX_ERROR_LENGTH);
  return `Merchant endpoint returned HTTP ${result.httpStatus}`;
}
