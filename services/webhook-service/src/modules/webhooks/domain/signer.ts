import { createHmac, timingSafeEqual } from "node:crypto";

export const SIGNATURE_HEADER = "X-SwiftDrop-Signature";
/** Receivers reject a signature older than this, which limits replay of a captured request. */
export const SIGNATURE_TOLERANCE_SECONDS = 300;

/** Hex HMAC-SHA256 of "<t>.<raw body>" with the endpoint's secret. */
export function computeSignature(secret: string, timestamp: number, rawBody: string): string {
  return createHmac("sha256", secret).update(`${timestamp}.${rawBody}`).digest("hex");
}

/** Header value {@code t=<unix seconds>,v1=<hex signature>}. Every attempt signs again with a fresh t. */
export function signatureHeader(secret: string, timestamp: number, rawBody: string): string {
  return `t=${timestamp},v1=${computeSignature(secret, timestamp, rawBody)}`;
}

/**
 * What a receiver does: parse the header, recompute over the raw body, compare in constant time and
 * reject an old timestamp. Used by the merchant simulator and by tests.
 */
export function verifySignature(header: string | undefined, rawBody: string, secret: string, nowSeconds: number,
                                toleranceSeconds = SIGNATURE_TOLERANCE_SECONDS): boolean {
  const parts = new Map((header ?? "").split(",").map((part) => {
    const index = part.indexOf("=");
    return [part.slice(0, index).trim(), part.slice(index + 1).trim()] as const;
  }));
  const timestamp = Number(parts.get("t"));
  const given = parts.get("v1") ?? "";
  if (!Number.isInteger(timestamp) || Math.abs(nowSeconds - timestamp) > toleranceSeconds) return false;
  const expected = Buffer.from(computeSignature(secret, timestamp, rawBody), "hex");
  const received = Buffer.from(given, "hex");
  return received.length === expected.length && timingSafeEqual(received, expected);
}
