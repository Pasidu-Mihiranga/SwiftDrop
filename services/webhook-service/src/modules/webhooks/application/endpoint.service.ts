import { randomBytes, randomUUID } from "node:crypto";
import { Inject, Injectable } from "@nestjs/common";
import { ApiError } from "@swiftdrop/common";
import { TEST_EVENT_TYPE, type WebhookDelivery, type WebhookEndpoint, type WebhookEventType } from "../domain/types";
import { deliveryId, endpointId } from "./ids";
import {
  DELIVERIES, DELIVERY_QUEUE, ENDPOINTS, SECRET_CIPHER, WEBHOOK_SETTINGS,
  type DeliveryQueue, type DeliveryRepository, type EndpointRepository, type SecretCipher, type WebhookSettings,
} from "./ports";

export interface RegisteredEndpoint {
  endpoint: WebhookEndpoint;
  /** Returned only in the answer to registration. */
  secret: string;
}

/** Endpoint management for one merchant. The merchant id always comes from the token, never the body. */
@Injectable()
export class EndpointService {
  constructor(
    @Inject(ENDPOINTS) private readonly endpoints: EndpointRepository,
    @Inject(DELIVERIES) private readonly deliveries: DeliveryRepository,
    @Inject(DELIVERY_QUEUE) private readonly queue: DeliveryQueue,
    @Inject(SECRET_CIPHER) private readonly cipher: SecretCipher,
    @Inject(WEBHOOK_SETTINGS) private readonly settings: WebhookSettings,
  ) {}

  async register(merchantId: string, url: string, eventTypes: WebhookEventType[]): Promise<RegisteredEndpoint> {
    const accepted = await this.settings.urlPolicy.assertAllowed(url);
    const secret = `whsec_${randomBytes(32).toString("base64url")}`;
    const endpoint: WebhookEndpoint = {
      id: endpointId(),
      merchantId,
      url: accepted.toString(),
      eventTypes: [...new Set(eventTypes)],
      secretEnc: this.cipher.encrypt(secret),
      active: true,
      createdAt: this.settings.now(),
    };
    await this.endpoints.insert(endpoint);
    return { endpoint, secret };
  }

  list(merchantId: string): Promise<WebhookEndpoint[]> {
    return this.endpoints.listByMerchant(merchantId);
  }

  async remove(merchantId: string, id: string): Promise<void> {
    if (!(await this.endpoints.deleteOwned(id, merchantId))) throw ApiError.notFound("Webhook endpoint", id);
  }

  /** Queues a signed ping that goes through the same worker, signing and retries as a real event. */
  async sendTestPing(merchantId: string, id: string): Promise<string> {
    const endpoint = await this.endpoints.findOwned(id, merchantId);
    if (!endpoint) throw ApiError.notFound("Webhook endpoint", id);
    const delivery: WebhookDelivery = {
      id: deliveryId(),
      merchantId,
      endpointId: endpoint.id,
      eventId: randomUUID(),
      eventType: TEST_EVENT_TYPE,
      sourceEventType: "WebhookTest",
      payload: { message: "SwiftDrop test ping", endpointId: endpoint.id },
      url: endpoint.url,
      status: "PENDING",
      attempts: [],
      createdAt: this.settings.now(),
    };
    await this.deliveries.insertPending(delivery);
    await this.queue.enqueue(delivery.id);
    return delivery.id;
  }
}
