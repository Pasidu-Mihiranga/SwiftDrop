# Running SwiftDrop locally

How to start SwiftDrop on your own machine and try the M7 merchant flow end to end: a customer
applies, an admin approves, the merchant collects API credentials, and the merchant's system places
delivery orders.

Sections 2–7 were run on 21 November 2026 on macOS (Apple Silicon) against branch
`feat/m7-merchant-orders`, and worked as shown, except the browser steps in section 7, which were
checked only as far as the sign-in redirect. Commands not run in that check are marked. For the general overview see
[README.md](README.md); for service details see each `services/*/README.md`.

---

## 1. Prerequisites

| Tool | Version used | Notes |
|---|---|---|
| Docker Desktop | Engine 29.9, Compose v5.6 | Must be running before you start. |
| Node.js | 24.x | Includes Corepack. |
| pnpm | 10.23 (via Corepack) | Run `corepack enable` once, so the `pnpm` command exists. |
| JDK | **21** (Temurin 21) | The Java services do not compile with JDK 17. |
| Maven | 3.9 or newer | 3.10.1 used. |
| curl, jq | any | Only for the API walkthrough in section 7. |

**Check that Maven uses JDK 21.** Run `mvn -v` and look at the "Java version" line. If it shows 17,
point `JAVA_HOME` at JDK 21 in every terminal you use for Java (macOS example):

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

---

## 2. One-time setup

From the repository root (`SwiftDrop/`):

```bash
corepack enable        # makes `pnpm` available (once per machine)
pnpm install           # Node workspace: web app, Nest services, shared library
pnpm env:init          # creates .env with freshly generated secrets
```

- `.env` holds every password and secret for your local stack. It is gitignored: **never commit it,
  and never paste its values into chats, issues or pull requests.**
- `pnpm env:init` never overwrites an existing `.env`. `pnpm env:init --force` regenerates it, but
  existing Postgres and Keycloak volumes keep the old passwords, so only do that together with the
  data reset in section 9.
- The demo users' password is `KC_SEED_USER_PASSWORD` in `.env` (default `Swift@2026`).

---

## 3. Start the infrastructure

```bash
pnpm infra:up
```

This starts Postgres, Keycloak, Kafka (and creates its topics), MongoDB, Redis, RustFS (S3),
Mailpit and Kafka UI in Docker. The first run downloads images and takes a few minutes; later runs
take about 1–2 minutes.

Wait until every container is healthy, Keycloak in particular:

```bash
docker compose ps        # postgres, kafka, keycloak, mongo, redis, mailpit: "healthy"
```

On first start Postgres creates one database per service (`merchants_db`, `parcels_db`,
`pricing_db`, `users_db`, `dispatch_db`, `reporting_db`), and Keycloak imports the `swiftdrop` realm
from `infra/keycloak/realm-swiftdrop.json`.

> **Already ran Keycloak on this machine before?** Keycloak only imports the realm file on first
> start. Run `pnpm keycloak:reimport` after pulling realm changes. M7 needs the `view-realm`
> permission for merchant-service's service account; without it, approving a merchant answers 503.

---

## 4. Build and start the backend services

The M7 flow needs four Java services. Use a terminal with **JDK 21** (section 1).

**Build once** (`-DskipTests` because the tests are run separately in section 8; the first build
downloads dependencies and takes several minutes):

```bash
mvn -B -DskipTests \
  -pl services/pricing-service,services/merchant-service,services/parcel-service,services/api-gateway \
  -am package
```

**Start each service in its own terminal, from the repository root.** The services read `.env`
from the directory you start them in.

```bash
java -jar services/pricing-service/target/pricing-service.jar      # port 8083
java -jar services/merchant-service/target/merchant-service.jar    # port 8082
java -jar services/parcel-service/target/parcel-service.jar        # port 8084
java -jar services/api-gateway/target/api-gateway.jar              # port 8080
```

Each takes about 5 seconds to start. Check them:

```bash
for p in 8083 8082 8084 8080; do curl -s localhost:$p/actuator/health; echo; done
# each prints {"groups":["liveness","readiness"],"status":"UP"}
```

Alternatives:

- **One service with Maven, without building jars first:**
  `mvn -pl services/parcel-service -am spring-boot:run`. Start one service this way at a time,
  because parallel `-am` builds overwrite each other's shared-library output.
- **Everything in containers:** `pnpm stack:up` builds and starts every service image. The first
  build is slow; this guide did not use it.

The other services (user-service, dispatch-service, reporting-service and the Node and Python
services) are started the same way, but the M7 flow does not need them.

---

## 5. Start the web app

```bash
pnpm web:dev
```

Open <http://localhost:3000>. Protected pages redirect to `/login`, which signs in through
Keycloak.

- If you skipped `corepack enable`, `pnpm web:dev` fails with `pnpm: command not found`, because the
  script calls `pnpm` internally. Either run `corepack enable`, or start the web app directly with
  `corepack pnpm --filter @swiftdrop/web dev`.
- On startup the dev server may print a Next.js security-upgrade notice and stop. Start it again and
  it keeps running. See section 10.

---

## 6. Local URLs and demo users

| What | URL | Sign in with |
|---|---|---|
| Web app | <http://localhost:3000> | demo users below |
| API gateway | <http://localhost:8080> | bearer token |
| Swagger UI, all services | <http://localhost:8080/swagger-ui.html> (pick the service at the top right) | — (calls need a bearer token) |
| Swagger UI per service | `http://localhost:<port>/swagger-ui.html` (8082 merchant, 8083 pricing, 8084 parcel) | — |
| Keycloak admin | <http://localhost:8180/admin> | `KEYCLOAK_ADMIN_USER` / `KEYCLOAK_ADMIN_PASSWORD` from `.env` |
| Kafka UI | <http://localhost:8088> | — |
| Mailpit (test email inbox) | <http://localhost:8026> | — |
| Postgres | `localhost:15432` | `POSTGRES_USER` / `POSTGRES_PASSWORD` from `.env` |

All host ports can be changed in `.env` (`*_HOST_PORT`).

| Demo user | Role |
|---|---|
| `admin@swiftdrop.lk` | ADMIN |
| `customer@swiftdrop.lk` | CUSTOMER |
| `dispatcher@swiftdrop.lk` | DISPATCHER |
| `courier1@swiftdrop.lk`, `courier2@swiftdrop.lk` | COURIER |
| `merchant@swiftdrop.lk` | MERCHANT (seeded; has no merchant record, so see the note in section 7) |

Password: `KC_SEED_USER_PASSWORD` from `.env`.

---

## 7. Try the M7 merchant flow

### In the browser

Use **Sign in with email** (Google sign-in needs your own Google OAuth client in `.env`).

1. Sign in as `customer@swiftdrop.lk` (or register a new user) and apply on **Become a merchant**
   (`/merchant/register`).
2. Sign out, sign in as `admin@swiftdrop.lk`, open **Operations → Merchants** (`/admin/merchants`) and
   approve the application.
3. Sign out and sign in again as the applicant, so the new MERCHANT role is in the session. **Merchant
   portal** now appears in the menu.
4. **Merchant portal → API credentials** (`/merchant/credentials`): tick the box and choose **Generate new
   secret**. The client ID, secret and token URL are shown once, with copy buttons. Save the secret: it
   is not stored anywhere and cannot be shown again. Generating again replaces it.
5. Place orders with those credentials (API steps 4 and 5 below).
6. **Merchant portal → Orders** (`/merchant/orders`): the orders appear newest first. Filter by status
   and by the day they were created (Sri Lanka time), page through them, and choose **Download CSV** for
   every order matching the filters.

The pages were checked in the verification run with a signed-in merchant: the orders list, the filters
(including a backwards date range and an empty result), the credentials page and the CSV download.
Generating a secret through the page is covered by the web tests; the same rotation was run through the
API.

### With the API (through the gateway)

Load the settings from `.env` and define a token helper. The `swiftdrop-test` client allows
password sign-in for local testing.

```bash
set -a; . ./.env; set +a
KC=http://localhost:8180/realms/swiftdrop/protocol/openid-connect/token
GW=http://localhost:8080
tok() { curl -s -d grant_type=password -d client_id=swiftdrop-test -d "username=$1" \
        --data-urlencode "password=$KC_SEED_USER_PASSWORD" $KC | jq -r .access_token; }
```

**1. A customer applies (201)**

```bash
CUST=$(tok customer@swiftdrop.lk)
MID=$(curl -s -X POST $GW/api/v1/merchants/register -H "Authorization: Bearer $CUST" \
  -H 'Content-Type: application/json' \
  -d '{"businessName":"Lanka Books","brNumber":"PV 12345","contactEmail":"orders@lankabooks.lk",
       "contactPhone":"0771234567",
       "pickupAddress":{"line1":"12 Galle Road","city":"Colombo 03","district":"Colombo"}}' | jq -r .id)
echo $MID
```

**2. An admin approves (200).** This creates the merchant's API client in Keycloak and publishes the
approval to Kafka, from which parcel-service learns the merchant is approved.

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST $GW/api/v1/admin/merchants/$MID/approve \
  -H "Authorization: Bearer $(tok admin@swiftdrop.lk)"
```

**3. The merchant collects its API credentials.** Get a **new** token first: only one issued after
approval carries the MERCHANT role. The secret is shown once; store it safely.

```bash
CREDS=$(curl -s -X POST $GW/api/v1/merchants/me/credentials/rotate \
  -H "Authorization: Bearer $(tok customer@swiftdrop.lk)")
CID=$(echo "$CREDS" | jq -r .clientId); CSEC=$(echo "$CREDS" | jq -r .clientSecret)
```

**4. The merchant's system gets a token (client credentials)**

```bash
MTOK=$(curl -s -d grant_type=client_credentials -d "client_id=$CID" \
  --data-urlencode "client_secret=$CSEC" $KC | jq -r .access_token)
```

**5. Place an order (201), repeat it (200), change it under the same key (409)**

```bash
ORDER='{"merchantOrderRef":"WEB-1001",
  "pickup":{"line1":"12 Galle Road","city":"Colombo 03","district":"Colombo"},
  "dropoff":{"line1":"45 Peradeniya Road","city":"Kandy","district":"Kandy"},
  "recipient":{"name":"Saman Perera","phone":"0712345678"},"weightKg":2,"sizeClass":"S"}'
order() { curl -s -w "\nHTTP %{http_code}\n" -X POST $GW/api/v1/merchant/orders \
  -H "Authorization: Bearer $MTOK" -H "Idempotency-Key: $1" -H 'Content-Type: application/json' -d "$2"; }

order order-web-1001-a "$ORDER"                      # 201: tracking number and fee
order order-web-1001-a "$ORDER"                      # 200: the same order again (Idempotent-Replayed: true)
order order-web-1001-a "${ORDER/WEB-1001/WEB-1002}"  # 409: IDEMPOTENCY_KEY_REUSED
```

The `Idempotency-Key` header is required and must be 8 to 100 visible ASCII characters; use a new
key for each new order.

**6. List the merchant's orders**

```bash
curl -s "$GW/api/v1/merchant/orders?size=20" -H "Authorization: Bearer $MTOK" | jq '.page, [.content[].merchantOrderRef]'
```

Results from the verification run: the order was created (201, fee LKR 631.00 from pricing-service),
the repeat answered 200, the changed body 409, a request without the header 400, and the list showed
only this merchant's order.

> **The seeded `merchant@swiftdrop.lk` user and `merchant-demo` client are not expected to place
> orders:** no merchant was ever approved for them, so parcel-service should answer 403
> `MERCHANT_NOT_APPROVED` (by design; not tried in the verification run). Use a
> merchant you registered and approved as above.

---

## 8. Run the tests

Java (JDK 21; Docker must be running, because integration tests start their own containers):

```bash
mvn -B -pl services/parcel-service -am verify     # one service: unit + integration tests + coverage gate
mvn -B -pl services/merchant-service -am verify
mvn -B verify                                     # everything (slow; not run in the verification)
```

Web app:

```bash
pnpm --filter @swiftdrop/web test
pnpm --filter @swiftdrop/web typecheck
```

Last verified results: parcel-service 202 unit and 46 integration tests, merchant-service 124 unit
and 33 integration tests, web 251 tests, all passing.

---

## 9. Stop and reset

(Not run in the verification, which left the stack running.)

```bash
# stop the Java services and the web app with Ctrl+C in their terminals, then:
pnpm stack:down          # stops and removes the containers; data volumes are kept
```

To start again from empty databases and a fresh Keycloak realm (**deletes all local data**):

```bash
docker compose --profile infra --profile observability --profile apps down -v
```

---

## 10. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `release version 21 not supported` | Maven is using JDK 17. Set `JAVA_HOME` to JDK 21 (section 1). |
| `pnpm: command not found` | Run `corepack enable`, or prefix commands with `corepack` (`corepack pnpm …`). |
| Approving a merchant answers **503** `IDENTITY_PROVIDER_UNAVAILABLE` | Keycloak still has an older realm: run `pnpm keycloak:reimport`. Also check that merchant-service is running with `.env` in its working directory. |
| Rotating credentials answers **403** | The token was issued before approval. Sign in again (get a new token). |
| Merchant order answers **403** `MERCHANT_NOT_APPROVED` for an approved merchant | parcel-service has not seen the approval event. Check it was running and connected to Kafka, and look for `Merchant … is APPROVED` in its log. Approvals older than the topic's seven-day retention are never replayed; see "Known limitation" in `services/parcel-service/README.md`. |
| Merchant order answers **503** `PRICING_UNAVAILABLE` | pricing-service is not running or not reachable on port 8083. |
| Pages hang or say a list is "unavailable", while `/actuator/health` says UP | The jars were rebuilt (`mvn package`) while the services were running from them. Restart the four services. |
| Sign-in ends on "Server error: Configuration" | Docker's clock fell behind the Mac's (usually after sleep), so the web app sees Keycloak's token as expired. Compare `date -u +%s` with `docker run --rm alpine date -u +%s`; if they differ, restart Docker Desktop or `docker restart swiftdrop-keycloak-1`. |
| A service fails with "Port … already in use" | Something else uses the port. Stop it, or change the `*_HOST_PORT` value in `.env`. |
| Integration tests fail with "Can't get Docker image" | The image download timed out. Pull it first, e.g. `docker pull quay.io/keycloak/keycloak:26.8.1`. |
| `next dev` prints a security notice and exits | Next.js 16.4.3 has published advisories (moderate; fixed in 16.4.5 or later). Starting it again works for local use. Upgrading Next.js is a team decision; see the note below. |

### Known issues found during the verification run

- **Next.js 16.4.3 security advisories.** `pnpm audit` in `apps/web` lists 7 moderate advisories
  for `next`, fixed in 16.4.5. The critical and high findings it also lists are in test tooling
  (`vitest`/`tinypool`, `glob`) and in `multer` under the shared Node library, not in the web app's
  own runtime code.
- **Fixed: merchant API tokens carried the CUSTOMER role.** Keycloak gives the realm's default roles
  (which include `CUSTOMER`) to every service account. merchant-service now removes them from each
  merchant's API client when it creates it, so a merchant token holds `MERCHANT_API` only and
  `GET /api/v1/parcels/mine` answers 403. Clients created before the fix keep the extra roles until they
  are removed in the Keycloak admin console (**Clients → merchant-… → Service account roles → unassign
  `default-roles-swiftdrop`**) or Keycloak is re-imported.
