import { after, before, beforeEach, describe, it } from "node:test";
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { createHandler } from "../src/app.js";
import { loadConfig } from "../src/config.js";
import { MerchantApi, orderFromForm } from "../src/merchant-api.js";
import { renderPage } from "../src/page.js";
import { sign, verify } from "../src/signature.js";
import { DemoLog } from "../src/store.js";

const SECRET = "whsec_test";
const NOW = 1_800_000_000;

describe("signature", () => {
  const body = '{"type":"parcel.created"}';

  it("accepts what SwiftDrop signs", () => {
    assert.deepEqual(verify(sign(SECRET, NOW, body), body, SECRET, NOW), { ok: true });
    assert.deepEqual(verify(sign(SECRET, NOW - 299, body), body, SECRET, NOW), { ok: true });
  });

  it("rejects a changed body, a wrong secret, an old timestamp and malformed headers", () => {
    assert.equal(verify(sign(SECRET, NOW, body), body + " ", SECRET, NOW).reason, "signature does not match");
    assert.equal(verify(sign("other", NOW, body), body, SECRET, NOW).reason, "signature does not match");
    assert.equal(verify(sign(SECRET, NOW - 301, body), body, SECRET, NOW).reason, "timestamp too old");
    assert.equal(verify("v1=abc", body, SECRET, NOW).reason, "malformed signature header");
    assert.equal(verify(`t=${NOW},v1=zz`, body, SECRET, NOW).reason, "signature does not match");
    assert.equal(verify(undefined, body, SECRET, NOW).reason, "missing signature header");
    assert.equal(verify(sign(SECRET, NOW, body), body, "", NOW).reason, "no signing secret configured");
  });
});

describe("config", () => {
  it("has local defaults and reads the environment", () => {
    const defaults = loadConfig({});
    assert.equal(defaults.port, 4000);
    assert.equal(defaults.clientId, "merchant-demo");
    assert.equal(defaults.tokenUrl, "http://localhost:8180/realms/swiftdrop/protocol/openid-connect/token");
    const custom = loadConfig({ SIMULATOR_PORT: "4100", GATEWAY_URL: "http://gw/", KC_DEMO_MERCHANT_CLIENT_SECRET: "s",
      KEYCLOAK_URL: "http://kc:8080/", WEBHOOK_SECRET: "w" });
    assert.equal(custom.port, 4100);
    assert.equal(custom.gatewayUrl, "http://gw");
    assert.equal(custom.clientSecret, "s");
    assert.equal(custom.tokenUrl, "http://kc:8080/realms/swiftdrop/protocol/openid-connect/token");
    assert.equal(custom.publicWebhookUrl, "http://merchant-simulator:4100/webhooks");
  });
});

describe("MerchantApi", () => {
  const config = loadConfig({ GATEWAY_URL: "http://gw", TOKEN_URL: "http://kc/token", MERCHANT_CLIENT_SECRET: "s" });

  it("gets a client-credentials token once and sends the Idempotency-Key", async () => {
    const calls = [];
    const fetchImpl = async (url, init) => {
      calls.push({ url, init });
      if (url === "http://kc/token") return new Response(JSON.stringify({ access_token: "tok", expires_in: 300 }));
      return new Response(JSON.stringify({ trackingNumber: "SD1" }), { status: 201 });
    };
    const api = new MerchantApi(config, fetchImpl, () => 0);

    const first = await api.createOrder({ merchantOrderRef: "A" }, "key-1");
    await api.createOrder({ merchantOrderRef: "B" });

    assert.equal(calls.filter((c) => c.url === "http://kc/token").length, 1);
    assert.equal(String(calls[0].init.body), "grant_type=client_credentials&client_id=merchant-demo&client_secret=s");
    assert.equal(calls[1].url, "http://gw/api/v1/merchant/orders");
    assert.equal(calls[1].init.headers.authorization, "Bearer tok");
    assert.equal(calls[1].init.headers["idempotency-key"], "key-1");
    assert.deepEqual(first, { status: 201, idempotencyKey: "key-1", body: { trackingNumber: "SD1" } });
    assert.match(calls[2].init.headers["idempotency-key"], /^[0-9a-f-]{36}$/);
  });

  it("refreshes an expiring token, reports a refusal and keeps a non-JSON answer", async () => {
    let tokens = 0;
    let clock = 0;
    const api = new MerchantApi(config, async (url) => {
      if (url === "http://kc/token") {
        tokens += 1;
        return new Response(JSON.stringify({ access_token: `t${tokens}`, expires_in: 60 }));
      }
      return new Response("upstream down", { status: 503 });
    }, () => clock);

    await api.accessToken();
    clock = 45_000;
    await api.accessToken();
    assert.equal(tokens, 2);
    assert.deepEqual((await api.createOrder({})).body, { detail: "upstream down" });

    const refused = new MerchantApi(config, async () => new Response("", { status: 401 }));
    await assert.rejects(refused.accessToken(), /HTTP 401/);
  });

  it("builds a demo order from the form with defaults", () => {
    const order = orderFromForm(new URLSearchParams({ orderRef: "LB-1", weightKg: "2.5", dropoffCity: "" }));
    assert.equal(order.merchantOrderRef, "LB-1");
    assert.equal(order.weightKg, 2.5);
    assert.equal(order.dropoff.city, "Kandy");
    assert.match(orderFromForm(new URLSearchParams()).merchantOrderRef, /^SIM-\d+$/);
  });
});

describe("HTTP app", () => {
  let server;
  let base;
  let log;
  const orders = [];
  const api = {
    async createOrder(order, key) {
      orders.push({ order, key });
      if (order.merchantOrderRef === "BOOM") throw new Error("token refused");
      return { status: orders.length === 1 ? 201 : 200, idempotencyKey: key, body: { trackingNumber: "SD261122AAAAAAA" } };
    },
  };

  before(async () => {
    log = new DemoLog(3);
    const handler = createHandler({ config: loadConfig({ WEBHOOK_SECRET: SECRET }), api, log, now: () => NOW });
    server = createServer((req, res) => void handler(req, res));
    await new Promise((resolve) => server.listen(0, resolve));
    base = `http://127.0.0.1:${server.address().port}`;
  });

  after(() => server.close());

  beforeEach(() => {
    log.failMode = false;
    log.webhookSecret = SECRET;
  });

  const form = (data) => ({ method: "POST", body: new URLSearchParams(data), redirect: "manual" });

  async function deliver(body, header) {
    return fetch(`${base}/webhooks`, { method: "POST", body, headers: { "x-swiftdrop-signature": header } });
  }

  it("creates an order and repeats it with the same Idempotency-Key", async () => {
    assert.equal((await fetch(`${base}/orders`, form({ orderRef: "LB-1" }))).status, 303);
    assert.equal((await fetch(`${base}/orders`, form({ orderRef: "LB-1", repeatKey: "1" }))).status, 303);
    await fetch(`${base}/orders`, form({ orderRef: "BOOM" }));

    assert.equal(orders[1].key, orders[0].key);
    assert.equal(log.orders[1].repeated, true);
    assert.equal(log.orders[0].body.detail, "token refused");
  });

  it("accepts a valid webhook and rejects a forged one", async () => {
    const body = JSON.stringify({ type: "parcel.status_changed", data: { trackingNumber: "SD261122AAAAAAA", status: "DELIVERED" } });

    assert.equal((await deliver(body, sign(SECRET, NOW, body))).status, 200);
    assert.equal((await deliver(body, sign("forged", NOW, body))).status, 401);
    assert.equal(log.webhooks[0].reason, "signature does not match");
    assert.equal(log.webhooks[1].result, "accepted");
    assert.equal((await deliver("not json", sign(SECRET, NOW, "not json"))).status, 200);
  });

  it("answers 503 in fail mode so SwiftDrop retries, and recovers", async () => {
    await fetch(`${base}/fail-mode`, form({ on: "true" }));
    assert.equal(log.failMode, true);
    const body = '{"type":"parcel.created"}';
    assert.equal((await deliver(body, sign(SECRET, NOW, body))).status, 503);

    await fetch(`${base}/fail-mode`, form({ on: "false" }));
    assert.equal((await deliver(body, sign(SECRET, NOW, body))).status, 200);
  });

  it("takes the signing secret from the page", async () => {
    await fetch(`${base}/secret`, form({ secret: " new-secret " }));
    assert.equal(log.webhookSecret, "new-secret");
    const body = "{}";
    assert.equal((await deliver(body, sign("new-secret", NOW, body))).status, 200);
  });

  it("serves the page, the log, health and 404, and refuses huge bodies", async () => {
    const page = await (await fetch(`${base}/`)).text();
    assert.match(page, /Merchant simulator/);
    assert.match(page, /http:\/\/merchant-simulator:4000\/webhooks/);
    assert.equal((await (await fetch(`${base}/health`)).json()).status, "UP");
    const state = await (await fetch(`${base}/api/log`)).json();
    assert.ok(state.webhooks.length <= 3, "the log keeps only the newest entries");
    assert.equal((await fetch(`${base}/nope`)).status, 404);
    assert.equal((await fetch(`${base}/webhooks`, { method: "POST", body: "x".repeat(1024 * 1024 + 1) })).status, 413);
  });
});

describe("page", () => {
  it("escapes everything it shows and says when lists are empty", () => {
    const log = new DemoLog();
    assert.match(renderPage({ config: loadConfig({}), log }), /No orders yet\..*No webhooks yet\./s);
    log.addOrder({ status: 422, idempotencyKey: "k", body: { code: "<script>x</script>" } });
    log.addWebhook({ result: "rejected", valid: false, reason: "<b>bad</b>", event: { type: "<i>" } });
    log.failMode = true;
    const html = renderPage({ config: loadConfig({}), log });
    assert.ok(!html.includes("<script>x"));
    assert.match(html, /&lt;script&gt;/);
    assert.match(html, /ON, every webhook gets 503/);
  });
});
