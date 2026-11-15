import { describe, expect, it, vi } from "vitest";

// The lookup runs on the server only; in tests the marker module is replaced by an empty one.
vi.mock("server-only", () => ({}));

const { fetchPublicTracking } = await import("./tracking-api");

const view = {
  trackingNumber: "SD261020K7Q2M9X",
  currentStatus: "PICKED_UP",
  from: "Colombo",
  to: "Kandy",
  recipient: "K*** P.",
  events: [{ status: "PICKED_UP", city: "Colombo", at: "2026-10-20T09:00:00Z" }],
};

const reply = (status: number, body?: unknown) =>
  vi.fn(async () => new Response(body === undefined ? null : JSON.stringify(body), { status }));

describe("fetchPublicTracking", () => {
  it("returns the view and asks for no cache, without a token", async () => {
    const fetchMock = reply(200, view);
    const result = await fetchPublicTracking("SD261020K7Q2M9X", fetchMock as unknown as typeof fetch);
    expect(result).toEqual({ kind: "found", view });
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toMatch(/\/api\/v1\/tracking\/public\/SD261020K7Q2M9X$/);
    expect(init.cache).toBe("no-store");
    expect(JSON.stringify(init.headers)).not.toMatch(/authorization/i);
  });

  it("maps 404 to not-found", async () => {
    expect(await fetchPublicTracking("SD000000000000", reply(404) as unknown as typeof fetch)).toEqual({ kind: "not-found" });
  });

  it("maps 429 to busy", async () => {
    expect(await fetchPublicTracking("SD261020K7Q2M9X", reply(429) as unknown as typeof fetch)).toEqual({ kind: "busy" });
  });

  it("maps other failures and network errors to unavailable", async () => {
    expect(await fetchPublicTracking("SD261020K7Q2M9X", reply(500) as unknown as typeof fetch)).toEqual({ kind: "unavailable" });
    const down = vi.fn(async () => {
      throw new TypeError("fetch failed");
    });
    expect(await fetchPublicTracking("SD261020K7Q2M9X", down as unknown as typeof fetch)).toEqual({ kind: "unavailable" });
  });
});
