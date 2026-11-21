/** The demo page: plain server-rendered HTML that refreshes itself, every value escaped. */

const escapeHtml = (value) =>
  String(value ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);

function orderRow(order) {
  const tracking = order.body?.trackingNumber ?? "";
  const outcome = order.status === 201 ? "created" : order.status === 200 ? "same order returned" : order.body?.code ?? order.body?.detail ?? "failed";
  return `<tr><td>${escapeHtml(order.at)}</td><td>${escapeHtml(order.status)}</td><td>${escapeHtml(outcome)}</td>
<td class="mono">${escapeHtml(tracking)}</td><td class="mono">${escapeHtml(order.idempotencyKey)}${order.repeated ? " (repeated)" : ""}</td></tr>`;
}

function webhookRow(hook) {
  const event = hook.event ?? {};
  const status = event.data?.toStatus ?? event.data?.status ?? "";
  return `<tr class="${hook.valid ? "ok" : "bad"}"><td>${escapeHtml(hook.at)}</td><td>${escapeHtml(hook.result)}</td>
<td>${escapeHtml(hook.valid ? "valid" : hook.reason)}</td><td>${escapeHtml(event.type)}</td>
<td class="mono">${escapeHtml(event.data?.trackingNumber)}</td><td>${escapeHtml(status)}</td></tr>`;
}

export function renderPage({ config, log }) {
  return `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<meta http-equiv="refresh" content="5"><title>Merchant simulator</title>
<style>
body{font:15px/1.5 system-ui,sans-serif;margin:0;padding:16px;background:#f5f6f8;color:#15181d}
main{max-width:1000px;margin:0 auto;display:grid;gap:16px}section{background:#fff;border:1px solid #d9dde4;border-radius:10px;padding:16px}
h1{margin:0}h2{margin:0 0 8px;font-size:17px}table{width:100%;border-collapse:collapse;font-size:13px}
td,th{border-bottom:1px solid #eceef2;padding:6px;text-align:left;vertical-align:top}.mono{font-family:ui-monospace,monospace}
tr.bad td{background:#fdecec}tr.ok td{background:#eefaf1}input{padding:6px;margin:2px 0}button{padding:8px 14px;cursor:pointer}
.on{color:#b42318;font-weight:700}.wrap{display:flex;flex-wrap:wrap;gap:8px;align-items:end}label{display:grid;font-size:13px}
.table{overflow-x:auto}
</style></head><body><main>
<header><h1>Lanka Books (merchant simulator)</h1>
<p>A demo online store using the SwiftDrop merchant API as <b class="mono">${escapeHtml(config.clientId)}</b>.
Register this webhook URL in the merchant portal: <b class="mono">${escapeHtml(config.publicWebhookUrl)}</b></p></header>

<section><h2>Create an order</h2>
<form method="post" action="/orders" class="wrap">
<label>Order ref<input name="orderRef" placeholder="SIM-..."></label>
<label>Recipient<input name="recipientName" value="Demo Customer"></label>
<label>Phone<input name="recipientPhone" value="0771234567"></label>
<label>Street<input name="dropoffLine1" value="45 Peradeniya Road"></label>
<label>City<input name="dropoffCity" value="Kandy"></label>
<label>District<input name="dropoffDistrict" value="Kandy"></label>
<label>Weight (kg)<input name="weightKg" value="1" size="4"></label>
<button type="submit">Create order</button>
<button type="submit" name="repeatKey" value="1">Repeat the last order (same Idempotency-Key)</button>
</form></section>

<section><h2>Webhook receiver</h2>
<div class="wrap">
<form method="post" action="/fail-mode"><input type="hidden" name="on" value="${log.failMode ? "false" : "true"}">
<button type="submit">${log.failMode ? "Turn fail mode off" : "Turn fail mode on (answer 503)"}</button></form>
<span>Fail mode: <b class="${log.failMode ? "on" : ""}">${log.failMode ? "ON, every webhook gets 503" : "off"}</b></span>
<form method="post" action="/secret" class="wrap"><label>Signing secret (shown once in the portal)
<input name="secret" type="password" placeholder="${log.webhookSecret ? "configured" : "paste it here"}"></label>
<button type="submit">Save secret</button></form>
</div></section>

<section><h2>Orders</h2><div class="table"><table><thead><tr><th>At</th><th>HTTP</th><th>Outcome</th><th>Tracking</th><th>Idempotency-Key</th></tr></thead>
<tbody>${log.orders.map(orderRow).join("") || '<tr><td colspan="5">No orders yet.</td></tr>'}</tbody></table></div></section>

<section><h2>Webhooks received</h2><div class="table"><table><thead><tr><th>At</th><th>Result</th><th>Signature</th><th>Event</th><th>Tracking</th><th>Status</th></tr></thead>
<tbody>${log.webhooks.map(webhookRow).join("") || '<tr><td colspan="6">No webhooks yet.</td></tr>'}</tbody></table></div></section>
</main></body></html>`;
}
