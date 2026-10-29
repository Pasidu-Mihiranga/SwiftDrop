# M2 Pricing & Zones diagrams

These Mermaid sources support [Issue #15](../../04-m2-pricing-zones-issue-15.md)
and the [design notes](../../05-m2-design-notes.md).

| View | Source | Export |
|---|---|---|
| Use cases | [usecase-m2-pricing.mmd](usecase-m2-pricing.mmd) | [SVG](usecase-m2-pricing.svg), [PNG](usecase-m2-pricing.png) |
| Service integration | [integration-m2-pricing.mmd](integration-m2-pricing.mmd) | [SVG](integration-m2-pricing.svg), [PNG](integration-m2-pricing.png) |
| Pricing layers | [architecture-m2-pricing-layers.mmd](architecture-m2-pricing-layers.mmd) | [SVG](architecture-m2-pricing-layers.svg), [PNG](architecture-m2-pricing-layers.png) |
| Data model | [er-m2-pricing-zones.mmd](er-m2-pricing-zones.mmd) | [SVG](er-m2-pricing-zones.svg), [PNG](er-m2-pricing-zones.png) |
| Pricing rules | [class-m2-pricing-strategy.mmd](class-m2-pricing-strategy.mmd) | [SVG](class-m2-pricing-strategy.svg), [PNG](class-m2-pricing-strategy.png) |
| Fee calculation | [activity-m2-fee-calculation.mmd](activity-m2-fee-calculation.mmd) | [SVG](activity-m2-fee-calculation.svg), [PNG](activity-m2-fee-calculation.png) |
| Rate-card selection | [activity-m2-rate-card-selection.mmd](activity-m2-rate-card-selection.mmd) | [SVG](activity-m2-rate-card-selection.svg), [PNG](activity-m2-rate-card-selection.png) |
| Fee estimate | [sequence-m2-fee-estimate.mmd](sequence-m2-fee-estimate.mmd) | [SVG](sequence-m2-fee-estimate.svg), [PNG](sequence-m2-fee-estimate.png) |
| Quote lookup | [sequence-m2-quote-lookup.mmd](sequence-m2-quote-lookup.mmd) | [SVG](sequence-m2-quote-lookup.svg), [PNG](sequence-m2-quote-lookup.png) |
| Admin rate change | [sequence-m2-admin-rate-change.mmd](sequence-m2-admin-rate-change.mmd) | [SVG](sequence-m2-admin-rate-change.svg), [PNG](sequence-m2-admin-rate-change.png) |
| Kubernetes deployment | [deployment-m2-kubernetes.mmd](deployment-m2-kubernetes.mmd) | [SVG](deployment-m2-kubernetes.svg), [PNG](deployment-m2-kubernetes.png) |

The Kubernetes diagram now describes a deployment that exists: the charts are in
`infra/helm` and the dev slice has been installed on kind. What that run did and did not
prove is recorded in the Phase 4 verification section of
[../04-m2-pricing-zones-issue-15.md](../04-m2-pricing-zones-issue-15.md).

The detailed ER diagram reflects the pricing database schema, and the cross-service
`er-v1.mmd` has been reconciled with it: surrogate UUID keys on `ZONE` and `RATE_CARD`,
`zone.code` as a unique key, `district_zone.zone_id`, and the `cod_fee_pct` and
`max_dimensions_cm` columns that migrations V2 to V4 actually create.

The SVG and PNG exports are drawn in the same black-and-white UML style as the other
design diagrams (M10 and the system diagrams), so the design PDF reads as one document. Each
export shows the same participants, steps, guards and relationships as its Mermaid source:

- Sequence diagrams: underlined participant names, activation bars, solid calls with filled
  arrowheads, dashed replies with open arrowheads, `alt` fragments and folded-corner notes.
- Activity diagrams: a "Use Case" frame with swimlanes, initial and final nodes, decision and
  merge diamonds with `[guard]` labels, and notes on dotted connectors.
- Class diagram: realization, generalization, aggregation and composition in UML notation.
- Data model: crow's-foot lines for foreign keys; dashed lines for values copied into the
  Redis snapshot.
- Deployment: boxes with a black title strip are M2 deliverables; plain boxes are context.

The Mermaid files stay the editable source. If one changes, redraw its export to match. The
architecture export states the injected `Clock` inside the PricingClockConfiguration box
instead of drawing the two arrows, because those arrows would have to cross the outbound ports.
