import type { IdempotencyStore } from "./consumer";

/** Minimal slice of a MongoDB collection (works with the native driver and mongoose's connection.collection()). */
export interface InsertableCollection {
  insertOne(doc: Record<string, unknown>): Promise<unknown>;
  deleteOne(filter: Record<string, unknown>): Promise<unknown>;
  createIndex?(spec: Record<string, 1 | -1>, options?: Record<string, unknown>): Promise<unknown>;
}

const DUPLICATE_KEY = 11000;

/**
 * Stores processed event ids in MongoDB. The unique _id makes "check and mark" a single atomic insert,
 * and a TTL index removes markers after 14 days (far longer than any Kafka redelivery window).
 */
export class MongoIdempotencyStore implements IdempotencyStore {
  constructor(private readonly collection: InsertableCollection) {}

  async ensureIndexes(): Promise<void> {
    await this.collection.createIndex?.({ processedAt: 1 }, { expireAfterSeconds: 14 * 24 * 3600 });
  }

  async markIfFirst(eventId: string, consumer: string): Promise<boolean> {
    try {
      await this.collection.insertOne({ _id: `${consumer}:${eventId}`, eventId, consumer, processedAt: new Date() });
      return true;
    } catch (error) {
      if ((error as { code?: number }).code === DUPLICATE_KEY) return false;
      throw error;
    }
  }

  async release(eventId: string, consumer: string): Promise<void> {
    await this.collection.deleteOne({ _id: `${consumer}:${eventId}` });
  }
}
