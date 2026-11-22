# OpenAPI consistency review

Reviewed on branch `feat/m7-merchant-service`, 22 November 2026. The review covers every contract in
`contracts/openapi/`, the controllers that implement them, and the API gateway. It checks versioning, naming,
methods and status codes, auth, schemas, errors, required fields, pagination, idempotency, spec-to-code
agreement and gateway exposure.

## Specifications reviewed

| Spec | Version | State on this branch |
|---|---|---|
| `common.v1.yaml` | 1.0.0 | Shared components |
| `merchant-service.v1.yaml` | 1.0.0-draft | Implemented and contract-tested (`OpenApiContractTest`, ITs) |
| `parcel-service.v1.yaml` | 1.0.0-draft | Customer booking implemented; courier, receipt and merchant-order operations not yet on this branch |
| `pricing-service.v1.yaml` | 1.0.0 | Implemented |
| `user-service.v1.yaml` | 1.0.0-draft | Profile, address book and courier directory implemented; `/admin/users` arrived from `dev` (M1) when it was merged in |
| `dispatch-service.v1.yaml` | 1.0.0-draft | Draft; only `meta/whoami` exists |
| `tracking-service.v1.yaml` | 1.0.0-draft | Draft; only `meta/whoami` exists |
| `notification-service.v1.yaml` | 1.0.0-draft | Draft; only `meta/whoami` exists |
| `webhook-service.v1.yaml` | 1.0.0-draft | Draft; only `meta/whoami` exists |
| `reporting-service.v1.yaml` | 1.0.0-draft | Draft; only `meta/whoami` exists |
| `ai-assistant-service.v1.yaml` | 1.0.0-draft | `chat` and `meta/provider` implemented |

The API gateway has no spec of its own. Its routes and public paths are in
`services/api-gateway/src/main/resources/application.yml`.

## What is consistent

- **Versioning.** Every route is under `/api/v1`, and every spec file is `*.v1.yaml`.
- **Naming.**
  - Resource paths are plural nouns in kebab-case, such as `rate-cards`, `size-classes` and `remote-areas`.
  - Admin APIs sit under `/api/v1/admin/<service>`.
  - The caller's own resources use `/me`.
  - State changes are POST sub-resources, such as `/approve`, `/reject`, `/suspend` and `/cancel`.
- **Errors.** Every implemented Java service answers errors with `application/problem+json` from the shared
  `GlobalExceptionHandler`. The body carries `type`, `title`, `status`, `detail`, `code`, `traceId`, `timestamp`,
  plus `errors[]` on a validation failure. This matches `common.v1.yaml#/components/schemas/ProblemDetail`.
- **Pagination.**
  - Every paged list (merchant applications, rate cards, the caller's parcels) takes `page` (from 0) and `size`
    (1 to 100, default 20).
  - It answers `{ content, page: PageMeta }`.
  - The controllers enforce the same limits with `@Min` and `@Max`.
  - Short bounded lists (addresses, couriers, zones) are plain arrays, as documented.
- **Auth.**
  - Every spec defaults to `bearerAuth`.
  - Public operations (quote creation, public zones, public tracking, the assistant) have `security: []` or
    optional auth.
  - These public operations match the gateway's `swiftdrop.gateway.public-paths` exactly.
  - Roles are stated in each operation's summary and match the controllers' `@PreAuthorize` rules.
- **Pricing and merchant contracts.**
  - Every implemented path, method, status code and parameter matches its controller.
  - merchant-service is also checked at runtime against its schemas.
- **Idempotency.** `common.v1.yaml#/components/parameters/IdempotencyKey` is defined once and used by the only
  idempotent operation, `POST /api/v1/merchant/orders`.

## Inconsistencies found and corrected

| # | Spec | Inconsistency | Correction |
|---|---|---|---|
| 1 | user-service | Implemented operations documented errors as plain text, with no `application/problem+json` body, while the code returns problem+json | Each 4xx/5xx now has a ProblemDetail body; the documented error codes are kept |
| 2 | user-service | No 401 on any operation, and 403 missing on the address book even though `@PreAuthorize` refuses MERCHANT_API clients | Added 401 everywhere and 403 on the address book |
| 3 | user-service | Request bodies were not marked required, but `@RequestBody` requires them | `required: true` on PUT `/me`, POST/PUT addresses and PUT courier |
| 4 | user-service | `POST /users/me/addresses` returns a `Location` header that was not documented | Documented it |
| 5 | user-service | `PUT /admin/couriers/{id}` can return 422 COURIER_EMAIL_MISSING and 503 IDENTITY_PROVIDER_UNAVAILABLE (it reads Keycloak); neither was documented | Documented both |
| 6 | user-service | `lat`/`lng` ranges (±90/±180) enforced in code were missing from AddressRequest | Added the ranges |
| 7 | user-service | `GET/POST /admin/users` and `PATCH /admin/users/{id}` were documented but not implemented on this branch | Marked "planned" at first. After `origin/dev` was merged, M1's implementation and full definitions replaced the label. The `search` parameter's description is now quoted, because its unquoted comma made the spec invalid OpenAPI |
| 8 | parcel-service | `POST /parcels` was missing its `Location` header, 401, 403 (CUSTOMER only) and 503 PRICING_UNAVAILABLE | Added them, with a description of QUOTE_EXPIRED and QUOTE_MISMATCH |
| 9 | parcel-service | `/parcels/mine` had no 400/401/403, `/parcels/{id}` no 401, and `/cancel` no 401/404/409 | Added them; 404 is also the answer for parcels the caller does not own; 409 PARCEL_MODIFIED |
| 10 | parcel-service | `quoteId` was a plain string, but the code requires a UUID | `format: uuid` |
| 11 | parcel-service | The Parcel response includes `serviceLevel` and `notes`, but the schema did not | Added both; documented that empty fields are left out (`@JsonInclude(NON_NULL)`) |
| 12 | merchant-service | `rotate` sends `Pragma: no-cache`, which was not documented | Documented it |
| 13 | merchant-service | The meaning of 409 (MERCHANT_MODIFIED) versus 422 on admin decisions was not stated | Added to the description |
| 14 | merchant, parcel, user | Every service has a `meta/whoami` smoke test, but only parcel-service documented it | Documented it in merchant and user; added 401 in parcel; stated the convention in `common.v1.yaml` |
| 15 | common | Several services return 503 when a dependency is down, with no shared response | Added `components/responses/ServiceUnavailable`; stated the required-body and 201-Location conventions |

None of these corrections changes behaviour. They document what the code already does, so no client contract is
broken.

## Validation

| Command | Result |
|---|---|
| CI step "OpenAPI drafts parse" (`.github/workflows/ci-platform.yml`): `python3 -c "import glob,yaml; [yaml.safe_load(open(f)) for f in glob.glob('contracts/openapi/*.yaml')]"` | OK, 11 files |
| `openapi-spec-validator <file>` for each of the 11 files (OpenAPI 3.1 structural check, run from a scratch virtualenv) | All 11 OK |
| Scratch audit script: every `$ref` resolves (local and cross-file); spec operations compared with controller mappings | No unresolved refs; no implemented operation missing from its spec |
| `mvn -B -pl services/merchant-service -am verify` (JDK 21) | BUILD SUCCESS: 124 unit and 33 integration tests, including `OpenApiContractTest` and the ITs that validate real responses against the contract; coverage gate met |

## Unresolved issues

- **No gateway aggregation of API docs.**
  - The Java services publish springdoc `/v3/api-docs` and Swagger UI on their own ports, and the shared security
    config permits these paths.
  - The gateway has no route for `/v3/api-docs` or Swagger UI, so the docs are not reachable through
    `localhost:8080`.
  - The springdoc output is generated from code and is not the same document as `contracts/openapi`.
  - Fixing this would add gateway functionality (routes, or springdoc's grouped UI), so it is left to the gateway
    owner.
- **Draft specs.**
  - The dispatch, tracking, notification, webhook and reporting specs describe endpoints that do not exist yet.
  - Many of their responses have no schema, no 401/403 and no problem+json error bodies.
  - Their owners (M5, M6, M8, M9, M10) should complete them as each endpoint is built. Inventing schemas now would
    be guesswork.
- **parcel-service merchant orders and courier operations.**
  - The merchant order API is implemented and its spec corrected on `feat/m7-merchant-orders`, which builds on this
    branch. That correction adds the 409/422/503 codes, `Idempotent-Replayed`, MerchantOrder and filters.
  - It was not repeated here, to avoid a conflicting edit.
  - Courier status, proof upload and receipt (M4, M9) are not implemented on this branch.
- **pricing-service does not list its `meta/whoami`.** The convention in `common.v1.yaml` covers it. The pricing
  spec is owned by M2 (see `AGENTS.md`), so it was not edited.
- **Version labels differ.**
  - `common` and `pricing` are `1.0.0`; the others are `1.0.0-draft`.
  - merchant-service is implemented and contract-tested, so its owner may want to mark it `1.0.0`.
- **Contract tests exist only in merchant-service.** user, parcel and pricing have no test that checks responses
  against `contracts/openapi`. The `OpenApiContract` helper could be reused there.
- **`courierId` in the parcel response.** The schema says `[string, null]`, but the service leaves the field out
  rather than sending null. Clients should treat a missing field as null.

## After merging `origin/dev`

`origin/dev` (merged at `502612e`) adds real implementations for dispatch, notification, webhook and tracking
endpoints, and for user-service's `/admin/users`. The "Draft; only `meta/whoami` exists" rows above describe this
branch before that merge. The same audit, re-run on the merged tree, found:
- All 11 specs pass `openapi-spec-validator`, and every `$ref` resolves.
- The new operations repeat the gaps corrected above for M7's own endpoints:
  - no 401;
  - errors described in plain text, with no problem+json body;
  - request bodies not marked required.

  This affects `/admin/users*`, dispatch, notification and webhook endpoints. Their owners (M1, M5, M8, M9)
  should apply the same conventions.
- `GET /api/v1/parcels`, which arrived from `dev`, is implemented but not yet in `parcel-service.v1.yaml`. Every
  other implemented route is in a spec, apart from the `meta/whoami` endpoints covered by the convention in
  `common.v1.yaml`.
