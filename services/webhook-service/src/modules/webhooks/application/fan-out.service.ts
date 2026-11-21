import { Inject, Injectable } from "@nestjs/common";
import type { EventEnvelope, MerchantLifecycleChanged, ParcelCreated, ParcelStatusChanged } from "@swiftdrop/common";
import { parcelCreatedData, parcelStatusChangedData } from "../domain/payload";
import type { WebhookEventType } from "../domain/types";
import { deliveryId } from "./ids";
import {
  DELIVERIES, DELIVERY_QUEUE, ENDPOINTS, MERCHANT_STATES, PARCEL_REFS, WEBHOOK_SETTINGS,
  type DeliveryQueue, type DeliveryRepository, type EndpointRepository, type MerchantStateRepository,
  type ParcelRefRepository, type WebhookSettings,
} from "./ports";

/**
 * Turns parcel events into deliveries: one per subscribed endpoint of the parcel's merchant. A
 * customer parcel (no merchant) creates none. A suspended merchant's deliveries are stored but held
 * until the merchant is approved again.
 */
@Injectable()
export class FanOutService {
  constructor(
    @Inject(ENDPOINTS) private readonly endpoints: EndpointRepository,
    @Inject(DELIVERIES) private readonly deliveries: DeliveryRepository,
    @Inject(MERCHANT_STATES) private readonly merchants: MerchantStateRepository,
    @Inject(PARCEL_REFS) private readonly parcelRefs: ParcelRefRepository,
    @Inject(DELIVERY_QUEUE) private readonly queue: DeliveryQueue,
    @Inject(WEBHOOK_SETTINGS) private readonly settings: WebhookSettings,
  ) {}

  async onParcelCreated(event: EventEnvelope<ParcelCreated>): Promise<number> {
    const { data } = event;
    if (data.merchantOrderRef) await this.parcelRefs.save(data.parcelId, data.merchantOrderRef);
    return this.fanOut(event, "parcel.created", data.merchantId, parcelCreatedData(data));
  }

  async onParcelStatusChanged(event: EventEnvelope<ParcelStatusChanged>): Promise<number> {
    const { data } = event;
    if (!data.merchantId) return 0;
    const merchantOrderRef = await this.parcelRefs.findMerchantOrderRef(data.parcelId);
    return this.fanOut(event, "parcel.status_changed", data.merchantId, parcelStatusChangedData(data, merchantOrderRef));
  }

  /** Records the merchant's status; a return to APPROVED sends whatever was held while suspended. */
  async onMerchantLifecycleChanged(event: EventEnvelope<MerchantLifecycleChanged>): Promise<number> {
    const { data } = event;
    const wasSuspended = await this.merchants.isSuspended(data.merchantId);
    await this.merchants.save(data.merchantId, data.status, new Date(data.changedAt));
    return wasSuspended && data.status === "APPROVED" ? this.releaseHeld(data.merchantId) : 0;
  }

  async releaseHeld(merchantId: string): Promise<number> {
    const held = await this.deliveries.findPendingByMerchant(merchantId);
    for (const delivery of held) await this.queue.enqueue(delivery.id);
    return held.length;
  }

  private async fanOut(event: EventEnvelope<unknown>, type: WebhookEventType, merchantId: string | null | undefined,
                       payload: Record<string, unknown>): Promise<number> {
    if (!merchantId) return 0;
    const [subscribed, suspended] = await Promise.all([
      this.endpoints.findSubscribed(merchantId, type),
      this.merchants.isSuspended(merchantId),
    ]);
    let queued = 0;
    for (const endpoint of subscribed) {
      const id = deliveryId();
      const inserted = await this.deliveries.insertPending({
        id, merchantId, endpointId: endpoint.id, eventId: event.eventId, eventType: type,
        sourceEventType: event.eventType, payload, url: endpoint.url, status: "PENDING", attempts: [],
        createdAt: this.settings.now(),
      });
      if (inserted && !suspended) {
        await this.queue.enqueue(id);
        queued++;
      }
    }
    return queued;
  }
}
