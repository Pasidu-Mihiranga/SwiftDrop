# Coverage summary

Collected 2026-10-28 17:08 UTC by scripts/collect-test-reports.sh from the
most recent local test run. Regenerate by re-running the tests and this script.

| Module | Lines | Branches | Report |
|---|---|---|---|
| swiftdrop-common-logging (Java) | 94.81% | 85.29% | `doc/test-reports/java/swiftdrop-common-logging/index.html` |
| swiftdrop-common-security (Java) | 97.18% | 75.00% | `doc/test-reports/java/swiftdrop-common-security/index.html` |
| swiftdrop-common-web (Java) | 73.24% | 57.14% | `doc/test-reports/java/swiftdrop-common-web/index.html` |
| pricing-service (Java) | 96.34% | 89.47% | `doc/test-reports/java/pricing-service/index.html` |
| web (Node/web) | 99.72% | 90.27% | `doc/test-reports/node/web/lcov-report/index.html` |
| tracking-service (Node, M6) | 97.58% | not reported by text summary | `services/tracking-service/coverage/lcov-report/index.html` (run `pnpm --filter @swiftdrop/tracking-service test`, Node 22.20 for Testcontainers) |
| web, M6 run (Node/web) | 98.90% | 91.81% | `apps/web/coverage/index.html` (run `pnpm --filter @swiftdrop/web test`) |

M6 figures: tracking-service 67 tests in 10 suites (unit plus Kafka, MongoDB and Redis integration) all passing;
web 234 tests in 17 files all passing. Recorded 2026-11-15 on branch docs/m6-contracts-and-contribution.

Project gate: 70% lines per module (jacoco.minimum.line.coverage in
libs/java/swiftdrop-parent/pom.xml, and the thresholds in apps/web/vitest.config.ts).
