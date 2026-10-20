# Test reports (submission)

Collected from CI artifacts / local runs before submission:

| Stack | Command | Report |
|---|---|---|
| Java | `mvn verify` | `**/target/site/jacoco/index.html` |
| Node | `pnpm -r test` | `**/coverage/lcov-report/index.html` |
| Python | `uv run pytest` | `coverage.xml` |
| Web | `pnpm --filter @swiftdrop/web test` | `apps/web/coverage/` |
