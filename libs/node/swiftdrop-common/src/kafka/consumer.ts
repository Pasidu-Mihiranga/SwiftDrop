import type { Consumer, EachMessagePayload, Kafka, Producer } from "kafkajs";
import type { Logger } from "pino";
import { EventHeaders } from "../events/topics";
import type { EventEnvelope } from "../events/types";

/**
 * Remembers which events a consumer already handled (Kafka delivers at-least-once).
 * {@link MongoIdempotencyStore} is the default implementation.
 */
export interface IdempotencyStore {
  /** @returns true the first time (eventId, consumer) is seen, false for duplicates */
  markIfFirst(eventId: string, consumer: string): Promise<boolean>;
  /** Forgets the marker so a dead-lettered event can be replayed later. */
  release(eventId: string, consumer: string): Promise<void>;
}

export type EventHandler = (event: EventEnvelope<unknown>, message: EachMessagePayload) => Promise<void>;

export interface EventConsumerOptions {
  /** Consumer group, e.g. "notification-service". */
  groupId: string;
  /** Logical consumer name used for idempotency, e.g. "notification-status-email". */
  consumerName: string;
  topics: string[];
  /** Handlers by eventType; unknown types are skipped (forward compatible). */
  handlers: Record<string, EventHandler>;
  idempotency: IdempotencyStore;
  logger: Logger;
  /** Attempts per message before it is parked on the dead-letter topic. */
  maxAttempts?: number;
  /** Where messages go after the last failed attempt, e.g. Topics.NOTIFICATION_DEAD_LETTER. */
  deadLetterTopic: string;
}

export type ProcessOutcome = "handled" | "duplicate" | "skipped" | "dead-lettered";

/**
 * Template method for every consumer: parse → de-duplicate → dispatch by eventType → retry →
 * dead-letter. Exposed separately from Kafka wiring so it can be unit tested.
 */
export async function processMessage(
  payload: EachMessagePayload,
  options: EventConsumerOptions,
  deadLetter: (payload: EachMessagePayload, error: unknown) => Promise<void>,
): Promise<ProcessOutcome> {
  const { message, topic } = payload;
  const raw = message.value?.toString("utf8");
  if (!raw) return "skipped";

  let event: EventEnvelope<unknown>;
  try {
    event = JSON.parse(raw) as EventEnvelope<unknown>;
  } catch (error) {
    options.logger.error({ topic, offset: message.offset }, "Unparseable event — sending to dead-letter topic");
    await deadLetter(payload, error);
    return "dead-lettered";
  }

  const handler = options.handlers[event.eventType];
  if (!handler) return "skipped";

  const log = options.logger.child({ eventId: event.eventId, eventType: event.eventType, correlationId: event.correlationId });
  const maxAttempts = options.maxAttempts ?? 3;
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      // Mark first so a crash after handling cannot cause a second side effect (e.g. a duplicate SMS);
      // if the handler keeps failing we release the marker and dead-letter, so a replay is processed.
      if (attempt === 1 && !(await options.idempotency.markIfFirst(event.eventId, options.consumerName))) {
        log.debug("Duplicate event ignored");
        return "duplicate";
      }
      await handler(event, payload);
      log.debug("Event handled");
      return "handled";
    } catch (error) {
      log.warn({ attempt, err: error }, "Event handler failed");
      if (attempt === maxAttempts) {
        await options.idempotency.release(event.eventId, options.consumerName);
        await deadLetter(payload, error);
        return "dead-lettered";
      }
      await new Promise((resolve) => setTimeout(resolve, 200 * 2 ** attempt));
    }
  }
  return "dead-lettered";
}

/** Starts a kafkajs consumer that routes every message through {@link processMessage}. */
export async function startEventConsumer(kafka: Kafka, options: EventConsumerOptions): Promise<{ consumer: Consumer; producer: Producer }> {
  const consumer = kafka.consumer({ groupId: options.groupId });
  const producer = kafka.producer({ idempotent: true });
  await Promise.all([consumer.connect(), producer.connect()]);
  for (const topic of options.topics) {
    await consumer.subscribe({ topic, fromBeginning: false });
  }

  const { deadLetterTopic } = options;
  const deadLetter = async ({ topic, partition, message }: EachMessagePayload, error: unknown) => {
    await producer.send({
      topic: deadLetterTopic,
      messages: [
        {
          key: message.key,
          value: message.value,
          headers: {
            ...message.headers,
            "x-original-topic": topic,
            "x-original-partition": String(partition),
            "x-original-offset": message.offset,
            "x-error": error instanceof Error ? error.message.slice(0, 300) : "unknown",
            "x-consumer": options.consumerName,
          },
        },
      ],
    });
  };

  await consumer.run({ eachMessage: (payload) => processMessage(payload, options, deadLetter).then(() => undefined) });
  options.logger.info({ topics: options.topics, groupId: options.groupId }, "Kafka consumer started");
  return { consumer, producer };
}

/** Publishes an envelope with the standard headers, keyed for per-aggregate ordering. */
export async function publishEvent(producer: Producer, topic: string, key: string, event: EventEnvelope<unknown>): Promise<void> {
  await producer.send({
    topic,
    messages: [
      {
        key,
        value: JSON.stringify(event),
        headers: {
          [EventHeaders.EVENT_ID]: event.eventId,
          [EventHeaders.EVENT_TYPE]: event.eventType,
          ...(event.correlationId ? { [EventHeaders.CORRELATION_ID]: event.correlationId } : {}),
        },
      },
    ],
  });
}
