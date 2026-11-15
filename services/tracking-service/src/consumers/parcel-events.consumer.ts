import { Inject, Injectable, type OnModuleDestroy, type OnModuleInit } from "@nestjs/common";
import { LOGGER } from "@swiftdrop/common/nest";
import {
  type ContractName,
  type EventEnvelope,
  type EventHandler,
  MongoIdempotencyStore,
  type Logger,
  startEventConsumer,
  Topics,
  validateEvent,
  type ParcelAssigned,
  type ParcelCreated,
  type ParcelStatusChanged,
} from "@swiftdrop/common";
import { Kafka, type Consumer, type Producer } from "kafkajs";
import type { Connection } from "mongoose";
import { env } from "../config/env";
import { MONGO_CONNECTION } from "../infrastructure/mongo.module";
import type { Timeline } from "../modules/timeline/domain/timeline";
import { TIMELINE_BROADCASTER, type TimelineBroadcaster } from "../modules/timeline/timeline.broadcast.port";
import { TimelineService } from "../modules/timeline/timeline.service";
import { countOutcome, observeProjection } from "../observability/tracking-metrics";

const CONSUMER_GROUP = "tracking-service";
const CONSUMER_NAME = "tracking-projection";

/** Handlers by event type. Exported so the unit tests can call them without a Kafka connection. */
export function trackingHandlers(timelines: TimelineService, broadcaster: TimelineBroadcaster): Record<string, EventHandler> {
  const handler = (contract: ContractName, apply: (data: unknown, eventId: string) => Promise<Timeline | null>): EventHandler =>
    async (event: EventEnvelope<unknown>) => {
      const check = validateEvent(contract, event);
      if (!check.valid) throw new Error(`Invalid ${contract} event: ${check.errors.join("; ")}`);
      const saved = await apply(event.data, event.eventId);
      if (!saved) return;
      const seconds = Math.max(0, Date.now() - Date.parse(event.occurredAt)) / 1000;
      observeProjection(event.eventType, seconds);
      await broadcaster.timelineChanged(saved, event.eventType);
    };
  return {
    ParcelCreated: handler("parcel-created.v1", (data, id) => timelines.onCreated(data as ParcelCreated, id)),
    ParcelStatusChanged: handler("parcel-status-changed.v1", (data, id) => timelines.onStatusChanged(data as ParcelStatusChanged, id)),
    ParcelAssigned: handler("parcel-assigned.v1", (data, id) => timelines.onAssigned(data as ParcelAssigned, id)),
  };
}

/**
 * Translates parcel events into timeline changes. The shared template deduplicates, retries and
 * dead-letters; this class only validates the payload and calls the timeline service.
 */
@Injectable()
export class ParcelEventsConsumer implements OnModuleInit, OnModuleDestroy {
  private running?: { consumer: Consumer; producer: Producer };

  constructor(
    private readonly timelines: TimelineService,
    @Inject(TIMELINE_BROADCASTER) private readonly broadcaster: TimelineBroadcaster,
    @Inject(LOGGER) private readonly logger: Logger,
    @Inject(MONGO_CONNECTION) private readonly connection: Connection,
  ) {}

  async onModuleInit(): Promise<void> {
    if (!env.KAFKA_CONSUMER_ENABLED) {
      this.logger.info("Kafka consumer disabled by configuration");
      return;
    }
    const kafka = new Kafka({ clientId: "tracking-service", brokers: env.KAFKA_BROKERS.split(",") });
    const idempotency = new MongoIdempotencyStore(this.connection.collection("processed_event"));
    await idempotency.ensureIndexes();
    this.running = await startEventConsumer(kafka, {
      groupId: CONSUMER_GROUP,
      consumerName: CONSUMER_NAME,
      topics: [Topics.PARCEL_CREATED, Topics.PARCEL_STATUS_CHANGED, Topics.PARCEL_ASSIGNED],
      handlers: trackingHandlers(this.timelines, this.broadcaster),
      idempotency,
      logger: this.logger,
      deadLetterTopic: Topics.TRACKING_DEAD_LETTER,
      onOutcome: (outcome, topic) => countOutcome(outcome, topic),
    });
  }

  async onModuleDestroy(): Promise<void> {
    if (!this.running) return;
    await this.running.consumer.disconnect();
    await this.running.producer.disconnect();
  }
}
