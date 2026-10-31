# M6 Real-time Tracking diagrams

Design diagrams for M6 (issue #19): `tracking-service` (NestJS, MongoDB read model, Socket.IO live
updates, public masked API), the M6 live components in `apps/web`, and the Node shared library
`libs/node/swiftdrop-common` (Kafka consumer template, guard, logger). Owner: M6.

Each diagram has a Mermaid source (`.mmd`, the editable file), an SVG export and a PNG export.

| View | Source | Export |
|---|---|---|
| Use cases (who can track what) | [usecase-m6-tracking.mmd](usecase-m6-tracking.mmd) | [SVG](usecase-m6-tracking.svg), [PNG](usecase-m6-tracking.png) |
| Module architecture (layers) | [architecture-m6-tracking-module.mmd](architecture-m6-tracking-module.mmd) | [SVG](architecture-m6-tracking-module.svg), [PNG](architecture-m6-tracking-module.png) |
| Class design (ports, adapters, template) | [class-m6-tracking-module.mmd](class-m6-tracking-module.mmd) | [SVG](class-m6-tracking-module.svg), [PNG](class-m6-tracking-module.png) |
| Integration design (patterns) | [integration-m6-tracking.mmd](integration-m6-tracking.mmd) | [SVG](integration-m6-tracking.svg), [PNG](integration-m6-tracking.png) |
| Real-time sequence: courier update to browser (required) | [sequence-m6-status-event-to-live-update.mmd](sequence-m6-status-event-to-live-update.mmd) | [SVG](sequence-m6-status-event-to-live-update.svg), [PNG](sequence-m6-status-event-to-live-update.png) |
| Public tracking page (guest, masked) | [sequence-m6-public-tracking-page.mmd](sequence-m6-public-tracking-page.mmd) | [SVG](sequence-m6-public-tracking-page.svg), [PNG](sequence-m6-public-tracking-page.png) |
| Socket handshake and room authorisation | [sequence-m6-socket-handshake-rooms.mmd](sequence-m6-socket-handshake-rooms.mmd) | [SVG](sequence-m6-socket-handshake-rooms.svg), [PNG](sequence-m6-socket-handshake-rooms.png) |
| Event processing activity (consumer) | [activity-m6-event-processing.mmd](activity-m6-event-processing.mmd) | [SVG](activity-m6-event-processing.svg), [PNG](activity-m6-event-processing.png) |
| Room join decision activity | [activity-m6-room-subscription.mmd](activity-m6-room-subscription.mmd) | [SVG](activity-m6-room-subscription.svg), [PNG](activity-m6-room-subscription.png) |
| Data model: timeline documents | [er-m6-tracking-timeline.mmd](er-m6-tracking-timeline.mmd) | [SVG](er-m6-tracking-timeline.svg), [PNG](er-m6-tracking-timeline.png) |
| Deployment and scaling (Redis adapter, consumer group) | [deployment-m6-scaling.mmd](deployment-m6-scaling.mmd) | [SVG](deployment-m6-scaling.svg), [PNG](deployment-m6-scaling.png) |

## Diagram details

**1. Use cases.** Four actor groups: guests (public lookup and public room only), customers,
merchants (owner timeline and toasts for their parcels), and dispatchers/admins (dispatch room). Two
internal use cases are shown: projecting Kafka events into timelines, and broadcasting `parcel.status`.
A foreign parcel returns 404 on the REST side, so a user cannot tell that someone else's parcel exists.

**2. Module architecture.** Layers inside `tracking-service`: `api` (controller, Socket.IO gateway,
rate-limit guard), `application` (query service, projector, broadcast port), `domain` (timeline
model, public-view mapper, room policy; no framework imports), and `infrastructure` (Kafka consumers,
Mongo repository, idempotency store, Socket.IO broadcaster, JWT verifier). It reuses the shared
library for the consumer template, JWT guard, logger and problem+json filter.

**3. Class design.** `KafkaConsumerTemplate` fixes the steps (validate, de-duplicate, handle, commit);
the three event consumers only implement `handle()`. `TimelineRepositoryPort` and `BroadcastPort`
are the ports; `MongoTimelineRepository` and `SocketIoBroadcaster` are the adapters. `PublicViewMapper`
is the only code allowed to build the guest view, so PII masking lives in one place.

**4. Integration design.** Numbered links: REST request/reply through the BFF and gateway (no token for
the public lookup); a Socket.IO push channel through `/ws/tracking`; publish/subscribe from
parcel-service and dispatch-service (transactional outbox, key `parcelId`); the consumer group
`tracking`; CQRS write to MongoDB; the Redis adapter between replicas; and JWKS from Keycloak.

**5. Real-time sequence (required).** Courier PATCH → parcel transaction and outbox row → relay to Kafka
→ schema validation → duplicate check on `eventId` → Mongo transaction → emit to the rooms → Redis
fan-out → owner payload to customers, masked payload to guests. Target from issue #19: the page updates
within about 1 second.

**6. Public tracking page.** Guest loads `/track/<trackingNumber>`. The REST call is rate-limited per
client IP (enumeration guard, 429 over the limit). The socket connects without a token and joins only
`tracking:<no>`. Both the REST view and the live payload carry the same masked fields (city and `K*** P.`).

**7. Socket handshake and rooms.** The BFF hands the browser a short-lived access token; refresh tokens
stay on the server. The handshake JWT is verified against Keycloak JWKS (signature, issuer, audience
`swiftdrop-api`, expiry). Each join is then checked by `RoomPolicy`: owner-only `user:<sub>`, staff-only
`dispatch`, public `tracking:<no>`. Refused joins get an error event; the socket is not disconnected.

**8. Event processing activity.** Shows the consumer's decision flow: invalid envelope → dead-letter;
duplicate `eventId` → skip; late (out-of-order) event → history only, status not moved back; transaction
errors → retry with backoff, then dead-letter. The broadcast happens only when `currentStatus` changes.

**9. Room join decision.** Activity view of the same rules for a join request: anonymous sockets may join
only public tracking rooms; signed-in sockets may join their own `user:<sub>`; `dispatch` needs the
DISPATCHER or ADMIN role; anything else is refused.

**10. Data model.** MongoDB database `tracking`, collection `timelines`. Events are embedded
(`events[]`), `publicView` is embedded and is the only part served to guests, and `processed_events`
(TTL index, 30 days) holds the idempotency markers. Keys are IDs only, as in the rest of the system.

**11. Deployment and scaling.** Two or more `tracking-service` pods share the Kafka consumer group, so
the 6 partitions of `parcel.status-changed.v1` are split across them. Every pod joins the same Redis
adapter channel, so a status event reaches a browser connected to any pod. The HPA scales on CPU and
open sockets. The ingress must allow WebSocket upgrades and a long read timeout.

## Design patterns and principles

| Pattern / principle | Where it is used |
|---|---|
| Template Method | `KafkaConsumerTemplate` (validate → de-duplicate → handle → retry → dead-letter → commit); event consumers override `handle()` |
| Observer / Publish-Subscribe | parcel-service and dispatch-service publish; tracking-service subscribes (consumer group `tracking`) |
| Idempotent Consumer | `processed_events` keyed by `eventId`; Kafka's at-least-once delivery is safe to replay |
| CQRS read model | `tracking.timelines` is built from events; the REST and socket reads never call other services |
| Repository | `TimelineRepositoryPort` with `MongoTimelineRepository` |
| Ports and Adapters (hexagonal) | `BroadcastPort` → `SocketIoBroadcaster`; `IdempotencyStorePort` → `MongoIdempotencyStore`; `JwtVerifier` wraps JWKS |
| Facade | Next.js BFF hides the token and the gateway routes from the browser |
| Gateway (API Gateway) | single entry for `/api/v1/tracking/**` and `/ws/tracking/**`, JWT check, rate limit |
| Guard / Policy | `RateLimitGuard` (public lookups); `RoomPolicy` (`tracking:`, `user:`, `dispatch` rules) |
| Retry with backoff and dead-letter | three attempts with exponential backoff, then a dead-letter record |
| Pub/sub fan-out across replicas | `@socket.io/redis-adapter` on Redis |
| Value masking (privacy by design) | `PublicViewMapper` and `maskName()` are the only route to a guest view |

**SOLID:** single responsibility (projector, query service and broadcaster are separate);
open/closed (a new event type is a new consumer, with no change to producers); dependency inversion
(application code depends on ports, not on Mongo or Socket.IO).
**Other principles:** DRY (consumer template, JWT guard and logger come from `libs/node/swiftdrop-common`);
least privilege (socket rooms are checked on every join, not only at connect); defence in depth (the gateway
and the service both verify the JWT); fail-fast config (zod-validated env); 12-factor (state in Mongo and
Redis, not in the pod); privacy by design (PII stays out of the guest payload and the logs).

## Built versus designed

- **Exists today:** the `tracking-service` skeleton (JWT guard, problem+json, metrics, `/meta/whoami`),
  the Kafka consumer template with `markIfFirst` idempotency and dead-letter support, the Mongo
  idempotency store, the PII masking logger, and the contract in
  `contracts/openapi/tracking-service.v1.yaml` (rooms and the `parcel.status` event).
- **Design for M6 implementation:** the timeline projector, `RoomPolicy`, `PublicViewMapper`, the Socket.IO
  gateway, the Redis adapter wiring and the rate-limit guard. Class and method names are planned, so update
  these diagrams if the code differs.
- **Source of truth:** `doc/01-architecture.md` (sections 1.1, 1.2, 1.3, 4, 7 and 9),
  `contracts/events/parcel-status-changed.v1.schema.json`, `libs/node/swiftdrop-common/src/kafka/consumer.ts`.

## Style

Black and white only: white fills, black outlines and text, light grey for notes and secondary boxes, and
no colour in the theme. Each source sets its own `%%{init}%%` theme header, so the rendered output does not
depend on the viewer's defaults. The Mermaid files are the editable source; if one changes, re-export its
SVG and PNG.
