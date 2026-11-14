import {
  createEnvelope, MongoIdempotencyStore, publishEvent, startEventConsumer, Topics,
  type EventEnvelope, type MerchantLifecycleChanged, type ParcelCreated, type ParcelStatusChanged,
  type WebhookDeliveryDeadLettered,
} from "@swiftdrop/common";
import type { Consumer, Kafka, Producer } from "kafkajs";
import type { Db } from "mongodb";
import type { Logger } from "@swiftdrop/common";
import type { DeadLetterPublisher } from "../application/ports";
import type { FanOutService } from "../application/fan-out.service";

const SERVICE = "webhook-service";

/** Publishes WebhookDeliveryDeadLettered, keyed by delivery id. No payload data travels with it. */
export class KafkaDeadLetterPublisher implements DeadLetterPublisher {
  private readonly producer: Producer;

  constructor(kafka: Kafka) {
    this.producer = kafka.producer({ idempotent: true });
  }

  connect(): Promise<void> {
    return this.producer.connect();
  }

  async publish(event: WebhookDeliveryDeadLettered): Promise<void> {
    await publishEvent(this.producer, Topics.WEBHOOK_DEAD_LETTER, event.deliveryId,
      createEnvelope("WebhookDeliveryDeadLettered", event, SERVICE));
  }

  disconnect(): Promise<void> {
    return this.producer.disconnect();
  }
}

/**
 * Subscribes to parcel and merchant lifecycle events through the shared consumer template, which
 * drops duplicates (markIfFirst) and retries a failing handler before parking the message.
 */
export async function startWebhookConsumers(kafka: Kafka, db: Db, fanOut: FanOutService, logger: Logger,
                                            poisonTopic: string): Promise<Consumer> {
  const idempotency = new MongoIdempotencyStore(db.collection("processed_event"));
  await idempotency.ensureIndexes();
  const { consumer } = await startEventConsumer(kafka, {
    groupId: SERVICE,
    consumerName: SERVICE,
    topics: [Topics.PARCEL_CREATED, Topics.PARCEL_STATUS_CHANGED, Topics.MERCHANT_LIFECYCLE],
    handlers: {
      ParcelCreated: (event) => fanOut.onParcelCreated(event as EventEnvelope<ParcelCreated>).then(() => undefined),
      ParcelStatusChanged: (event) => fanOut.onParcelStatusChanged(event as EventEnvelope<ParcelStatusChanged>).then(() => undefined),
      MerchantLifecycleChanged: (event) =>
        fanOut.onMerchantLifecycleChanged(event as EventEnvelope<MerchantLifecycleChanged>).then(() => undefined),
    },
    idempotency,
    deadLetterTopic: poisonTopic,
    logger,
  });
  return consumer;
}
