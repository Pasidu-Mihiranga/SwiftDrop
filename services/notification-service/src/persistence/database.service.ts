import { Injectable, type OnModuleInit, type OnModuleDestroy, Logger } from "@nestjs/common";
import { MongoClient, type Db, type Collection } from "mongodb";
import { env } from "../config/env";

export interface NotificationDoc {
  _id: string; // UUID
  userId: string;
  title: string;
  message: string;
  type: string;
  trackingNumber?: string;
  metadata?: Record<string, unknown>;
  read: boolean;
  createdAt: Date;
}

export interface NotificationPreferenceDoc {
  _id: string; // userId
  email: boolean;
  sms: boolean;
  events: string[];
  updatedAt: Date;
}

export interface SmsOutboxDoc {
  _id: string; // UUID
  recipientPhoneMasked: string;
  recipientPhoneRaw: string;
  body: string;
  eventType: string;
  trackingNumber?: string;
  status: "SENT" | "PENDING" | "FAILED";
  sentAt: Date;
}

export interface ParcelContactDoc {
  _id: string; // trackingNumber
  parcelId: string;
  customerId?: string;
  email?: string;
  phone?: string;
  recipientName?: string;
  senderName?: string;
}

export interface NotificationLogDoc {
  _id: string; // UUID
  eventId: string;
  eventType: string;
  channel: "EMAIL" | "SMS" | "IN_APP";
  recipient: string;
  status: "SUCCESS" | "FAILED";
  error?: string;
  createdAt: Date;
}

@Injectable()
export class DatabaseService implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(DatabaseService.name);
  private client!: MongoClient;
  private db!: Db;
  private initPromise?: Promise<void>;

  async ensureConnected(): Promise<Db> {
    if (this.db) return this.db;
    if (!this.initPromise) {
      this.initPromise = (async () => {
        this.client = new MongoClient(env.MONGO_URI);
        await this.client.connect();
        this.db = this.client.db();
        this.logger.log(`Connected to MongoDB: ${this.db.databaseName}`);

        // Ensure Indexes
        await this.notifications.createIndex({ userId: 1, createdAt: -1 });
        await this.smsOutbox.createIndex({ sentAt: -1 });
        await this.notificationLogs.createIndex({ eventId: 1 });
        await this.idempotency.createIndex({ processedAt: 1 }, { expireAfterSeconds: 14 * 24 * 3600 });
      })();
    }
    await this.initPromise;
    return this.db;
  }

  async onModuleInit(): Promise<void> {
    await this.ensureConnected();
  }

  async onModuleDestroy(): Promise<void> {
    if (this.client) {
      await this.client.close();
      this.logger.log("Disconnected from MongoDB");
    }
  }

  get notifications(): Collection<NotificationDoc> {
    return this.db.collection<NotificationDoc>("notifications");
  }

  get preferences(): Collection<NotificationPreferenceDoc> {
    return this.db.collection<NotificationPreferenceDoc>("notification_preferences");
  }

  get smsOutbox(): Collection<SmsOutboxDoc> {
    return this.db.collection<SmsOutboxDoc>("sms_outbox");
  }

  get notificationLogs(): Collection<NotificationLogDoc> {
    return this.db.collection<NotificationLogDoc>("notification_logs");
  }

  get idempotency(): Collection {
    return this.db.collection("consumer_idempotency");
  }

  get parcelContacts(): Collection<ParcelContactDoc> {
    return this.db.collection<ParcelContactDoc>("parcel_contacts");
  }
}
