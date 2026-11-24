import type { Admin, ITopicConfig } from "kafkajs";

export interface CreateTopicsOptions {
  topics: ITopicConfig[];
  waitForLeaders?: boolean;
  /** Attempts before giving up (default 10). */
  attempts?: number;
  /** Pause between attempts in milliseconds (default 1000). */
  delayMs?: number;
}

/**
 * Creates topics once the broker can take the request. A freshly started broker may accept the
 * admin connection before it has published its own metadata, and KafkaJS then fails with
 * "Broker -1 not found in the cached metadata"; this waits and tries again instead.
 *
 * @returns what {@link Admin.createTopics} returned: false when every topic already existed
 */
export async function createTopicsWhenReady(admin: Admin, options: CreateTopicsOptions): Promise<boolean> {
  const { attempts = 10, delayMs = 1_000, ...request } = options;
  for (let attempt = 1; ; attempt++) {
    try {
      return await admin.createTopics(request);
    } catch (err) {
      if (attempt >= attempts) throw err;
      await new Promise((resolve) => setTimeout(resolve, delayMs));
    }
  }
}
