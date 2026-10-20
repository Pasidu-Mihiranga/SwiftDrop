# merchant-simulator (Phase 2 · owner M8)

A tiny "online store" used in the demo:

1. Gets a token from Keycloak with the `merchant-demo` client (client credentials, secret `KC_DEMO_MERCHANT_CLIENT_SECRET`).
2. Creates delivery orders through `POST /api/v1/merchant/orders` (with an `Idempotency-Key`).
3. Receives SwiftDrop webhooks, verifies `X-SwiftDrop-Signature`, and shows them on a page.
4. Can be told to fail (HTTP 503) to demonstrate retries and the dead-letter queue.

Quick manual check of the client-credentials flow today:

```bash
curl -s -d grant_type=client_credentials -d client_id=merchant-demo \
     -d client_secret=$KC_DEMO_MERCHANT_CLIENT_SECRET \
     http://localhost:8180/realms/swiftdrop/protocol/openid-connect/token
```
