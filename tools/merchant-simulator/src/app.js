import { randomUUID } from "node:crypto";
import { orderFromForm } from "./merchant-api.js";
import { renderPage } from "./page.js";
import { SIGNATURE_HEADER, verify } from "./signature.js";

const MAX_BODY_BYTES = 1024 * 1024;

/** Reads the body; past 1 MB it stops keeping it but reads to the end, so the caller still gets a 413. */
function readBody(request) {
  return new Promise((resolve, reject) => {
    let size = 0;
    let tooLarge = false;
    const chunks = [];
    request.on("data", (chunk) => {
      size += chunk.length;
      if (size > MAX_BODY_BYTES) tooLarge = true;
      else chunks.push(chunk);
    });
    request.on("end", () => (tooLarge ? reject(new Error("body too large")) : resolve(Buffer.concat(chunks).toString("utf8"))));
    request.on("error", reject);
  });
}

function send(response, status, body, type = "application/json") {
  response.writeHead(status, { "content-type": `${type}; charset=utf-8`, "cache-control": "no-store" });
  response.end(typeof body === "string" ? body : JSON.stringify(body));
}

function redirectHome(response) {
  response.writeHead(303, { location: "/" });
  response.end();
}

/**
 * The simulator's HTTP handler: the demo page, the order form, the webhook receiver and the
 * fail-mode switch. Dependencies are passed in so tests can drive it without a network.
 */
export function createHandler({ config, api, log, now = () => Math.floor(Date.now() / 1000) }) {
  log.webhookSecret = log.webhookSecret || config.webhookSecret;

  return async function handle(request, response) {
    const url = new URL(request.url ?? "/", "http://simulator");
    try {
      if (request.method === "GET" && url.pathname === "/") {
        return send(response, 200, renderPage({ config, log }), "text/html");
      }
      if (request.method === "GET" && url.pathname === "/health") {
        return send(response, 200, { status: "UP" });
      }
      if (request.method === "GET" && url.pathname === "/api/log") {
        return send(response, 200, { failMode: log.failMode, orders: log.orders, webhooks: log.webhooks });
      }
      if (request.method === "POST" && url.pathname === "/webhooks") {
        return await receiveWebhook(request, response);
      }
      if (request.method === "POST" && url.pathname === "/orders") {
        const form = new URLSearchParams(await readBody(request));
        const repeat = form.get("repeatKey");
        const key = repeat && log.orders[0]?.idempotencyKey ? log.orders[0].idempotencyKey : randomUUID();
        try {
          const result = await api.createOrder(orderFromForm(form), key);
          log.addOrder({ ...result, repeated: Boolean(repeat) });
        } catch (error) {
          log.addOrder({ status: 0, idempotencyKey: key, body: { detail: error.message } });
        }
        return redirectHome(response);
      }
      if (request.method === "POST" && url.pathname === "/fail-mode") {
        const form = new URLSearchParams(await readBody(request));
        log.failMode = form.get("on") === "true";
        return redirectHome(response);
      }
      if (request.method === "POST" && url.pathname === "/secret") {
        const form = new URLSearchParams(await readBody(request));
        log.webhookSecret = (form.get("secret") ?? "").trim();
        return redirectHome(response);
      }
      return send(response, 404, { error: "not found" });
    } catch (error) {
      return send(response, error.message === "body too large" ? 413 : 500, { error: error.message });
    }
  };

  async function receiveWebhook(request, response) {
    const raw = await readBody(request);
    const check = verify(request.headers[SIGNATURE_HEADER], raw, log.webhookSecret, now(), config.toleranceSeconds);
    let event = null;
    try {
      event = JSON.parse(raw);
    } catch {
      // logged as-is below
    }
    if (log.failMode) {
      log.addWebhook({ result: "rejected (fail mode)", valid: check.ok, reason: check.reason, event });
      return send(response, 503, { error: "fail mode is on" });
    }
    if (!check.ok) {
      log.addWebhook({ result: "rejected", valid: false, reason: check.reason, event });
      return send(response, 401, { error: check.reason });
    }
    log.addWebhook({ result: "accepted", valid: true, event });
    return send(response, 200, { received: true });
  }
}
