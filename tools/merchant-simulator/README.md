# merchant-simulator (owner M8)

A tiny "online store" for the demo. It talks to SwiftDrop only through the public merchant API and
webhooks, exactly as a real merchant's system would.

| | |
|---|---|
| Page | http://localhost:4010 (compose; `MERCHANT_SIMULATOR_HOST_PORT`) |
| Webhook URL to register | `http://merchant-simulator:4000/webhooks` (inside the compose network) |
| Runtime | Node 22, no dependencies (`node:http`, `node:crypto`, `node:test`) |

## What it does

1. **Orders:** gets a client-credentials token from Keycloak (`merchant-demo` by default, cached until shortly
   before it expires) and creates orders with `POST /api/v1/merchant/orders` and an `Idempotency-Key`. The
   "Repeat the last order" button resends the same key, so SwiftDrop returns the same parcel instead of a second one.
2. **Webhooks:** receives `POST /webhooks`, verifies `X-SwiftDrop-Signature` (`t=<unix seconds>,v1=<hex
   HMAC-SHA256(secret, "<t>.<raw body>")>`) over the raw body in constant time, and rejects a timestamp more than
   five minutes old. Valid events get 200; forged or stale ones get 401 and are shown in red.
3. **Fail mode:** a toggle that answers every webhook with 503, to show SwiftDrop's retries, the dead-letter state
   and the admin replay after it is switched off again.

## Demo

```bash
pnpm stack:up                    # includes merchant-simulator (profile apps)
```

1. Sign in as the merchant, open **Webhooks**, add `http://merchant-simulator:4000/webhooks` for
   `parcel.created` and `parcel.status_changed`, and copy the signing secret (shown once).
2. Paste it on the simulator page (**Signing secret**), or set `MERCHANT_SIMULATOR_WEBHOOK_SECRET` and restart it.
3. **Create order** on the simulator page: the order appears with its tracking number, and a signed
   `parcel.created` arrives within seconds.
4. Move the parcel on (dispatch assigns it, the courier updates it): `parcel.status_changed` arrives.
5. Turn **fail mode** on, change the parcel again: deliveries get 503 and are retried
   (`WEBHOOK_BACKOFF_SECONDS` shortens the delays) until they are dead-lettered; turn fail mode off and replay
   them from **Admin, Webhooks**.

To use a merchant approved through the portal instead of `merchant-demo`, set
`MERCHANT_SIMULATOR_CLIENT_ID` and `MERCHANT_SIMULATOR_CLIENT_SECRET` to the credentials from its
**API credentials** page.

## Configuration

| Variable | Default | |
|---|---|---|
| `GATEWAY_URL` | `http://localhost:8080` | SwiftDrop API gateway |
| `KEYCLOAK_URL`, `KEYCLOAK_REALM` | `http://localhost:8180`, `swiftdrop` | Token endpoint is derived (or set `TOKEN_URL`) |
| `MERCHANT_CLIENT_ID`, `MERCHANT_CLIENT_SECRET` | `merchant-demo`, `KC_DEMO_MERCHANT_CLIENT_SECRET` | Client-credentials client |
| `WEBHOOK_SECRET` | empty | Endpoint signing secret; can also be pasted on the page |
| `PUBLIC_WEBHOOK_URL` | `http://merchant-simulator:<port>/webhooks` | Shown on the page to copy into the portal |
| `SIMULATOR_PORT` | `4000` | |

## Run and test

```bash
cd tools/merchant-simulator
node src/main.js     # standalone, against a locally running stack
pnpm test            # node:test, coverage gate 70%
```
