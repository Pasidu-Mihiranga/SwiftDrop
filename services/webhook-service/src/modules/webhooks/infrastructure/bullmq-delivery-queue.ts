import { Queue, Worker, type ConnectionOptions } from "bullmq";
import type { Logger } from "@swiftdrop/common";
import type { DeliveryQueue } from "../application/ports";
import { RetryLaterError, type DeliveryProcessor } from "../application/delivery-processor";
import type { BackoffSchedule } from "../domain/backoff";

export const DELIVERY_QUEUE_NAME = "webhook-deliveries";
const BACKOFF_TYPE = "swiftdrop-webhook";

interface DeliveryJob {
  deliveryId: string;
}

/**
 * BullMQ on Redis. Kafka offsets commit as soon as the job is queued, so a slow merchant never holds up
 * event consumption. Each job gets the full attempt budget; a replay is a new job with a new budget.
 */
export class BullMqDeliveryQueue implements DeliveryQueue {
  private readonly queue: Queue<DeliveryJob>;
  private worker?: Worker<DeliveryJob>;

  constructor(private readonly connection: ConnectionOptions, private readonly backoff: BackoffSchedule) {
    this.queue = new Queue<DeliveryJob>(DELIVERY_QUEUE_NAME, { connection });
  }

  async enqueue(deliveryId: string): Promise<void> {
    await this.queue.add("deliver", { deliveryId }, {
      attempts: this.backoff.maxAttempts,
      backoff: { type: BACKOFF_TYPE },
      removeOnComplete: { count: 1000 },
      removeOnFail: { count: 5000 },
    });
  }

  /** Starts the worker. The delivery log in Mongo, not the job, is the record of what happened. */
  startWorker(processor: DeliveryProcessor, logger: Logger, concurrency = 10): void {
    this.worker = new Worker<DeliveryJob>(DELIVERY_QUEUE_NAME,
      async (job) => processor.process(job.data.deliveryId, job.attemptsMade + 1),
      {
        connection: this.connection,
        concurrency,
        settings: { backoffStrategy: (attemptsMade: number) => this.backoff.delayMs(attemptsMade) },
      });
    this.worker.on("failed", (job, error) => {
      if (!(error instanceof RetryLaterError)) logger.error({ err: error, deliveryId: job?.data.deliveryId }, "Delivery job failed");
    });
  }

  async close(): Promise<void> {
    await this.worker?.close();
    await this.queue.close();
  }
}
