/**
 * APPLICATION layer — use cases. {@code application.command} changes state (one transaction per
 * use case, events written through the outbox), {@code application.query} reads, and
 * {@code application.port.out} declares interfaces for everything external (other services,
 * storage, messaging) which the infrastructure layer implements (Dependency Inversion).
 * <p>
 * May use the domain layer. Must not depend on {@code api} or {@code infrastructure}.
 */
package lk.swiftdrop.reporting.application;
