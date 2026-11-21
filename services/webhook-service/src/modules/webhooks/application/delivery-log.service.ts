import { Inject, Injectable } from "@nestjs/common";
import { ApiError } from "@swiftdrop/common";
import type { DeliveryStatus, WebhookDelivery } from "../domain/types";
import {
  DELIVERIES, DELIVERY_QUEUE, type DeliveryPage, type DeliveryQueue, type DeliveryRepository,
} from "./ports";

/** The delivery log for merchants and admins, and the admin's replay of a dead-lettered delivery. */
@Injectable()
export class DeliveryLogService {
  constructor(
    @Inject(DELIVERIES) private readonly deliveries: DeliveryRepository,
    @Inject(DELIVERY_QUEUE) private readonly queue: DeliveryQueue,
  ) {}

  forMerchant(merchantId: string, status: DeliveryStatus | undefined, page: number, size: number): Promise<DeliveryPage> {
    return this.deliveries.list({ merchantId, ...(status ? { status } : {}) }, page, size);
  }

  forAdmin(status: DeliveryStatus | undefined, page: number, size: number): Promise<DeliveryPage> {
    return this.deliveries.list(status ? { status } : {}, page, size);
  }

  /**
   * Sends a DEAD delivery again with a fresh attempt budget. Earlier attempts stay in the log.
   * @throws ApiError 404 for an unknown id, 409 when the delivery is not DEAD
   */
  async replay(id: string): Promise<WebhookDelivery> {
    const delivery = await this.deliveries.findById(id);
    if (!delivery) throw ApiError.notFound("Webhook delivery", id);
    if (delivery.status !== "DEAD") {
      throw new ApiError(409, "DELIVERY_NOT_DEAD", "Only a dead-lettered delivery can be replayed.");
    }
    await this.deliveries.update(id, { status: "PENDING", nextRetryAt: null });
    await this.queue.enqueue(id);
    return { ...delivery, status: "PENDING", nextRetryAt: undefined };
  }
}
