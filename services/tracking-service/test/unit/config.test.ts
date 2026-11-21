import { loadEnv } from "../../src/config/env";

describe("tracking-service configuration (Mongo and Redis)", () => {
  it("defaults to local Mongo and Redis", () => {
    const env = loadEnv({});
    expect(env.MONGO_URI).toMatch(/^mongodb:\/\//);
    expect(env.REDIS_URL).toMatch(/^redis:\/\//);
  });

  it("rejects a malformed MONGO_URI or REDIS_URL", () => {
    expect(() => loadEnv({ MONGO_URI: "http://not-mongo" })).toThrow();
    expect(() => loadEnv({ REDIS_URL: "localhost:6379" })).toThrow();
  });

  it("accepts a mongodb+srv URI and a rediss URI", () => {
    expect(() => loadEnv({ MONGO_URI: "mongodb+srv://u:p@cluster.example.net/tracking" })).not.toThrow();
    expect(() => loadEnv({ REDIS_URL: "rediss://cache.example.net:6380" })).not.toThrow();
  });
});

describe("consumer switch", () => {
  it("is on by default and can be turned off", () => {
    expect(loadEnv({}).KAFKA_CONSUMER_ENABLED).toBe(true);
    expect(loadEnv({ KAFKA_CONSUMER_ENABLED: "false" }).KAFKA_CONSUMER_ENABLED).toBe(false);
    expect(() => loadEnv({ KAFKA_CONSUMER_ENABLED: "maybe" })).toThrow();
  });
});
