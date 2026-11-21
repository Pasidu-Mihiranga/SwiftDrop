# M8 Webhooks and External Integration diagrams

Design diagrams for M8 (issue #21): `webhook-service` (NestJS, MongoDB, BullMQ), the merchant and admin
webhook pages in `apps/web`, and `tools/merchant-simulator`. The required items from
[../../03-team-contribution.md](../../03-team-contribution.md) are the integration design chapter and the
webhook retry activity diagram. The other diagrams support them.

| View | Source | Export |
|---|---|---|
| Use cases: webhooks | [01-use-cases-webhooks.mmd](01-use-cases-webhooks.mmd) | [SVG](01-use-cases-webhooks.svg), [PNG](01-use-cases-webhooks.png) |
| Use cases: merchant simulator | [02-use-cases-merchant-simulator.mmd](02-use-cases-merchant-simulator.mmd) | [SVG](02-use-cases-merchant-simulator.svg), [PNG](02-use-cases-merchant-simulator.png) |
| Register an endpoint, test ping | [06-seq-endpoint-registration.mmd](06-seq-endpoint-registration.mmd) | [SVG](06-seq-endpoint-registration.svg), [PNG](06-seq-endpoint-registration.png) |
| Parcel event to signed webhook | [07-seq-signed-delivery.mmd](07-seq-signed-delivery.mmd) | [SVG](07-seq-signed-delivery.svg), [PNG](07-seq-signed-delivery.png) |
| Retries, dead letter, admin replay | [08-seq-retry-dead-letter-replay.mmd](08-seq-retry-dead-letter-replay.mmd) | [SVG](08-seq-retry-dead-letter-replay.svg), [PNG](08-seq-retry-dead-letter-replay.png) |
| Merchant simulator demo | [09-seq-simulator-demo.mmd](09-seq-simulator-demo.mmd) | [SVG](09-seq-simulator-demo.svg), [PNG](09-seq-simulator-demo.png) |
| Data model | [03-er-diagram.mmd](03-er-diagram.mmd) | [SVG](03-er-diagram.svg), [PNG](03-er-diagram.png) |
| Webhook retry activity (required) | [10-activity-webhook-retry.mmd](10-activity-webhook-retry.mmd) | [SVG](10-activity-webhook-retry.svg), [PNG](10-activity-webhook-retry.png) |
| Service architecture | [04-architecture.mmd](04-architecture.mmd) | [SVG](04-architecture.svg), [PNG](04-architecture.png) |
| Integration design (required) | [05-integration-design.mmd](05-integration-design.mmd) | [SVG](05-integration-design.svg), [PNG](05-integration-design.png) |
| Class diagram | [11-class-webhooks.mmd](11-class-webhooks.mmd) | [SVG](11-class-webhooks.svg), [PNG](11-class-webhooks.png) |
| Deployment on Kubernetes | [12-deployment-webhooks.mmd](12-deployment-webhooks.mmd) | [SVG](12-deployment-webhooks.svg), [PNG](12-deployment-webhooks.png) |

## What is already built and what is design

- **Exists today:** the `webhook-service` skeleton, the shared Kafka consumer and idempotency store in
  `libs/node/swiftdrop-common`, the gateway routes, and the contracts in
  `contracts/openapi/webhook-service.v1.yaml` and `contracts/events/webhook-dead-letter.v1.schema.json`.
- **Design for M8 implementation:** everything else, including class names, collections and the simulator.
  Update the diagrams if the implementation changes them.

## Integration design: what and why

The numbers match [05-integration-design](05-integration-design.svg).

| # | Pattern | Where | Why |
|---|---|---|---|
| 1 | Transactional outbox | parcel-service | The parcel change and its event commit together, so no event is lost or invented. |
| 2 | Publish / subscribe | Kafka | parcel-service does not need to know that webhooks exist. |
| 3 | Idempotent consumer | webhook-service | Kafka delivers at least once; `markIfFirst` stops a duplicate delivery. |
| 4 | Work queue | BullMQ on Redis | A slow merchant must not block Kafka consumption. Offsets commit once the job is queued. |
| 5 | Webhook with HMAC signature | worker to merchant | The merchant can check the sender and the body; the timestamp limits replay attacks to 5 minutes. |
| 6 | Retry with backoff | worker | Merchant outages are normal. Growing delays give it time to recover without flooding it. |
| 7 | Dead-letter channel | status DEAD + Kafka topic | Failures stay visible and recoverable instead of being retried forever. |
| 8 | Request / reply, client credentials | merchant to gateway | Order creation needs an immediate answer, and machine clients have no user login. |
| 9 | Idempotent receiver | `Idempotency-Key` | A merchant can safely repeat a request after a network error. |
| 10 | Manual replay | admin API | A person decides when the merchant has recovered. |

## Design decisions shown in the diagrams

- **webhook-service owns subscriptions and secrets.** Secrets never travel over Kafka.
- **Secrets are encrypted, not hashed,** because the worker needs the plain value to sign. The API returns
  the secret once only.
- **Merchant scope comes from the token.** Another merchant's endpoint or delivery answers 404. Replay is ADMIN only.
- **Seven attempts in total:** the first POST plus six retries, which matches `attempts: 7` in the dead-letter
  example. The schedule is not a pure exponential, so BullMQ needs a custom backoff strategy with these values.
- **SSRF checks run twice:** at registration and before each POST, because DNS can change after registration.
- **Payloads use an allow-list.** The issue names contacts, but `note` and `proof.receivedBy` can also hold
  personal data, so only listed fields are copied.
- **Each attempt signs again** with a fresh `t`. Otherwise a retry after 5 minutes would always be rejected.
  The `id` stays the same so receivers can de-duplicate.

## Open points for the implementation

- `ParcelStatusChanged` has no `merchantOrderRef`, but the webhook contract's `data` lists it. The design
  fills it from a `parcel_ref` collection written on `ParcelCreated`. The alternative is adding the field to the event.
- The contract's `type` enum has no value for the test ping. The design needs one, for example `webhook.test`,
  added to `contracts/openapi/webhook-service.v1.yaml`.
- The issue says CRUD, but the contract has no update operation. The diagrams follow the contract.
- `doc/01-architecture.md` names `webhooks.merchant_configs`; these diagrams call it `webhook_endpoint`.
- The contract's status enum has no paused value, so held deliveries stay PENDING. They need to be queued when a
  `MerchantLifecycleChanged` event returns the merchant to APPROVED.

## Style and rebuilding

The exports use the same black-and-white UML style as the M1, M2, M6 and M10 diagrams, drawn with the primitives
in `../m10/src`. The Mermaid files are the editable source; `src/build_m8.py` draws the SVG and PNG files and
writes the sequence, class and deployment `.mmd` files. It needs Google Chrome and Pillow: `python3 src/build_m8.py`.
