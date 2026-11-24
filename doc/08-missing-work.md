# What is still missing, issue by issue

> **SwiftDrop docs:** [01 Architecture](01-architecture.md) · [02 Implementation Plan](02-implementation-plan.md) · [03 Team & Contribution](03-team-contribution.md)

First checked on 22 Nov 2026 against `main` at `af82585`, item by item for all 224 checklist entries in issues #14
to #23. **Updated on 24 Nov 2026 against `dev`** after #108 to #130, which closed every gap that did not need a
member's own details or decision (#110 finished M1). Those PRs are on `dev` only; `main` gets them with the next reviewed `dev` to `main` merge. Items marked [x] were done in
the PR named; open items remain.

None of the issue checkboxes are ticked on GitHub yet: each owner should tick what is done so the issue shows the
real state.

## Summary

| Issue | Owner | State on `dev` | Still to do |
|---|---|---|---|
| [#14 M1 Identity & Access](#14-m1-identity--access) | ThisukaMW | Done (#110) | Nothing found |
| [#15 M2 Pricing & Zones](#15-m2-pricing--zones) | sasindu345 | Done | Student ID |
| [#16 M3 Customer Booking](#16-m3-customer-booking) | Malshipahasara | Done | Contribution row; owner review of the booking screens |
| [#17 M4 Courier Ops & Status Lifecycle](#17-m4-courier-ops--status-lifecycle) | pamindu-w | Done | Design-PDF section, contribution row |
| [#18 M5 Dispatch & Workload](#18-m5-dispatch--workload) | Chaniths | Done | Contribution row |
| [#19 M6 Real-time Tracking](#19-m6-real-time-tracking) | ashmax77 | Done | Design-PDF note |
| [#20 M7 Merchant Onboarding & API](#20-m7-merchant-onboarding--api) | KavinuS | Done | Name and student ID in the contribution row |
| [#21 M8 Webhooks & External Integration](#21-m8-webhooks--external-integration) | geemalfernando | Done | Integration design chapter, contribution row |
| [#22 M9 Notifications, History & Receipts](#22-m9-notifications-history--receipts) | Sapuni-Pathirana | Done | Contribution row |
| [#23 M10 Reporting & AI Assistant](#23-m10-reporting--ai-assistant) | Pasidu-Mihiranga | Done | Student ID |

The three blockers of 22 Nov are closed: courier screens (#108), the merchant simulator (#109) and distributed
tracing (#112).

### Left for the members themselves

These need a person's own details or writing and were deliberately not filled in by others:

| Item | Missing for |
|---|---|
| Contribution sheet row (`doc/03-team-contribution.md`) | M3, M4, M5, M8, M9 empty; M7 name and ID are TODO; M2 and M10 student ID are TODO |
| Design PDF / design-document section | M4, M6, M8 (M1, M2 and M3 have Markdown design docs; M5, M7, M9, M10 have PDFs) |
| Owner review of work others built | M3 booking screens (written by M2/M9, logic now tested in #115); M4 backend (#107) and screens (#108) |

### Found while doing this round

- [x] **Merchant approval emails were never sent**: the per-status opt-outs (parcel statuses) also filtered
      `MerchantLifecycleChanged`. Fixed in #119; found by the event-flow integration test in #116.
- [x] `scripts/demo-m2-pricing.sh` did not run in Git Bash on Windows (curl wrote `/tmp`, native Python read
      `C:\tmp`). Fixed in #121; 16/16 on Windows and Linux.
- [x] The kind e2e job could not tell an absent parcel-service from a failure, because the gateway answered 500
      for an unreachable service; #110 makes it 503, which the demo script already treats as "offline".
- [x] Grafana in compose did not pick up a new, edited or deleted dashboard on a Windows (or macOS) bind mount
      until a restart; a `grafana-dashboard-sync` container now calls the provisioning reload API when the folder
      changes (#123).
- [x] The dispatch board test hard-coded the day it was written, so it failed from 24 Nov; fixed in #124.
- [x] The notification event-flow IT (#116) could run out of Kafka connection retries on a busy CI runner;
      #125 gives its test client a longer retry budget.
- [x] In compose, a recreated service got a new IP and the gateway kept routing to the old one (503) until it
      was restarted: reactor-netty cached Docker DNS answers for their long TTL. The gateway now caches upstream
      addresses for 5 s; a service moved to a new IP was reachable again 11 s later, its start-up time (#127).
- [x] The dispatch board's workloads call answered 500 when user-service could not list couriers; it now
      answers 503 `COURIER_DIRECTORY_UNAVAILABLE` (#128).
- [x] On a phone the header's links wrapped into a tall block above every page (worst on the courier screens);
      they now sit behind a Menu button (#129). The Fees & handling band rows got column headings (#129).
- [x] `/merchant/credentials` showed an empty card to a merchant with no application; it now links to the
      registration form (#130).
- [x] Screenshots of every UI are in [`doc/screenshots`](screenshots/README.md).
- Local environment only, no code change: the local Keycloak realm predated #110's `view-realm` grant for the
  user-service and merchant-service accounts (Keycloak never re-imports an existing realm); it was added through
  the admin API. The local Keycloak client secrets were rotated after they appeared in a session log.

---

## #14 M1 Identity & Access

All done by the owner in #110 (merged 24 Nov):
- [x] Gateway answers 503 problem+json when a service is down (`ServiceUnavailableHandler`).
- [x] Redis rate limiting shared by gateway replicas (`TieredRedisRateLimiter`), with a Redis IT.
- [x] One security test per entry in `swiftdrop.gateway.public-paths`.
- [x] Email verification on in the realm; branded `swiftdrop` login theme.
- [x] One shared PII library, `libs/java/swiftdrop-pii`, used by user-, merchant- and parcel-service.
- [x] Design section `doc/design/m1-identity-access-design.md`; Grafana dashboard `identity-access.json`.

## #15 M2 Pricing & Zones

- [x] Hand the quote to booking: the wizard loads the `/quote` quote and books with it while it is valid and the
      request is unchanged (#114).
- [x] Demo 3 end to end: `scripts/demo-m2-pricing.sh` passed 16/16 against compose, including parcel-service
      consuming a valid quote (201) and rejecting an expired one (422 `QUOTE_EXPIRED`) (#114).
- [x] `doc/04-m2-pricing-zones-issue-15.md` updated (#114).
- [x] Kubernetes infra: Kafka (KRaft, with a topic Job), MongoDB, RustFS and Mailpit templated, plus
      `values-full.yaml` and `SWIFTDROP_PROFILE=full ./scripts/kind-deploy.sh` for every service (#122). The
      Grafana stack stays with the official Grafana charts.
- [x] The demo script runs on Windows as well (#121).
- [x] V16 placeholder values: kept as the starting configuration and now changed by an administrator on
      `/admin/pricing/settings` (Fees & handling) instead of by migration; settings and distance bands are
      versioned from a date, handling options and same-day vehicles edited in place (#124).
- [x] Same-day cut-off: `pricing_setting.same_day_cutoff` (V17, none until set); from it, in Colombo time, a
      same-day quote is refused with 422 `SAME_DAY_CUTOFF_PASSED` (#124).
- [x] Free-text address search on the quote and booking maps, through a Nominatim-compatible geocoder that is
      off unless configured; results are checked against the district boundaries (#126).
- [ ] The owner may still want to review the seeded figures on the new screen; nothing in code depends on them.
- [ ] Student ID in the contribution row.

## #16 M3 Customer Booking

- [x] Booking wizard logic moved into `src/lib/booking.ts` with 15 tests at 100% lines and branches (#115).
- [x] Live timeline on the customer parcel page (#113).
- [x] Grafana panels: bookings per hour by channel, quote rejections by reason, pricing circuit breaker state, on
      the new `swiftdrop-parcels-webhooks` dashboard with new parcel-service metrics (#118).
- [ ] **Contribution sheet row** is empty.
- [ ] Owner review of the booking screens.

## #17 M4 Courier Ops & Status Lifecycle

- [x] Courier screens: status stepper with only the valid next steps, proof capture with photo upload and
      progress, failed-delivery form, optimistic updates; linked from the run sheet (#108).
- [x] Diagrams: parcel lifecycle, courier use case, status-update sequence in `doc/diagrams/m4-courier-operations` (#117).
- [x] Grafana panels: status changes per hour, failed deliveries by reason (#118).
- [x] Presigned URLs tested against a real RustFS container: upload, download, signed content type, expiry (#120).
- [ ] Design-PDF section, contribution row.
- [ ] Owner hand-over: review #107 and #108.

## #18 M5 Dispatch & Workload

- [x] Least-loaded courier suggestion: already on `dev` before this round (`rankedCouriers` puts zone matches
      first, then the lowest load, and the assign dialog pre-selects the top one; tested in `dispatch.test.ts`).
- [x] Run sheet tests: `run-sheet.test.tsx` (#108) and the run sheet cases in `dispatch-board.test.tsx`; #113
      fixed the run sheet test that broke on 2026-11-23 because it used that date as the new date.
- [ ] **Contribution sheet row** is empty.

## #19 M6 Real-time Tracking

- [x] Live timeline on the customer parcel page and, opened per row, on the merchant orders page (#113).
- [ ] Design-PDF note on WebSockets + Redis adapter and the MongoDB read model.

## #20 M7 Merchant Onboarding & API

- [x] Demo 2 (signed webhook after an order) is possible with the merchant simulator (#109).
- [ ] Contribution row: **name and student ID** are still `TODO`.

## #21 M8 Webhooks & External Integration

- [x] Merchant simulator: client-credentials orders with `Idempotency-Key`, signature and timestamp checks, a
      page, fail mode (503), Dockerfile and compose entry on port 4010 (#109).
- [x] Grafana panels: attempts by outcome, endpoint answer time, success rate, dead letters, with new
      webhook-service metrics (#118).
- [ ] **Integration design chapter** of the design PDF.
- [ ] **Contribution sheet row** is empty.

## #22 M9 Notifications, History & Receipts

- [x] Distributed tracing: OpenTelemetry in every Java, Node and Python service, W3C `traceparent` stored by the
      outbox and carried in Kafka headers; one booking showed spans from the gateway, parcel, tracking, webhook,
      dispatch, notification and reporting services in Tempo (#112). The OTLP metrics push it pulled in is off by
      default (Prometheus scrapes instead).
- [x] Logs-to-traces linking follows from real trace ids (#112).
- [x] Event-flow integration test on Kafka, MongoDB and Mailpit: ParcelCreated and ParcelStatusChanged become
      emails, a redelivered event sends no second email (#116).
- [x] "How to debug with Grafana" guide in the README (already on `dev`; the 22 Nov audit missed it).
- [x] Observability diagram `doc/diagrams/m9-notifications-receipts/13-architecture-observability` (#116).
- [x] Demo 1 on a phone is possible with the courier screens (#108).
- [ ] **Contribution sheet row** is empty.

## #23 M10 Reporting & AI Assistant

Done: everything in the issue (#106).
- [ ] Student ID in the contribution row.

---

## How this was checked

22 Nov: every checklist line was searched for on `main` (endpoint, migration, class, page, test or document),
contribution rows, design PDFs, diagrams and dashboards were listed per member, and the gateway log and end-to-end
runs confirmed the runtime gaps. 23 and 24 Nov: each PR from #108 to #122 was merged into `dev` after its CI
passed, and the runtime items were checked against the compose stack (M2 demo script, Tempo trace search,
Prometheus queries for the new metrics, Grafana dashboard load) and, for the Helm work, a full-profile kind
install on a GitHub runner. #124 and #126 were also checked live: the settings API and cut-off on a throwaway
copy of `pricing_db`, and address search against a local stand-in geocoder, so no data changed and no request
left the machine. Re-check an item after its owner merges new work.
