import { beforeEach, describe, expect, it, vi } from "vitest";
import type { TrackingView } from "./tracking";

type Handler = (...args: unknown[]) => void;
const fake = {
  handlers: new Map<string, Handler>(),
  auth: undefined as undefined | ((cb: (data: object) => void) => void),
  emitted: [] as { event: string; payload: unknown }[],
  disconnect: vi.fn(),
  removeAllListeners: vi.fn(),
};

vi.mock("socket.io-client", () => ({
  io: (_url: string, options: { auth?: unknown }) => {
    fake.auth = options.auth as typeof fake.auth;
    return {
      on: (event: string, handler: Handler) => fake.handlers.set(event, handler),
      emitWithAck: (event: string, payload: unknown) => {
        fake.emitted.push({ event, payload });
        return Promise.resolve({ ok: true });
      },
      disconnect: fake.disconnect,
      removeAllListeners: fake.removeAllListeners,
    };
  },
}));

const { fetchSocketSession, followParcel, followSession } = await import("./tracking-socket");

const view: TrackingView = {
  trackingNumber: "SD261020K7Q2M9X",
  currentStatus: "PICKED_UP",
  from: "Colombo",
  to: "Kandy",
  recipient: "K*** P.",
  events: [{ status: "PICKED_UP", city: "Colombo", at: "2026-10-20T09:00:00Z" }],
};

const flush = () => new Promise((resolve) => setTimeout(resolve, 0));

beforeEach(() => {
  fake.handlers.clear();
  fake.auth = undefined;
  fake.emitted.length = 0;
  fake.disconnect.mockClear();
  fake.removeAllListeners.mockClear();
});

describe("fetchSocketSession", () => {
  it("returns the ticket and rooms from the BFF", async () => {
    const fetchMock = vi.fn(
      async () => new Response(JSON.stringify({ ticket: "t1", rooms: ["user:u1"], expiresIn: 60 }), { status: 201 }),
    );
    const session = await fetchSocketSession(fetchMock as unknown as typeof fetch);
    expect(session).toEqual({ ticket: "t1", rooms: ["user:u1"], expiresIn: 60 });
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("/api/bff/api/v1/tracking/socket-tickets");
    expect(init.method).toBe("POST");
  });

  it("throws when the ticket is refused", async () => {
    const refused = vi.fn(async () => new Response(null, { status: 401 }));
    await expect(fetchSocketSession(refused as unknown as typeof fetch)).rejects.toThrow("401");
  });
});

describe("followParcel", () => {
  it("joins the public room on connect and reports pushed views", async () => {
    const onUpdate = vi.fn();
    const onState = vi.fn();
    const stop = followParcel(view.trackingNumber, onUpdate, onState);
    fake.handlers.get("connect")?.();
    await flush();
    expect(fake.emitted).toEqual([{ event: "join", payload: { room: "tracking:SD261020K7Q2M9X" } }]);
    expect(onState).toHaveBeenCalledWith("live");

    fake.handlers.get("parcel.status")?.(view);
    expect(onUpdate).toHaveBeenCalledWith(view);

    fake.handlers.get("disconnect")?.();
    expect(onState).toHaveBeenLastCalledWith("reconnecting");

    stop();
    expect(fake.disconnect).toHaveBeenCalled();
  });
});

describe("followSession", () => {
  it("takes a fresh ticket for the handshake and joins the returned rooms", async () => {
    const fetchSession = vi.fn(async () => ({ ticket: "ticket-1", rooms: ["user:u1", "dispatch"] }));
    const onState = vi.fn();
    followSession(() => undefined, onState, fetchSession);

    const handshake = vi.fn();
    fake.auth?.(handshake);
    await flush();
    expect(fetchSession).toHaveBeenCalledTimes(1);
    expect(handshake).toHaveBeenCalledWith({ ticket: "ticket-1" });

    fake.handlers.get("connect")?.();
    await flush();
    expect(fake.emitted.map((e) => e.payload)).toEqual([{ room: "user:u1" }, { room: "dispatch" }]);
    expect(onState).toHaveBeenCalledWith("live");
  });

  it("connects anonymously when no ticket can be obtained", async () => {
    const onState = vi.fn();
    followSession(
      () => undefined,
      onState,
      async () => {
        throw new Error("refused");
      },
    );
    const handshake = vi.fn();
    fake.auth?.(handshake);
    await flush();
    expect(handshake).toHaveBeenCalledWith({});
    expect(onState).toHaveBeenCalledWith("reconnecting");
  });
});
