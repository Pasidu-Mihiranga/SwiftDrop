import { io, type Socket } from "socket.io-client";
import type { TrackingView } from "@/lib/tracking";

export type LiveState = "connecting" | "live" | "reconnecting";

/**
 * Gateway address for the browser. NEXT_PUBLIC_ values are public by design: the socket is anonymous for
 * public rooms, so no token is involved here.
 */
export function trackingSocketUrl(): string {
  return (process.env.NEXT_PUBLIC_GATEWAY_URL ?? "http://localhost:8080").replace(/\/$/, "");
}

/**
 * Joins the public room of one parcel and reports each pushed view. Returns a function that disconnects.
 * Socket.IO reconnects by itself; every reconnect joins the room again.
 */
export function followParcel(
  trackingNumber: string,
  onUpdate: (view: TrackingView) => void,
  onState: (state: LiveState) => void,
): () => void {
  const socket: Socket = io(trackingSocketUrl(), {
    path: "/ws/tracking",
    transports: ["websocket"],
    reconnectionDelayMax: 10_000,
  });

  const join = () => {
    socket
      .emitWithAck("join", { room: `tracking:${trackingNumber}` })
      .then((reply: { ok: boolean }) => onState(reply.ok ? "live" : "reconnecting"))
      .catch(() => onState("reconnecting"));
  };

  socket.on("connect", join);
  socket.on("disconnect", () => onState("reconnecting"));
  socket.on("parcel.status", (view: TrackingView) => onUpdate(view));

  return () => {
    socket.removeAllListeners();
    socket.disconnect();
  };
}

/** Rooms and a single-use ticket for a signed-in user, from the BFF (the access token stays on the server). */
export interface SocketSession {
  ticket: string;
  rooms: string[];
}

export async function fetchSocketSession(fetchImpl: typeof fetch = fetch): Promise<SocketSession> {
  const res = await fetchImpl("/api/bff/api/v1/tracking/socket-tickets", {
    method: "POST",
    credentials: "same-origin",
    headers: { accept: "application/json" },
  });
  if (!res.ok) throw new Error(`socket ticket refused: ${res.status}`);
  return (await res.json()) as SocketSession;
}

/**
 * Follows everything this signed-in user may see live: their own rooms (and merchant or dispatch rooms
 * for those roles). Every connection and reconnection uses a fresh ticket, because tickets are single use.
 * If no ticket can be obtained the socket connects anonymously and only public rooms are joined.
 */
export function followSession(
  onUpdate: (view: TrackingView) => void,
  onState: (state: LiveState) => void,
  fetchSession: () => Promise<SocketSession> = () => fetchSocketSession(),
): () => void {
  let rooms: string[] = [];
  const socket: Socket = io(trackingSocketUrl(), {
    path: "/ws/tracking",
    transports: ["websocket"],
    reconnectionDelayMax: 10_000,
    auth: (callback) => {
      fetchSession()
        .then((session) => {
          rooms = session.rooms;
          callback({ ticket: session.ticket });
        })
        .catch(() => {
          rooms = [];
          onState("reconnecting");
          callback({});
        });
    },
  });

  socket.on("connect", () => {
    if (rooms.length === 0) {
      onState("live");
      return;
    }
    Promise.all(rooms.map((room) => socket.emitWithAck("join", { room })))
      .then((replies: { ok: boolean }[]) => onState(replies.every((r) => r.ok) ? "live" : "reconnecting"))
      .catch(() => onState("reconnecting"));
  });
  socket.on("disconnect", () => onState("reconnecting"));
  socket.on("parcel.status", (view: TrackingView) => onUpdate(view));

  return () => {
    socket.removeAllListeners();
    socket.disconnect();
  };
}
