# M6 Real-time Tracking diagrams

Design diagrams for M6 (issue #19): `tracking-service` (NestJS, MongoDB read model, Socket.IO live
updates, public masked API), the M6 live components in `apps/web`, and the Node shared library
`libs/node/swiftdrop-common`. Owner: M6. Style and structure follow the M10 design document.

Each figure has three files: the Mermaid source (`.mmd`, editable), an SVG and a PNG. The SVG and PNG
are drawn by hand in black, white and light grey to match the M10 design document. If a `.mmd`
changes, redraw its SVG and PNG to match.

## Figures

| Figure | Diagram | Source | Export |
|---|---|---|---|
| 1 | Use cases: who can track what | [usecase-m6-tracking.mmd](usecase-m6-tracking.mmd) | [SVG](usecase-m6-tracking.svg), [PNG](usecase-m6-tracking.png) |
| 2 | Data model: timeline documents and references | [er-m6-tracking-timeline.mmd](er-m6-tracking-timeline.mmd) | [SVG](er-m6-tracking-timeline.svg), [PNG](er-m6-tracking-timeline.png) |
| 3 | Architecture: four tiers | [architecture-m6-tracking-module.mmd](architecture-m6-tracking-module.mmd) | [SVG](architecture-m6-tracking-module.svg), [PNG](architecture-m6-tracking-module.png) |
| 4 | Integration design: numbered patterns | [integration-m6-tracking.mmd](integration-m6-tracking.mmd) | [SVG](integration-m6-tracking.svg), [PNG](integration-m6-tracking.png) |
| 5 | Sequence: courier update to live browser (required) | [sequence-m6-status-event-to-live-update.mmd](sequence-m6-status-event-to-live-update.mmd) | [SVG](sequence-m6-status-event-to-live-update.svg), [PNG](sequence-m6-status-event-to-live-update.png) |
| 6 | Sequence: public tracking page (guest, masked) | [sequence-m6-public-tracking-page.mmd](sequence-m6-public-tracking-page.mmd) | [SVG](sequence-m6-public-tracking-page.svg), [PNG](sequence-m6-public-tracking-page.png) |
| 7 | Sequence: socket handshake and room authorisation | [sequence-m6-socket-handshake-rooms.mmd](sequence-m6-socket-handshake-rooms.mmd) | [SVG](sequence-m6-socket-handshake-rooms.svg), [PNG](sequence-m6-socket-handshake-rooms.png) |
| 8 | Sequence: owner timeline (404 for other users) | [sequence-m6-owner-timeline.mmd](sequence-m6-owner-timeline.mmd) | [SVG](sequence-m6-owner-timeline.svg), [PNG](sequence-m6-owner-timeline.png) |
| 9 | Activity: processing one parcel event | [activity-m6-event-processing.mmd](activity-m6-event-processing.mmd) | [SVG](activity-m6-event-processing.svg), [PNG](activity-m6-event-processing.png) |
| 10 | Activity: join a socket room | [activity-m6-room-subscription.mmd](activity-m6-room-subscription.mmd) | [SVG](activity-m6-room-subscription.svg), [PNG](activity-m6-room-subscription.png) |
| 11 | Class design: tracking-service | [class-m6-tracking-module.mmd](class-m6-tracking-module.mmd) | [SVG](class-m6-tracking-module.svg), [PNG](class-m6-tracking-module.png) |
| 12 | Class design: shared library (platform share) | [class-m6-common-lib.mmd](class-m6-common-lib.mmd) | [SVG](class-m6-common-lib.svg), [PNG](class-m6-common-lib.png) |
| 13 | Deployment and scaling | [deployment-m6-scaling.mmd](deployment-m6-scaling.mmd) | [SVG](deployment-m6-scaling.svg), [PNG](deployment-m6-scaling.png) |

## 1 Use cases

tracking-service serves four kinds of actor. Guests and customers look up a parcel by its tracking
number and can follow it live. Merchants and customers see the full timeline of their own parcels.
Dispatchers and admins watch the live dispatcher board. The public lookup includes a rate limit per
IP, so tracking numbers cannot be enumerated. Parcel events come from parcel-service and are projected
into timelines. Couriers have no tracking use case: their status updates are the data the timeline shows.

## 2 Data model

The timeline is a MongoDB document keyed by `trackingNumber`, with its events embedded. `processed_event`
keeps duplicate Kafka deliveries from being counted twice. Redis holds the Socket.IO adapter channels and
the per-IP counters for the public lookup. Parcel and user records belong to other services and are
referenced by id only, with no foreign keys.

## 3 Architecture

The browser never holds the access token. Four tiers:

- **Client:** public tracking page, live timeline and toasts, dispatcher board.
- **Transport:** Next.js BFF (`/api/bff/socket-token`, token stays server-side) and Spring Cloud Gateway
  (`/api/v1/tracking/**`, `/ws/tracking`, JWT check).
- **Application:** tracking-service (NestJS) and the `swiftdrop-common` library.
- **Data and infrastructure:** MongoDB `tracking`, Kafka (KRaft), Redis 7.

Why this style: live tracking has a very different load from parcel writes, so tracking-service scales
on its own. It reads from events, so a tracking outage never blocks bookings. Socket.IO runs in Node,
which suits I/O-heavy fan-out.

## 4 Integration design

Asynchronous patterns build the read model; synchronous patterns serve users. Numbers match the figure.

| # | Pattern | Where used | Why |
|---|---|---|---|
| 1 | Transactional outbox | parcel-service writes the outbox row with the status change | Event and data commit together, so tracking never misses or invents a status. |
| 2 | Publish/subscribe (Kafka) | topics `swiftdrop.parcel.created.v1`, `swiftdrop.parcel.status-changed.v1`, `swiftdrop.dispatch.parcel-assigned.v1` | One status change feeds tracking, notification, webhook and reporting without the producer knowing them. |
| 3 | Competing consumers | consumer group `tracking-service`, key = parcelId | Replicas share partitions; per-parcel order is kept. |
| 4 | Idempotent consumer | `processed_event` (unique eventId + consumer) | Kafka delivers at least once; duplicates must not change the timeline. |
| 5 | CQRS read model | MongoDB `timelines` | Public and owner reads are single document lookups, with no joins across services. |
| 6 | Request/reply (REST) | `GET /api/v1/tracking/public/{no}`, `GET /api/v1/tracking/parcels/{id}/timeline` | The page needs an immediate answer for a tracking number. |
| 7 | WebSocket push | Socket.IO at `/ws/tracking`, event `parcel.status` | Live updates without polling. |
| 8 | Pub/sub across replicas | Socket.IO Redis adapter | An emit on one replica reaches sockets on every replica. |
| 9 | Token relay via BFF | `/api/bff/socket-token` issues a short-lived token | Refresh tokens never reach the browser. |
| 10 | Rate limit (enumeration protection) | gateway counter per IP in Redis, `ratelimit:public:{ip}` | Public lookups cannot scan tracking numbers. |

## 5 Key flows

- **Sequence 5** (required): courier update to live browser. The event is written once to `processed_event`
  and to the timeline in the same handler; a duplicate is skipped without an emit.
- **Sequence 6:** a guest opens `/track/SD…`. The response holds the city and a masked name (`K*** P.`),
  never a phone number or street address. A missing number gets the same 404 as any other unknown number.
- **Sequence 7:** the socket handshake checks the JWT with JwtVerifier. A customer can join their own
  `user:<sub>` room; another user's room and `dispatch` are refused. An anonymous socket can only join
  public rooms.
- **Sequence 8:** the owner timeline is allowed for the owner, the assigned courier, the merchant with a
  matching `merchant_id` claim, and staff. Everyone else gets 404, so existence is not leaked.
- **Activity 9:** the consumer validates the envelope, checks `eventId`, drops events older than the
  timeline's `lastChangedAt`, upserts in one transaction, emits, and commits the offset. Errors retry with
  backoff up to three attempts, then go to the dead-letter topic.
- **Activity 10:** the join decision. Public rooms need no login; user and staff rooms go through `RoomPolicy`.

## 6 Design patterns and principles

| Pattern | Applied to |
|---|---|
| Template Method | `KafkaConsumerTemplate` in `swiftdrop-common`: validate, dedupe, handle, commit. Each consumer overrides only `handle`. |
| Observer / pub-sub | Kafka topics; tracking reacts to parcel events it subscribes to. |
| Repository | `TimelineRepositoryPort`, with `MongoTimelineRepository` as the adapter. |
| Adapter / ports and adapters | `BroadcastPort` with `SocketIoBroadcaster`; `IdempotencyStorePort` with `MongoIdempotencyStore`. |
| Facade | `TrackingQueryService` in front of the timeline and the mapper. |
| CQRS | Write side in parcel-service; tracking keeps its own read model. |
| Idempotent consumer | `processed_event` keyed by eventId and consumer. |
| Transactional outbox | Producer side in parcel-service (figure 4, pattern 1). |
| Policy | `RoomPolicy.canJoin` decides room access in one place. |
| Guard | `RateLimitGuard` on public lookups; JWT guard in the shared library. |

Principles:

- **Single responsibility:** consumers translate events; `PublicViewMapper` does masking; the controller only validates and delegates.
- **Open/closed:** a new event type is a new consumer subclass; the template does not change.
- **Dependency inversion:** tracking depends on `TimelineRepositoryPort` and `BroadcastPort`, not on MongoDB or Socket.IO.
- **DRY:** the Kafka template, idempotency store, JWT verification and PII-masking logger come from `swiftdrop-common`.
- **Least privilege:** the token decides every room; the public page gets only the masked view.
- **Twelve-factor configuration:** `MONGO_URI`, `REDIS_URL` and Kafka settings come from the environment.
- **Fail fast:** the zod-validated environment stops the service on bad configuration.
- **PII protection:** masked names on the public view; logs mask phones and e-mails.

## Built and design

- **Exists in code today:** the tracking-service skeleton (`services/tracking-service`), the shared Kafka
  consumer template and `MongoIdempotencyStore` (`libs/node/swiftdrop-common`), the contract draft
  `contracts/openapi/tracking-service.v1.yaml` (rooms and the `parcel.status` event).
- **M6 design, to be built:** the consumers, `TimelineProjector`, `TrackingQueryService`, the Socket.IO
  gateway and `RoomPolicy`, the Redis adapter and rate limit, and the live components. Update the figures
  if the implementation changes names or flows.

## Style

Black, white and light grey only (`#000000`, `#ffffff`, `#ececec`). Participants are underlined boxes and
stick figures; components have black title strips; external systems are dashed; stores are cylinders.
Sequence messages use filled arrows for calls and dashed open arrows for replies. Class relationships
follow UML: dashed hollow triangle for implements, solid hollow triangle for inheritance, dashed open
arrow for dependency. The name in the top-left corner is the file name.
