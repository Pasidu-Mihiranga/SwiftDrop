/**
 * DOMAIN layer — the business model: aggregates and value objects ({@code domain.model}),
 * rules and policies ({@code domain.policy}, e.g. state machines, pricing strategies) and
 * domain exceptions ({@code domain.exception}).
 * <p>
 * Plain Java only: no Spring, no JPA, no HTTP. This keeps the rules fast to unit test and
 * independent of frameworks.
 */
package lk.swiftdrop.dispatch.domain;
