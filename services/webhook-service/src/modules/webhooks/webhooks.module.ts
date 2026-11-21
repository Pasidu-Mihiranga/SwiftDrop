import { Inject, Module, type DynamicModule, type OnApplicationBootstrap, type OnApplicationShutdown, type Provider } from "@nestjs/common";
import { LOGGER } from "@swiftdrop/common/nest";
import { Kafka, type Consumer } from "kafkajs";
import { MongoClient, type Db } from "mongodb";
import type { Logger } from "@swiftdrop/common";
import type { Env } from "../../config/env";
import { AdminWebhooksController } from "./api/admin-webhooks.controller";
import { MerchantWebhooksController } from "./api/merchant-webhooks.controller";
import { DeliveryLogService } from "./application/delivery-log.service";
import { DeliveryProcessor } from "./application/delivery-processor";
import { EndpointService } from "./application/endpoint.service";
import { FanOutService } from "./application/fan-out.service";
import {
  DEAD_LETTERS, DELIVERIES, DELIVERY_QUEUE, ENDPOINTS, MERCHANT_STATES, PARCEL_REFS, SECRET_CIPHER, SENDER,
  WEBHOOK_SETTINGS, type WebhookSettings,
} from "./application/ports";
import { BackoffSchedule } from "./domain/backoff";
import { UrlPolicy } from "./domain/url-policy";
import { AesSecretCipher } from "./infrastructure/aes-secret-cipher";
import { BullMqDeliveryQueue } from "./infrastructure/bullmq-delivery-queue";
import { FetchWebhookSender } from "./infrastructure/fetch-sender";
import { KafkaDeadLetterPublisher, startWebhookConsumers } from "./infrastructure/kafka-integration";
import {
  MongoDeliveryRepository, MongoEndpointRepository, MongoMerchantStateRepository, MongoParcelRefRepository,
} from "./infrastructure/mongo-repositories";

const MONGO_DB = Symbol("WEBHOOK_MONGO_DB");
const MONGO_CLIENT = Symbol("WEBHOOK_MONGO_CLIENT");
const KAFKA = Symbol("WEBHOOK_KAFKA");
const ENV = Symbol("WEBHOOK_ENV");

export const APPLICATION_SERVICES = [EndpointService, DeliveryLogService, FanOutService, DeliveryProcessor];
export const CONTROLLERS = [MerchantWebhooksController, AdminWebhooksController];

/** Starts and stops the parts that run in the background: indexes, the delivery worker and the Kafka consumer. */
class WebhookRuntime implements OnApplicationBootstrap, OnApplicationShutdown {
  private consumer?: Consumer;

  constructor(
    @Inject(ENV) private readonly env: Env,
    @Inject(MONGO_CLIENT) private readonly client: MongoClient,
    @Inject(MONGO_DB) private readonly db: Db,
    @Inject(KAFKA) private readonly kafka: Kafka,
    @Inject(ENDPOINTS) private readonly endpoints: MongoEndpointRepository,
    @Inject(DELIVERIES) private readonly deliveries: MongoDeliveryRepository,
    @Inject(PARCEL_REFS) private readonly parcelRefs: MongoParcelRefRepository,
    @Inject(DELIVERY_QUEUE) private readonly queue: BullMqDeliveryQueue,
    @Inject(DEAD_LETTERS) private readonly deadLetters: KafkaDeadLetterPublisher,
    private readonly processor: DeliveryProcessor,
    private readonly fanOut: FanOutService,
    @Inject(LOGGER) private readonly logger: Logger,
  ) {}

  async onApplicationBootstrap(): Promise<void> {
    await Promise.all([this.endpoints.ensureIndexes(), this.deliveries.ensureIndexes(), this.parcelRefs.ensureIndexes()]);
    await this.deadLetters.connect();
    if (this.env.WEBHOOK_WORKER_ENABLED) this.queue.startWorker(this.processor, this.logger);
    if (this.env.WEBHOOK_CONSUMERS_ENABLED) {
      this.consumer = await startWebhookConsumers(this.kafka, this.db, this.fanOut, this.logger, this.env.WEBHOOK_POISON_TOPIC);
    }
  }

  async onApplicationShutdown(): Promise<void> {
    await this.consumer?.disconnect();
    await this.queue.close();
    await this.deadLetters.disconnect();
    await this.client.close();
  }
}

/** Production wiring: MongoDB, BullMQ on Redis and Kafka behind the application ports. */
@Module({})
export class WebhooksModule {
  static forRoot(env: Env): DynamicModule {
    const backoff = new BackoffSchedule(env.WEBHOOK_BACKOFF_SECONDS);
    const settings: WebhookSettings = {
      backoff,
      timeoutMs: env.WEBHOOK_TIMEOUT_MS,
      urlPolicy: new UrlPolicy({ allowHttp: env.WEBHOOK_ALLOW_HTTP, allowPrivateNetworks: env.WEBHOOK_ALLOW_PRIVATE_NETWORKS }),
      now: () => new Date(),
    };
    const providers: Provider[] = [
      { provide: ENV, useValue: env },
      { provide: WEBHOOK_SETTINGS, useValue: settings },
      { provide: MONGO_CLIENT, useFactory: () => MongoClient.connect(env.MONGO_URI) },
      { provide: MONGO_DB, useFactory: (client: MongoClient) => client.db(), inject: [MONGO_CLIENT] },
      { provide: KAFKA, useValue: new Kafka({ clientId: "webhook-service", brokers: env.KAFKA_BROKERS.split(",") }) },
      { provide: ENDPOINTS, useFactory: (db: Db) => new MongoEndpointRepository(db), inject: [MONGO_DB] },
      { provide: DELIVERIES, useFactory: (db: Db) => new MongoDeliveryRepository(db), inject: [MONGO_DB] },
      { provide: MERCHANT_STATES, useFactory: (db: Db) => new MongoMerchantStateRepository(db), inject: [MONGO_DB] },
      { provide: PARCEL_REFS, useFactory: (db: Db) => new MongoParcelRefRepository(db), inject: [MONGO_DB] },
      { provide: DELIVERY_QUEUE, useValue: new BullMqDeliveryQueue({ url: env.REDIS_URL, maxRetriesPerRequest: null }, backoff) },
      { provide: DEAD_LETTERS, useFactory: (kafka: Kafka) => new KafkaDeadLetterPublisher(kafka), inject: [KAFKA] },
      { provide: SENDER, useValue: new FetchWebhookSender() },
      { provide: SECRET_CIPHER, useValue: new AesSecretCipher(env.WEBHOOK_SECRET_KEY) },
      ...APPLICATION_SERVICES,
      WebhookRuntime,
    ];
    return { module: WebhooksModule, controllers: CONTROLLERS, providers };
  }
}
