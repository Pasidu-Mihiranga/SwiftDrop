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
