# M8 Webhooks and External Integration diagrams

Design diagrams for M8 (issue #21): `webhook-service` (NestJS, MongoDB, BullMQ), the merchant and admin
webhook pages in `apps/web`, and `tools/merchant-simulator`. The required items from
[../../03-team-contribution.md](../../03-team-contribution.md) are the integration design chapter and the
webhook retry activity diagram. The other diagrams support them.

| View | Source | Export |
|---|---|---|
| Use cases: webhooks | [usecase-m8-webhooks.mmd](usecase-m8-webhooks.mmd) | [SVG](usecase-m8-webhooks.svg), [PNG](usecase-m8-webhooks.png) |
| Use cases: merchant simulator | [usecase-m8-merchant-simulator.mmd](usecase-m8-merchant-simulator.mmd) | [SVG](usecase-m8-merchant-simulator.svg), [PNG](usecase-m8-merchant-simulator.png) |
| Register an endpoint, test ping | [sequence-m8-endpoint-registration.mmd](sequence-m8-endpoint-registration.mmd) | [SVG](sequence-m8-endpoint-registration.svg), [PNG](sequence-m8-endpoint-registration.png) |
| Parcel event to signed webhook | [sequence-m8-signed-delivery.mmd](sequence-m8-signed-delivery.mmd) | [SVG](sequence-m8-signed-delivery.svg), [PNG](sequence-m8-signed-delivery.png) |
| Retries, dead letter, admin replay | [sequence-m8-retry-dead-letter-replay.mmd](sequence-m8-retry-dead-letter-replay.mmd) | [SVG](sequence-m8-retry-dead-letter-replay.svg), [PNG](sequence-m8-retry-dead-letter-replay.png) |
| Merchant simulator demo | [sequence-m8-simulator-demo.mmd](sequence-m8-simulator-demo.mmd) | [SVG](sequence-m8-simulator-demo.svg), [PNG](sequence-m8-simulator-demo.png) |
| Data model | [er-m8-webhooks.mmd](er-m8-webhooks.mmd) | [SVG](er-m8-webhooks.svg), [PNG](er-m8-webhooks.png) |
| Webhook retry activity (required) | [activity-m8-webhook-retry.mmd](activity-m8-webhook-retry.mmd) | [SVG](activity-m8-webhook-retry.svg), [PNG](activity-m8-webhook-retry.png) |
| Service architecture | [architecture-m8-webhooks.mmd](architecture-m8-webhooks.mmd) | [SVG](architecture-m8-webhooks.svg), [PNG](architecture-m8-webhooks.png) |
| Integration design (required) | [integration-m8-patterns.mmd](integration-m8-patterns.mmd) | [SVG](integration-m8-patterns.svg), [PNG](integration-m8-patterns.png) |
| Class diagram | [class-m8-webhooks.mmd](class-m8-webhooks.mmd) | [SVG](class-m8-webhooks.svg), [PNG](class-m8-webhooks.png) |
| Deployment on Kubernetes | [deployment-m8-webhooks.mmd](deployment-m8-webhooks.mmd) | [SVG](deployment-m8-webhooks.svg), [PNG](deployment-m8-webhooks.png) |

## What is already built and what is design

- **Exists today:** the `webhook-service` skeleton, the shared Kafka consumer and idempotency store in
  `libs/node/swiftdrop-common`, the gateway routes, and the contracts in
  `contracts/openapi/webhook-service.v1.yaml` and `contracts/events/webhook-dead-letter.v1.schema.json`.
- **Design for M8 implementation:** everything else, including class names, collections and the simulator.
  Update the diagrams if the implementation changes them.

## Integration design: what and why

The numbers match [integration-m8-patterns](integration-m8-patterns.svg).

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
