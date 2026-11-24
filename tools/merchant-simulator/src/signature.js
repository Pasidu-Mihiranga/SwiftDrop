import { createHmac, timingSafeEqual } from "node:crypto";

export const SIGNATURE_HEADER = "x-swiftdrop-signature";

/**
 * Checks `X-SwiftDrop-Signature: t=<unix seconds>,v1=<hex HMAC-SHA256(secret, "<t>.<raw body>")>`,
 * the way a merchant's server should: over the raw body, in constant time, and rejecting a
 * timestamp more than five minutes away (replay protection). Returns the reason when it fails.
 */
export function verify(header, rawBody, secret, nowSeconds, toleranceSeconds = 300) {
  if (!secret) return { ok: false, reason: "no signing secret configured" };
  if (!header) return { ok: false, reason: "missing signature header" };
  const parts = new Map(
    String(header).split(",").map((part) => {
      const index = part.indexOf("=");
      return [part.slice(0, index).trim(), part.slice(index + 1).trim()];
    }),
  );
  const timestamp = Number(parts.get("t"));
  const given = parts.get("v1") ?? "";
  if (!Number.isInteger(timestamp)) return { ok: false, reason: "malformed signature header" };
  if (Math.abs(nowSeconds - timestamp) > toleranceSeconds) return { ok: false, reason: "timestamp too old" };
  const expected = createHmac("sha256", secret).update(`${timestamp}.${rawBody}`).digest();
  const received = Buffer.from(given, "hex");
  if (received.length !== expected.length || !timingSafeEqual(received, expected)) {
    return { ok: false, reason: "signature does not match" };
  }
  return { ok: true };
}

/** What SwiftDrop sends, for tests and for trying the receiver by hand. */
export function sign(secret, timestamp, rawBody) {
  return `t=${timestamp},v1=${createHmac("sha256", secret).update(`${timestamp}.${rawBody}`).digest("hex")}`;
}
