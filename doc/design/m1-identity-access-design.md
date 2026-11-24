# M1 Identity & Access: authentication and authorisation decisions

Owner: M1 (Warsavithana T.M., 234219R). Scope: Keycloak realm `swiftdrop`, `api-gateway`,
`user-service`, and sign-in, profile and admin user pages in `apps/web`.

Diagrams (in `doc/diagrams/m1-identity-access/`):

- `01-use-cases-customer` and `02-use-cases-identity-admin`: customer and identity-admin use cases
- `03-er-diagram`: users, roles, addresses and courier profiles
- `04-architecture`: Keycloak, gateway, user-service and the web BFF
- `05-seq-google-login`: OAuth 2.0 / Google sign-in sequence
- `06-seq-first-profile-jwt-chain`: first API call, JWT chain and just-in-time profile
- `07-seq-admin-create-courier`: admin creates a courier account
- `08-activity-route-guard`: role-based page guard

## 1. One identity provider: Keycloak

**Decision.** Every person and every machine client signs in through Keycloak (realm `swiftdrop`).
No SwiftDrop service stores or checks a password.

**Why.**
- Passwords are hashed by Keycloak (Argon2 by default), which meets the "hashed passwords"
  requirement without writing our own crypto. The realm enforces a password policy (at least 8
  characters with upper case, lower case and a digit, not the username) and brute-force protection
  (an account is locked for a while after repeated wrong passwords).
- One issuer means one token format for the whole platform. The gateway and every service check
  the same signature, issuer and audience.
- Keycloak gives us e-mail verification, password reset, account lock-out and the Admin API for free.

**Alternatives rejected.** A custom auth service (more code to get wrong, no social login) and Auth.js
with a database adapter (Next.js would own identity, but the Java and Node services also need it).

## 2. Google sign-in through Keycloak identity brokering

**Decision.** "Continue with Google" sends the user to Keycloak with `kc_idp_hint=google`. Keycloak
talks to Google and then issues its own token.

**Why.** Services only ever see Keycloak tokens, so they need no Google-specific code. A Google user
gets the default role `CUSTOMER` on first sign-in, the same as a self-registered user.
`trustEmail` is on for Google, because Google has already verified the address.

## 3. The web app is a BFF; tokens never reach the browser

**Decision.** Next.js runs the OAuth 2.0 Authorization Code flow with PKCE as a confidential client
(Auth.js v5). Access, refresh and ID tokens are kept in an encrypted, HTTP-only session cookie.
Pages and server actions call the gateway from the server with the user's token.

**Why.**
- No token in `localStorage` or JavaScript, so a cross-site scripting bug cannot steal it.
- The browser only sees what the session exposes: the user's id, name, e-mail and roles.
- Expired access tokens are refreshed on the server. If Keycloak ends the session, the session is
  marked `RefreshAccessTokenError` and the user is sent to sign in again.

**Sign-out** is federated: the app calls Keycloak's `end_session_endpoint` with `id_token_hint`, so the
Keycloak (and Google) session ends too. Signing out of SwiftDrop really signs you out.

## 4. Roles and who may hold them

| Role | How it is given |
|---|---|
| CUSTOMER | Everyone, through the realm's default roles (self sign-up and Google) |
| MERCHANT | Merchant onboarding (M7) |
| COURIER, DISPATCHER, ADMIN | Only by an admin, on the admin Users page |

**Decision.** Staff accounts cannot be self-registered. An admin creates them through
`user-service`, which calls the Keycloak Admin API with its own service account
(`user-service-admin`, only the `manage-users`, `view-users`, `query-*` and `view-realm` permissions).

**Why.**
- The admin never chooses or sees a password. Keycloak e-mails a set-your-password link
  (`UPDATE_PASSWORD`, valid 24 hours). Opening it also proves the e-mail address.
- If a later step fails after Keycloak created the account, the account is deleted again, so a retry
  does not fail with "already registered".
- An admin cannot disable their own account or remove their own ADMIN role, so the system cannot be
  left without an administrator by mistake.
- Disabling an account also ends its sessions at once.

## 5. Authorisation happens twice

**Decision.** The gateway authenticates; each service authorises.

1. **Gateway.** Checks the JWT signature against Keycloak's keys, the issuer and the audience
   `swiftdrop-api`. Only the paths in `swiftdrop.gateway.public-paths` (health, public tracking,
   quotes, zones, the assistant, the tracking websocket handshake) are open without a token. Anything
   else without a valid token gets `401`.
2. **Service.** Maps realm roles to Spring authorities and checks them on each endpoint
   (`@PreAuthorize`), for example `ADMIN` for `/api/v1/admin/users`. Non-admins get `403`.
3. **Ownership.** A user asking for another user's address gets `404`, not `403`, so the answer does
   not reveal that the address exists.
4. **Web app.** `proxy.ts` sends a signed-in user without the right role to `/forbidden` and a
   signed-out user to the sign-in page. This is for a good experience only; the API checks again.

**Why both.** If one layer is misconfigured, the other still protects the data. Services never trust a
role or user id sent in a header; they read it from the verified token.

## 6. Profiles are created just in time

**Decision.** `user_profile.id` is the Keycloak user id. The first `GET /api/v1/users/me` creates the
profile from the token (`INSERT ... ON CONFLICT DO NOTHING`).

**Why.** No sign-up event or synchronisation job is needed. Two simultaneous first requests are safe.
Couriers created by an admin still appear in the courier directory before their first sign-in,
because the directory reads role members from Keycloak.

## 7. Personal data at rest

**Decision.** Phone numbers and address lines are encrypted with AES-256-GCM before they reach the
database, using one shared library (`libs/java/swiftdrop-pii`) in user-, parcel- and merchant-service.
The key comes from `PII_ENCRYPTION_KEY`; a service refuses to start without a valid 32-byte key.

**Why.**
- A database dump or backup does not reveal personal data.
- GCM authenticates each value, so a value changed in the database fails to decrypt instead of
  returning wrong data.
- One library means one format (`v1:` + IV + ciphertext + tag) and one key everywhere, and a fix
  reaches every service.
- Logs mask phone numbers and e-mail addresses, and error messages never contain them.

City and district stay readable, because pricing and dispatch need them.

## 8. Protecting the platform: rate limits and failures

**Rate limiting.** The gateway uses Spring Cloud Gateway's `RequestRateLimiter` with token buckets in
Redis, shared by every gateway replica:

| Caller | Counted by | Limit |
|---|---|---|
| Signed-in person | Keycloak user id | 300 requests a minute |
| Merchant API client | `client_id` | 300 requests a minute |
| Anonymous | IP address | 60 requests a minute |

Over the limit the gateway answers `429` with `Retry-After`. The web server passes the visitor's IP
on in `X-Forwarded-For`, and the gateway believes that header only from trusted proxies
(`GATEWAY_TRUSTED_PROXIES`); otherwise any caller could fake an IP to dodge the limit. If Redis is
down, requests are allowed: an outage of the limiter should not take the whole platform down.

**Unavailable services.** If the service behind a route cannot be reached, the gateway answers
`503` problem+json (`SERVICE_UNAVAILABLE`) with `Retry-After`. The body never names the service or
its address; the gateway log does, with the correlation id.

## 9. Observability

The Grafana dashboard **"SwiftDrop – Identity & Access (M1)"** shows:
- successful and failed sign-ins (Keycloak user-event metrics), failed sign-ins by reason, and
  sign-ins by method (password or Google);
- new accounts and failed token refreshes;
- the gateway's 401, 403, 429 and 503 answers over time.

A rise in failed sign-ins across many accounts is the first sign of password guessing.

## 10. Known limits and production notes

- The development realm allows plain HTTP (`sslRequired: none`) so it works on `localhost`.
  Production must use `external` (HTTPS for everything except private networks).
- Keycloak only reads the realm file when the realm is first created. Realm changes (e-mail
  verification, login theme) reach an existing local Keycloak only after a re-import.
- In Kubernetes, the gateway needs `REDIS_*` and `GATEWAY_TRUSTED_PROXIES` set to the ingress
  network; without Redis the limiter lets everything through.
- Access tokens live 5 minutes; a disabled user can keep using an already issued token until it
  expires, but cannot refresh it.
