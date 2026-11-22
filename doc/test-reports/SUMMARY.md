# Coverage summary

Collected 2026-11-22 12:19 UTC by scripts/collect-test-reports.sh from the
most recent local test run. Regenerate by re-running the tests and this script.

| Module | Lines | Branches | Report |
|---|---|---|---|
| swiftdrop-common-logging (Java) | 96.05% | 88.24% | `doc/test-reports/java/swiftdrop-common-logging/index.html` |
| swiftdrop-common-security (Java) | 97.18% | 75.00% | `doc/test-reports/java/swiftdrop-common-security/index.html` |
| swiftdrop-common-web (Java) | 73.24% | 57.14% | `doc/test-reports/java/swiftdrop-common-web/index.html` |
| swiftdrop-events (Java) | 89.74% | 25.00% | `doc/test-reports/java/swiftdrop-events/index.html` |
| swiftdrop-outbox (Java) | 94.00% | 73.08% | `doc/test-reports/java/swiftdrop-outbox/index.html` |
| api-gateway (Java) | 100.00% | 83.33% | `doc/test-reports/java/api-gateway/index.html` |
| dispatch-service (Java) | 100.00% | n/a | `doc/test-reports/java/dispatch-service/index.html` |
| merchant-service (Java) | 100.00% | n/a | `doc/test-reports/java/merchant-service/index.html` |
| parcel-service (Java) | 98.38% | 85.38% | `doc/test-reports/java/parcel-service/index.html` |
| pricing-service (Java) | 94.86% | 80.28% | `doc/test-reports/java/pricing-service/index.html` |
| reporting-service (Java) | 98.94% | 75.00% | `doc/test-reports/java/reporting-service/index.html` |
| user-service (Java) | 98.15% | 87.93% | `doc/test-reports/java/user-service/index.html` |
| web (Node/web) | 99.63% | 94.49% | `doc/test-reports/node/web/lcov-report/index.html` |
| swiftdrop-common (Node/web) | 94.44% | 81.30% | `doc/test-reports/node/swiftdrop-common/lcov-report/index.html` |
| notification-service (Node/web) | 100.00% | 100.00% | `doc/test-reports/node/notification-service/lcov-report/index.html` |
| tracking-service (Node/web) | 100.00% | 100.00% | `doc/test-reports/node/tracking-service/lcov-report/index.html` |
| webhook-service (Node/web) | 100.00% | 100.00% | `doc/test-reports/node/webhook-service/lcov-report/index.html` |
| swiftdrop_common (Python) | 99.66% | n/a | `doc/test-reports/python/swiftdrop_common/coverage.xml` |
| ai-assistant-service (Python) | 98.65% | n/a | `doc/test-reports/python/ai-assistant-service/coverage.xml` |

Project gate: 70% lines per module (jacoco.minimum.line.coverage in
libs/java/swiftdrop-parent/pom.xml, and the thresholds in apps/web/vitest.config.ts).

## End-to-end demo suite (Playwright)

Run 2026-11-22 12:18 UTC: **20 passed**, 0 failed, 0 flaky, 5 skipped. HTML report: `doc/test-reports/e2e/html/index.html`, JUnit: `doc/test-reports/e2e/junit.xml`.

Skipped, with the reason the suite gave:

- 3. a dispatcher assigns the parcel to a courier: dispatch-service (M5) is not deployed.
- 4. the courier picks it up and takes it out for delivery: Courier status updates (M4) are not deployed or the parcel is not assigned.
- 5. the public tracking page shows the parcel: tracking-service (M6) is not deployed.
- 6. the customer is emailed (Mailpit): notification-service (M9) is not deployed.
- 7. the merchant simulator receives a signed webhook: webhook-service (M8) is not deployed.
