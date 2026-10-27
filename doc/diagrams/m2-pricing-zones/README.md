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
| Planned Kubernetes deployment | [deployment-m2-kubernetes.mmd](deployment-m2-kubernetes.mmd) |

The Kubernetes diagram is a planned design, not an implemented deployment.
The detailed ER diagram reflects the pricing database schema; the cross-service
`er-v1.mmd` still needs reconciliation.
