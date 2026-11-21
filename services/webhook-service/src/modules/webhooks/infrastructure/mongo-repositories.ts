import type { Collection, Db } from "mongodb";
import type {
  DeliveryChange, DeliveryPage, DeliveryRepository, EndpointRepository, MerchantStateRepository, ParcelRefRepository,
} from "../application/ports";
import type { DeliveryAttempt, DeliveryStatus, WebhookDelivery, WebhookEndpoint, WebhookEventType } from "../domain/types";

const DUPLICATE_KEY = 11000;
/** A parcel reference only needs to outlive the parcel's journey, so it expires after 90 days. */
const PARCEL_REF_TTL_SECONDS = 90 * 24 * 3600;

type EndpointDoc = Omit<WebhookEndpoint, "id"> & { _id: string };
type DeliveryDoc = Omit<WebhookDelivery, "id"> & { _id: string };
type MerchantStateDoc = { _id: string; status: string; changedAt: Date };
type ParcelRefDoc = { _id: string; merchantOrderRef: string; createdAt: Date };

function fromDoc<T extends { _id: string }>({ _id, ...rest }: T): Omit<T, "_id"> & { id: string } {
  return { id: _id, ...rest };
}

/** Collection webhook_endpoint. */
export class MongoEndpointRepository implements EndpointRepository {
  private readonly rows: Collection<EndpointDoc>;

  constructor(db: Db) {
    this.rows = db.collection<EndpointDoc>("webhook_endpoint");
  }

  async ensureIndexes(): Promise<void> {
    await this.rows.createIndex({ merchantId: 1, eventTypes: 1, active: 1 });
  }

  async insert({ id, ...rest }: WebhookEndpoint): Promise<void> {
    await this.rows.insertOne({ _id: id, ...rest });
  }

  async findById(id: string): Promise<WebhookEndpoint | null> {
    const doc = await this.rows.findOne({ _id: id });
    return doc ? fromDoc(doc) : null;
  }

  async findOwned(id: string, merchantId: string): Promise<WebhookEndpoint | null> {
    const doc = await this.rows.findOne({ _id: id, merchantId });
    return doc ? fromDoc(doc) : null;
  }

  async listByMerchant(merchantId: string): Promise<WebhookEndpoint[]> {
    return (await this.rows.find({ merchantId }).sort({ createdAt: -1 }).toArray()).map(fromDoc);
  }

  async findSubscribed(merchantId: string, eventType: WebhookEventType): Promise<WebhookEndpoint[]> {
    return (await this.rows.find({ merchantId, eventTypes: eventType, active: true }).toArray()).map(fromDoc);
  }

  async deleteOwned(id: string, merchantId: string): Promise<boolean> {
    return (await this.rows.deleteOne({ _id: id, merchantId })).deletedCount === 1;
  }
}

/** Collection webhook_delivery, with each attempt embedded in the delivery it belongs to. */
export class MongoDeliveryRepository implements DeliveryRepository {
  private readonly rows: Collection<DeliveryDoc>;

  constructor(db: Db) {
    this.rows = db.collection<DeliveryDoc>("webhook_delivery");
  }

  async ensureIndexes(): Promise<void> {
    await Promise.all([
      this.rows.createIndex({ endpointId: 1, eventId: 1 }, { unique: true }),
      this.rows.createIndex({ merchantId: 1, createdAt: -1 }),
      this.rows.createIndex({ status: 1, createdAt: -1 }),
    ]);
  }

  async insertPending({ id, ...rest }: WebhookDelivery): Promise<boolean> {
    try {
      await this.rows.insertOne({ _id: id, ...rest });
      return true;
    } catch (error) {
      if ((error as { code?: number }).code === DUPLICATE_KEY) return false;
      throw error;
    }
  }

  async findById(id: string): Promise<WebhookDelivery | null> {
    const doc = await this.rows.findOne({ _id: id });
    return doc ? fromDoc(doc) : null;
  }

  async list(filter: { merchantId?: string; status?: DeliveryStatus }, page: number, size: number): Promise<DeliveryPage> {
    const [docs, total] = await Promise.all([
      this.rows.find(filter).sort({ createdAt: -1 }).skip(page * size).limit(size).toArray(),
      this.rows.countDocuments(filter),
    ]);
    return { items: docs.map(fromDoc), total };
  }

  async findPendingByMerchant(merchantId: string): Promise<WebhookDelivery[]> {
    return (await this.rows.find({ merchantId, status: "PENDING" }).sort({ createdAt: 1 }).toArray()).map(fromDoc);
  }

  async update(id: string, change: DeliveryChange, attempt?: DeliveryAttempt): Promise<void> {
    const { nextRetryAt, ...set } = change;
    await this.rows.updateOne({ _id: id }, {
      $set: { ...set, ...(nextRetryAt ? { nextRetryAt } : {}) },
      ...(nextRetryAt === null ? { $unset: { nextRetryAt: "" } } : {}),
      ...(attempt ? { $push: { attempts: attempt } } : {}),
    });
  }
}

/** Collection merchant_state, projected from MerchantLifecycleChanged. */
export class MongoMerchantStateRepository implements MerchantStateRepository {
  private readonly rows: Collection<MerchantStateDoc>;

  constructor(db: Db) {
    this.rows = db.collection<MerchantStateDoc>("merchant_state");
  }

  async isSuspended(merchantId: string): Promise<boolean> {
    return (await this.rows.findOne({ _id: merchantId }))?.status === "SUSPENDED";
  }

  /** An older event arriving late never overwrites a newer status. */
  async save(merchantId: string, status: string, changedAt: Date): Promise<void> {
    await this.rows.updateOne({ _id: merchantId, $or: [{ changedAt: { $lte: changedAt } }, { changedAt: { $exists: false } }] },
      { $set: { status, changedAt } }, { upsert: true }).catch((error: { code?: number }) => {
      if (error.code !== DUPLICATE_KEY) throw error;
    });
  }
}

/** Collection parcel_ref: the merchant's order reference, remembered from ParcelCreated. */
export class MongoParcelRefRepository implements ParcelRefRepository {
  private readonly rows: Collection<ParcelRefDoc>;

  constructor(db: Db) {
    this.rows = db.collection<ParcelRefDoc>("parcel_ref");
  }

  async ensureIndexes(): Promise<void> {
    await this.rows.createIndex({ createdAt: 1 }, { expireAfterSeconds: PARCEL_REF_TTL_SECONDS });
  }

  async save(parcelId: string, merchantOrderRef: string): Promise<void> {
    await this.rows.updateOne({ _id: parcelId }, { $setOnInsert: { merchantOrderRef, createdAt: new Date() } }, { upsert: true });
  }

  async findMerchantOrderRef(parcelId: string): Promise<string | null> {
    return (await this.rows.findOne({ _id: parcelId }))?.merchantOrderRef ?? null;
  }
}
