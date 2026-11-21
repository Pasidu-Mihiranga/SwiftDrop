# End-to-end tests (Playwright)

The demo suite drives the real stack: the web app in Chromium, the gateway, Keycloak's own login form, Kafka and
every service behind them. Nothing is mocked.

## Run

```bash
pnpm env:init            # once: creates .env with the demo passwords
pnpm stack:up            # the full compose stack
pnpm install
pnpm e2e:install         # once: downloads Chromium
pnpm e2e                 # runs the suite
```

The suite reads ports and the demo password (`KC_SEED_USER_PASSWORD`) from the repository's `.env`, so it always
matches the stack it runs against. Override with `E2E_WEB_URL`, `E2E_GATEWAY_URL` or `E2E_MAILPIT_URL` when a port
is taken, e.g. `GATEWAY_HOST_PORT=18080 pnpm stack:up` then `E2E_GATEWAY_URL=http://localhost:18080 pnpm e2e`.
A global setup waits for the web app, the gateway and Keycloak, and stops with a hint if they are not up.

## What it covers

| Spec | Flow |
|---|---|
| `01-public` | Home page; a guest gets a real fee estimate on `/quote` |
| `02-assistant` | Chat widget on every page; a guest asks "How much to send 2 kg from Colombo to Kandy?"; a signed-in customer asks "Where is my parcel?" |
| `03-mcp` | An external MCP client lists the five tools, gets a quote for a town (Nugegoda resolves to Colombo), cannot list parcels anonymously, and lists only its own parcels with a token |
| `04-reports` | Customers, couriers and merchants get 403 on reports, guests 401, dispatchers may read but not rebuild; an admin's dashboard matches the report API |
| `05-demo-flows` | One parcel through every service: booking from a real quote, the booking reaching the reports through Kafka, dispatch, courier status updates, the public tracking page, the Mailpit email, the merchant webhook, and owner-only access |

Steps that need a service which is not deployed yet are **skipped with the reason** (for example
"tracking-service (M6) is not deployed"), so the report shows what is missing instead of failing on it. Assistant
answers need an LLM key (`GEMINI_API_KEY` or `OPENAI_API_KEY`); without one the suite checks the graceful
"not switched on" message instead, and MCP still runs in full because it needs no model.

Backend steps whose screens are not built yet sign in with the DEV ONLY `swiftdrop-test` client (password grant);
UI steps always sign in through the real login page.

## Results

Written to `doc/test-reports/e2e/` (git-ignored, collected for the submission by `pnpm test:reports`):
`junit.xml`, `results.json`, the HTML report (`pnpm --filter @swiftdrop/e2e e2e:report`), and traces, screenshots
and videos of failures.
