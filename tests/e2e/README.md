# End-to-end tests (Playwright · Phase 3–5)

Demo flows run against the full compose stack (`pnpm stack:up`):

1. Customer signs in → gets a quote → creates a delivery → sees the tracking number.
2. Merchant simulator creates an order via the API → receives the signed webhook.
3. Dispatcher assigns the parcel → courier updates statuses → public tracking page updates live.
4. Notification visible in Mailpit; webhook retries visible after the simulator fails.
