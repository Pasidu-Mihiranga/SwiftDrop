import type { Admin } from "kafkajs";
import { createTopicsWhenReady } from "../src";

function adminFailing(times: number) {
  let calls = 0;
  const createTopics = jest.fn(async () => {
    calls += 1;
    if (calls <= times) throw new Error("Broker -1 not found in the cached metadata");
    return true;
  });
  return { admin: { createTopics } as unknown as Admin, createTopics };
}

describe("createTopicsWhenReady", () => {
  it("creates the topics at once when the broker is ready", async () => {
    const { admin, createTopics } = adminFailing(0);
    await expect(createTopicsWhenReady(admin, { topics: [{ topic: "a" }], waitForLeaders: true })).resolves.toBe(true);
    expect(createTopics).toHaveBeenCalledWith({ topics: [{ topic: "a" }], waitForLeaders: true });
  });

  it("tries again while the broker has not published its metadata", async () => {
    const { admin, createTopics } = adminFailing(2);
    await expect(createTopicsWhenReady(admin, { topics: [{ topic: "a" }], delayMs: 1 })).resolves.toBe(true);
    expect(createTopics).toHaveBeenCalledTimes(3);
  });

  it("gives up with the last error after the given attempts", async () => {
    const { admin, createTopics } = adminFailing(5);
    await expect(createTopicsWhenReady(admin, { topics: [{ topic: "a" }], attempts: 3, delayMs: 1 })).rejects.toThrow(
      "Broker -1 not found",
    );
    expect(createTopics).toHaveBeenCalledTimes(3);
  });
});
