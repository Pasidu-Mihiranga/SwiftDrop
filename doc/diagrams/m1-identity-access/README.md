# M1 Identity & Access diagrams

Design diagrams for M1 (Keycloak realm, `api-gateway` security, `user-service`, web login and role
routing), as listed for M1 in [../../03-team-contribution.md](../../03-team-contribution.md):
OAuth sequence, ER for users and roles, and the customer use case. The other diagrams support those
three and the M1 section of the design PDF.

| View | Source | Export |
|---|---|---|
| Customer use cases (required) | [usecase-m1-customer.mmd](usecase-m1-customer.mmd) | [SVG](usecase-m1-customer.svg), [PNG](usecase-m1-customer.png) |
| Identity and admin use cases | [usecase-m1-identity-admin.mmd](usecase-m1-identity-admin.mmd) | [SVG](usecase-m1-identity-admin.svg), [PNG](usecase-m1-identity-admin.png) |
| OAuth 2.0 login with Google (required) | [sequence-m1-google-login.mmd](sequence-m1-google-login.mmd) | [SVG](sequence-m1-google-login.svg), [PNG](sequence-m1-google-login.png) |
| First API call: JWT chain + JIT profile | [sequence-m1-first-profile-jwt-chain.mmd](sequence-m1-first-profile-jwt-chain.mmd) | [SVG](sequence-m1-first-profile-jwt-chain.svg), [PNG](sequence-m1-first-profile-jwt-chain.png) |
| Admin creates a courier account | [sequence-m1-admin-create-courier.mmd](sequence-m1-admin-create-courier.mmd) | [SVG](sequence-m1-admin-create-courier.svg), [PNG](sequence-m1-admin-create-courier.png) |
| Data model: users and roles (required) | [er-m1-users-roles.mmd](er-m1-users-roles.mmd) | [SVG](er-m1-users-roles.svg), [PNG](er-m1-users-roles.png) |
| Role-based page guard | [activity-m1-route-guard.mmd](activity-m1-route-guard.mmd) | [SVG](activity-m1-route-guard.svg), [PNG](activity-m1-route-guard.png) |
| Identity architecture (layers) | [architecture-m1-identity.mmd](architecture-m1-identity.mmd) | [SVG](architecture-m1-identity.svg), [PNG](architecture-m1-identity.png) |

## What is already built and what is design

- **Matches code that exists today:** the Google login sequence, the gateway part of the JWT chain and
  the route-guard activity follow `apps/web/src/auth.ts`, `proxy.ts`, `lib/roles.ts`,
  `app/api/bff/[...path]/route.ts`, `services/api-gateway`, `libs/java/swiftdrop-common-security` and
  `infra/keycloak/realm-swiftdrop.json` (clients, roles, `default-roles-swiftdrop` = CUSTOMER, Google IdP).
- **Design for M1 implementation:** user-service class names (`UserController`, `UserProfileService`,
  `AdminUserService`, `KeycloakAdminAdapter`, `IdentityAdminPort`, ...) and the `users_db` columns are the
  planned design, following `contracts/openapi/user-service.v1.yaml` and the data model in
  `doc/01-architecture.md` section 3. Update the diagrams if the implementation changes them.

## Design decisions shown in the diagrams

- **Keycloak owns identity, user-service owns profile data.** `user_profile.id` is the Keycloak subject
  (JWT `sub`); the profile is created just-in-time on the first `GET /api/v1/users/me`. No password is
  stored in `users_db`.
- **Google login is Keycloak identity brokering** (`kc_idp_hint=google`), so every user, Google or not,
  gets the same Keycloak JWT and role model. A first Google login gets `CUSTOMER` from
  `default-roles-swiftdrop`; an e-mail already registered with a password is linked after the user
  confirms with that password (no duplicate accounts).
- **Tokens never reach the browser.** Auth.js keeps them in an encrypted HTTP-only cookie; the BFF adds
  the bearer token on the server.
- **Defence in depth.** The gateway checks signature, issuer, audience `swiftdrop-api` and expiry; each
  service validates the same token again and enforces roles with `@PreAuthorize`. `proxy.ts` is a UX guard only.
- **Staff accounts go through the Keycloak Admin API** with the `user-service-admin` service account
  (client credentials). Keycloak e-mails a set-password link, so SwiftDrop never handles staff
  passwords. A failed database write deletes the Keycloak user again (compensation).
- **PII at rest:** `phone_enc`, `line1_enc` and `line2_enc` use an AES-GCM JPA `AttributeConverter`;
  logs mask phones and e-mails; error bodies are RFC 9457 problem+json without personal data.
- `ROLE` / `USER_ROLE` mirror Keycloak realm roles so user-service can answer queries such as "all
  couriers" without calling Keycloak.

## Style

The exports use the same black-and-white UML style as the M2, M10 and system diagrams: underlined
participant names, activation bars, solid calls and dashed replies, `alt` / `opt` fragments,
crow's-foot ER lines, swimlane activity frames and stick-figure actors. The Mermaid files stay the
editable source.
