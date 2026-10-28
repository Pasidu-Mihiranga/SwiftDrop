# M2 Pricing & Zones diagrams

These Mermaid sources support [Issue #15](../../04-m2-pricing-zones-issue-15.md)
and the [design notes](../../05-m2-design-notes.md).

| View | Diagram |
|---|---|
| Use cases | [usecase-m2-pricing.mmd](usecase-m2-pricing.mmd) |
| Service integration | [integration-m2-pricing.mmd](integration-m2-pricing.mmd) |
| Pricing layers | [architecture-m2-pricing-layers.mmd](architecture-m2-pricing-layers.mmd) |
| Data model | [er-m2-pricing-zones.mmd](er-m2-pricing-zones.mmd) |
| Pricing rules | [class-m2-pricing-strategy.mmd](class-m2-pricing-strategy.mmd) |
| Fee calculation | [activity-m2-fee-calculation.mmd](activity-m2-fee-calculation.mmd) |
| Rate-card selection | [activity-m2-rate-card-selection.mmd](activity-m2-rate-card-selection.mmd) |
| Fee estimate | [sequence-m2-fee-estimate.mmd](sequence-m2-fee-estimate.mmd) |
| Quote lookup | [sequence-m2-quote-lookup.mmd](sequence-m2-quote-lookup.mmd) |
| Admin rate change | [sequence-m2-admin-rate-change.mmd](sequence-m2-admin-rate-change.mmd) |
| Kubernetes deployment | [deployment-m2-kubernetes.mmd](deployment-m2-kubernetes.mmd) |

The Kubernetes diagram now describes a deployment that exists: the charts are in
`infra/helm` and the dev slice has been installed on kind. What that run did and did not
prove is recorded in the Phase 4 verification section of
[../04-m2-pricing-zones-issue-15.md](../04-m2-pricing-zones-issue-15.md).

The detailed ER diagram reflects the pricing database schema, and the cross-service
`er-v1.mmd` has been reconciled with it: surrogate UUID keys on `ZONE` and `RATE_CARD`,
`zone.code` as a unique key, `district_zone.zone_id`, and the `cod_fee_pct` and
`max_dimensions_cm` columns that migrations V2 to V4 actually create.

No PNG or SVG exports are checked in. Mermaid CLI cannot launch its headless browser in
this environment, so exports for the design PDF have to be produced where it can:

```bash
npx -y @mermaid-js/mermaid-cli -i <diagram>.mmd -o <diagram>.svg
```
