# Agent guidance for SwiftDrop

Follow `CONTRIBUTING.md` and the existing service conventions. For issue #15
(M2 Pricing & Zones), use `doc/04-m2-pricing-zones-issue-15.md` as the working
checklist and update it as each reviewable step is completed.

## Maintenance rules

- Do not commit unless the user explicitly asks. At the end of each manageable
  step, say what is ready for review and that they can ask for a commit.
- Do not push unless the user explicitly asks. Do not push to `main` unless
  the user explicitly asks to push `main`.
- Do not add an agent co-author trailer, or any `Co-authored-by` line, to
  commits.
- When the user asks for a guide, write it step by step in simple plain
  English. One action per step. Say how they know that step worked before
  the next one.

## M2 scope

- Own `services/pricing-service`, the public quote and admin pricing UI, and
  `infra/helm` as described in `doc/03-team-contribution.md`.
- Keep changes in other services limited to contracts or integration points
  needed for pricing. Coordinate the quote validation shape with the parcel
  service owner before changing that contract.
- Work in small, reviewable steps on `feat/m2-pricing-zones`.

## Implementation rules for issue #15

- Keep pricing rules, rates, zone assignments, and size classes in the pricing
  database and managed seed/configuration data. Do not embed demo prices or
  district-to-zone assignments in Java or frontend components.
- Treat `contracts/openapi/pricing-service.v1.yaml` as the API contract. Use
  versioned `/api/v1` routes, Bean Validation, shared problem+json errors,
  Keycloak roles, and the existing web BFF/gateway helpers.
- Get service addresses, credentials, quote TTL, and deployment settings from
  configuration. Do not hard-code API hosts or secrets.
- Use the web app's CSS variables/design tokens for colors. Do not add inline
  hex colors or duplicate palette values in M2 components.
- Add new Flyway migrations; do not rewrite a migration that has already been
  shared. Keep the `domain` layer free of Spring and JPA types.
- Keep Helm settings in values files or Kubernetes Secrets and reuse the
  library chart for shared service templates.
- Follow the maintenance rules above for commits, pushes, and guides.
