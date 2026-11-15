import { Injectable, type OnModuleInit, type OnModuleDestroy, Logger } from "@nestjs/common";
import { Kafka, type Consumer, type Producer } from "kafkajs";
import {
  Topics,
  startEventConsumer,
  MongoIdempotencyStore,
  type EventEnvelope,
  createLogger,
} from "@swiftdrop/common";
import { env } from "../config/env";
import { DatabaseService } from "../persistence/database.service";
import { NotificationDispatcherService } from "../modules/channels/notification-dispatcher.service";

interface ParcelCreatedData {
  parcelId: string;
  trackingNumber: string;
  source: string;
  customerId?: string;
  merchantId?: string;
  pickup: { city: string; district: string; zoneCode?: string };
  dropoff: { city: string; district: string; zoneCode?: string };
  fee: { amount: number; currency: string };
  contacts: {
    senderName?: string;
    senderEmail?: string;
    senderPhone?: string;
    recipientName: string;
    recipientPhone: string;
  };
  createdAt: string;
}

interface ParcelStatusChangedData {
  parcelId: string;
  trackingNumber: string;
  customerId?: string;
  merchantId?: string;
  courierId?: string;
  fromStatus?: string;
  toStatus: string;
  note?: string;
  failureReason?: string;
  proof?: { type: string; receivedBy?: string; proofId?: string };
  changedAt: string;
}

interface ParcelAssignedData {
  assignmentId: string;
  parcelId: string;
  trackingNumber: string;
  courierId: string;
  runDate: string;
  assignedAt: string;
}

interface MerchantLifecycleData {
  merchantId: string;
  businessName: string;
  ownerUserId: string;
  contactEmail: string;
  status: string;
  reason?: string;
  changedAt: string;
}

@Injectable()
export class NotificationConsumer implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(NotificationConsumer.name);
  private consumer?: Consumer;
  private producer?: Producer;

  constructor(
    private readonly db: DatabaseService,
    private readonly dispatcher: NotificationDispatcherService,
  ) {}

  async onModuleInit(): Promise<void> {
    await this.db.ensureConnected();

    const kafka = new Kafka({
      clientId: "notification-service",
      brokers: env.KAFKA_BROKERS.split(","),
    });

    const pinoLogger = createLogger({ service: "notification-service" });
    const idempotency = new MongoIdempotencyStore(this.db.idempotency);

    try {
      const handles = await startEventConsumer(kafka, {
        groupId: "notification-service",
        consumerName: "notification-events",
        topics: [
          Topics.PARCEL_CREATED,
          Topics.PARCEL_STATUS_CHANGED,
          Topics.PARCEL_ASSIGNED,
          Topics.MERCHANT_LIFECYCLE,
        ],
        deadLetterTopic: Topics.NOTIFICATION_DEAD_LETTER,
        idempotency,
        logger: pinoLogger,
        maxAttempts: 3,
        handlers: {
          ParcelCreated: async (env) => this.handleParcelCreated(env as EventEnvelope<ParcelCreatedData>),
          "swiftdrop.parcel.created.v1": async (env) => this.handleParcelCreated(env as EventEnvelope<ParcelCreatedData>),
          ParcelStatusChanged: async (env) => this.handleParcelStatusChanged(env as EventEnvelope<ParcelStatusChangedData>),
          "swiftdrop.parcel.status-changed.v1": async (env) => this.handleParcelStatusChanged(env as EventEnvelope<ParcelStatusChangedData>),
          ParcelAssigned: async (env) => this.handleParcelAssigned(env as EventEnvelope<ParcelAssignedData>),
          "swiftdrop.dispatch.parcel-assigned.v1": async (env) => this.handleParcelAssigned(env as EventEnvelope<ParcelAssignedData>),
          MerchantLifecycleChanged: async (env) => this.handleMerchantLifecycle(env as EventEnvelope<MerchantLifecycleData>),
          "swiftdrop.merchant.lifecycle.v1": async (env) => this.handleMerchantLifecycle(env as EventEnvelope<MerchantLifecycleData>),
        },
      });

      this.consumer = handles.consumer;
      this.producer = handles.producer;
      this.logger.log("Notification Kafka consumer initialized");
    } catch (err) {
      this.logger.error(`Failed to connect Kafka consumer: ${err}`);
    }
  }

  async onModuleDestroy(): Promise<void> {
    if (this.consumer) await this.consumer.disconnect();
    if (this.producer) await this.producer.disconnect();
    this.logger.log("Notification Kafka consumer disconnected");
  }

  async handleParcelCreated(event: EventEnvelope<ParcelCreatedData>): Promise<void> {
    const { data } = event;
    this.logger.log(`Handling ParcelCreated event: ${data.trackingNumber}`);

    // Store contact mapping for future status updates
    try {
      await this.db.parcelContacts.updateOne(
        { _id: data.trackingNumber },
        {
          $set: {
            _id: data.trackingNumber,
            parcelId: data.parcelId,
            customerId: data.customerId,
            email: data.contacts.senderEmail,
            phone: data.contacts.senderPhone || data.contacts.recipientPhone,
            recipientName: data.contacts.recipientName,
            senderName: data.contacts.senderName,
          },
        },
        { upsert: true },
      );
    } catch (err) {
      this.logger.warn(`Failed to cache parcel contacts for ${data.trackingNumber}: ${err}`);
    }

    await this.dispatcher.dispatch({
      eventId: event.eventId,
      eventType: "ParcelCreated",
      userId: data.customerId,
      toEmail: data.contacts.senderEmail,
      toPhone: data.contacts.senderPhone || data.contacts.recipientPhone,
      recipientName: data.contacts.senderName || data.contacts.recipientName,
      trackingNumber: data.trackingNumber,
      status: "CREATED",
      origin: data.pickup.city,
      destination: data.dropoff.city,
      fee: `${data.fee.currency} ${Number(data.fee.amount).toFixed(2)}`,
      templateName: "parcel-created",
    });
  }

  async handleParcelStatusChanged(event: EventEnvelope<ParcelStatusChangedData>): Promise<void> {
    const { data } = event;
    this.logger.log(`Handling ParcelStatusChanged event: ${data.trackingNumber} -> ${data.toStatus}`);

    let description = data.note;
    if (data.proof?.receivedBy) {
      description = `Received by: ${data.proof.receivedBy}${data.note ? " (" + data.note + ")" : ""}`;
    } else if (data.failureReason) {
      description = `Delivery failed: ${data.failureReason}${data.note ? " (" + data.note + ")" : ""}`;
    }

    // Lookup contact details from cached parcel contacts
    let contact: any = null;
    try {
      contact = await this.db.parcelContacts.findOne({ _id: data.trackingNumber });
      if (!contact && data.parcelId) {
        contact = await this.db.parcelContacts.findOne({ parcelId: data.parcelId });
      }
    } catch {
      // Ignore lookup failure
    }

    const toEmail = contact?.email || (data.customerId ? "customer@swiftdrop.lk" : undefined);
    const toPhone = contact?.phone || (data.customerId ? "+94771234567" : undefined);
    const recipientName = contact?.recipientName;

    await this.dispatcher.dispatch({
      eventId: event.eventId,
      eventType: "ParcelStatusChanged",
      userId: data.customerId,
      toEmail,
      toPhone,
      recipientName,
      trackingNumber: data.trackingNumber,
      status: data.toStatus,
      statusDescription: description,
      templateName: "parcel-status-changed",
    });
  }

  async handleParcelAssigned(event: EventEnvelope<ParcelAssignedData>): Promise<void> {
    const { data } = event;
    this.logger.log(`Handling ParcelAssigned event: ${data.trackingNumber} to courier ${data.courierId}`);

    await this.dispatcher.dispatch({
      eventId: event.eventId,
      eventType: "ParcelAssigned",
      trackingNumber: data.trackingNumber,
      courierName: data.courierId,
      templateName: "parcel-assigned",
    });
  }

  async handleMerchantLifecycle(event: EventEnvelope<MerchantLifecycleData>): Promise<void> {
    const { data } = event;
    this.logger.log(`Handling MerchantLifecycleChanged event: ${data.businessName} -> ${data.status}`);

    await this.dispatcher.dispatch({
      eventId: event.eventId,
      eventType: "MerchantLifecycleChanged",
      userId: data.ownerUserId,
      toEmail: data.contactEmail,
      recipientName: data.businessName,
      status: data.status,
      statusDescription: data.reason,
      templateName: "merchant-lifecycle",
    });
  }
}
