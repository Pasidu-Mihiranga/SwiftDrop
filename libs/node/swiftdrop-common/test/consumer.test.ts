import type { EachMessagePayload } from "kafkajs";
import { createLogger, type EventConsumerOptions, type IdempotencyStore, MongoIdempotencyStore, processMessage } from "../src";

class MemoryStore implements IdempotencyStore {
  readonly seen = new Set<string>();
  async markIfFirst(eventId: string, consumer: string) {
    const key = `${consumer}:${eventId}`;
    if (this.seen.has(key)) return false;
    this.seen.add(key);
    return true;
  }
  async release(eventId: string, consumer: string) {
    this.seen.delete(`${consumer}:${eventId}`);
  }
}

const silent = createLogger({ service: "test", level: "silent" });

function message(value: string | null): EachMessagePayload {
  return {
    topic: "swiftdrop.parcel.status-changed.v1",
    partition: 0,
    message: { key: Buffer.from("p-1"), value: value === null ? null : Buffer.from(value), offset: "42", timestamp: "0", attributes: 0, headers: {} },
    heartbeat: async () => undefined,
    pause: () => () => undefined,
  } as EachMessagePayload;
}

const event = JSON.stringify({ eventId: "e-1", eventType: "ParcelStatusChanged", eventVersion: 1, producer: "parcel-service", data: {} });

function options(handler: jest.Mock, store = new MemoryStore()): EventConsumerOptions {
  return {
    groupId: "g",
    consumerName: "notification-status",
    topics: ["t"],
    handlers: { ParcelStatusChanged: handler },
    idempotency: store,
    logger: silent,
    maxAttempts: 2,
    deadLetterTopic: "swiftdrop.notification.dead-letter.v1",
  };
}

describe("processMessage (consumer template)", () => {
  it("handles an event once and ignores the redelivery", async () => {
    const handler = jest.fn().mockResolvedValue(undefined);
    const deadLetter = jest.fn();
    const opts = options(handler);

    await expect(processMessage(message(event), opts, deadLetter)).resolves.toBe("handled");
    await expect(processMessage(message(event), opts, deadLetter)).resolves.toBe("duplicate");
    expect(handler).toHaveBeenCalledTimes(1);
    expect(deadLetter).not.toHaveBeenCalled();
  });

  it("skips empty messages and unknown event types (forward compatible)", async () => {
    const handler = jest.fn();
    const deadLetter = jest.fn();
    await expect(processMessage(message(null), options(handler), deadLetter)).resolves.toBe("skipped");
    await expect(processMessage(message(event.replace("ParcelStatusChanged", "SomethingNew")), options(handler), deadLetter)).resolves.toBe("skipped");
    expect(handler).not.toHaveBeenCalled();
  });

  it("retries a failing handler and succeeds", async () => {
    const handler = jest.fn().mockRejectedValueOnce(new Error("smtp down")).mockResolvedValue(undefined);
    await expect(processMessage(message(event), options(handler), jest.fn())).resolves.toBe("handled");
    expect(handler).toHaveBeenCalledTimes(2);
  });

  it("dead-letters after the last attempt and releases the marker so a replay is processed", async () => {
    const store = new MemoryStore();
    const handler = jest.fn().mockRejectedValue(new Error("always failing"));
    const deadLetter = jest.fn().mockResolvedValue(undefined);

    await expect(processMessage(message(event), options(handler, store), deadLetter)).resolves.toBe("dead-lettered");
    expect(deadLetter).toHaveBeenCalledTimes(1);
    expect(store.seen.size).toBe(0);
  });

  it("dead-letters unparseable messages", async () => {
    const deadLetter = jest.fn().mockResolvedValue(undefined);
    await expect(processMessage(message("{not json"), options(jest.fn()), deadLetter)).resolves.toBe("dead-lettered");
  });
});

describe("MongoIdempotencyStore", () => {
  it("uses an atomic insert and treats duplicate keys as already processed", async () => {
    const ids = new Set<string>();
    const collection = {
      insertOne: jest.fn(async (doc: Record<string, unknown>) => {
        if (ids.has(doc._id as string)) throw Object.assign(new Error("dup"), { code: 11000 });
        ids.add(doc._id as string);
      }),
      deleteOne: jest.fn(async (filter: Record<string, unknown>) => ids.delete(filter._id as string)),
      createIndex: jest.fn(async () => "processedAt_1"),
    };
    const store = new MongoIdempotencyStore(collection);
    await store.ensureIndexes();

    await expect(store.markIfFirst("e-1", "tracking")).resolves.toBe(true);
    await expect(store.markIfFirst("e-1", "tracking")).resolves.toBe(false);
    await store.release("e-1", "tracking");
    await expect(store.markIfFirst("e-1", "tracking")).resolves.toBe(true);
    expect(collection.createIndex).toHaveBeenCalledWith({ processedAt: 1 }, { expireAfterSeconds: 1209600 });
  });

  it("propagates other database errors", async () => {
    const store = new MongoIdempotencyStore({
      insertOne: async () => {
        throw new Error("connection lost");
      },
      deleteOne: async () => undefined,
    });
    await expect(store.markIfFirst("e", "c")).rejects.toThrow("connection lost");
  });
});
