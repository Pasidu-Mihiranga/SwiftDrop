# M2 Pricing & Zones diagrams

Mermaid sources and their exports for the M2 pricing and zones design: who uses the
service, how a fee is worked out, what is stored, and how it is deployed.

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

[m2-pricing-zones-diagrams.pdf](m2-pricing-zones-diagrams.pdf) collects all eleven
diagrams: each one gets a page listing its name, file paths and what it shows, followed
by a full page of the image.

The exports are drawn in the same black-and-white UML style as the other design
diagrams, so the design PDF reads as one document. Each export shows the same
participants, steps, guards and relationships as its Mermaid source:

- Sequence diagrams: underlined participant names, activation bars, solid calls with filled
  arrowheads, dashed replies with open arrowheads, `alt` fragments and folded-corner notes.
- Activity diagrams: a "Use Case" frame with swimlanes, initial and final nodes, decision and
  merge diamonds with `[guard]` labels, and notes on dotted connectors.
- Class diagram: realization, generalization, aggregation and composition in UML notation.
- Data model: crow's-foot lines for foreign keys; dashed lines for values copied into the
  Redis snapshot.
- Deployment: boxes with a black title strip are the pricing parts; plain boxes are context.

The detailed ER diagram matches the pricing database, and the cross-service `er-v1.mmd`
uses the same keys and columns.

The Mermaid files stay the editable source. If one changes, redraw its export to match.
