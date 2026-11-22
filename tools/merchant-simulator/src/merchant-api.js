import { randomUUID } from "node:crypto";

/**
 * The store's side of the SwiftDrop merchant API: a client-credentials token (cached until shortly
 * before it expires) and `POST /api/v1/merchant/orders` with an Idempotency-Key, so a retried
 * order never becomes two parcels.
 */
export class MerchantApi {
  constructor(config, fetchImpl = fetch, clock = () => Date.now()) {
    this.config = config;
    this.fetch = fetchImpl;
    this.clock = clock;
    this.token = null;
    this.expiresAt = 0;
  }

  async accessToken() {
    if (this.token && this.clock() < this.expiresAt - 30_000) return this.token;
    const response = await this.fetch(this.config.tokenUrl, {
      method: "POST",
      headers: { "content-type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "client_credentials",
        client_id: this.config.clientId,
        client_secret: this.config.clientSecret,
      }),
    });
    if (!response.ok) throw new Error(`Keycloak refused the client credentials (HTTP ${response.status})`);
    const body = await response.json();
    this.token = body.access_token;
    this.expiresAt = this.clock() + (body.expires_in ?? 60) * 1000;
    return this.token;
  }

  /** Creates an order. Pass the same idempotencyKey to retry it safely. */
  async createOrder(order, idempotencyKey = randomUUID()) {
    const token = await this.accessToken();
    const response = await this.fetch(`${this.config.gatewayUrl}/api/v1/merchant/orders`, {
      method: "POST",
      headers: {
        authorization: `Bearer ${token}`,
        "content-type": "application/json",
        "idempotency-key": idempotencyKey,
      },
      body: JSON.stringify(order),
    });
    const text = await response.text();
    let body;
    try {
      body = text ? JSON.parse(text) : {};
    } catch {
      body = { detail: text };
    }
    return { status: response.status, idempotencyKey, body };
  }
}

/** A demo order from the simulator's form, with sensible defaults. */
export function orderFromForm(form) {
  const value = (name, fallback) => (form.get(name) ?? "").toString().trim() || fallback;
  return {
    merchantOrderRef: value("orderRef", `SIM-${Date.now()}`),
    pickup: { line1: "12 Galle Road", city: "Colombo 03", district: "Colombo" },
    dropoff: { line1: value("dropoffLine1", "45 Peradeniya Road"), city: value("dropoffCity", "Kandy"),
      district: value("dropoffDistrict", "Kandy") },
    recipient: { name: value("recipientName", "Demo Customer"), phone: value("recipientPhone", "0771234567") },
    weightKg: Number(value("weightKg", "1")),
    sizeClass: value("sizeClass", "S"),
    serviceLevel: "STANDARD",
  };
}
