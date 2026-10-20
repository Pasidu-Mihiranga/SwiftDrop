/**
 * INFRASTRUCTURE layer — adapters: JPA entities and Spring Data repositories
 * ({@code infrastructure.persistence}), Kafka consumers ({@code infrastructure.messaging}),
 * REST clients for other services ({@code infrastructure.client}) and Spring configuration
 * ({@code infrastructure.config}). Implements the application layer's ports.
 * <p>
 * Nothing else may depend on this package directly; it is wired in through Spring.
 */
package lk.swiftdrop.__pkg__.infrastructure;
