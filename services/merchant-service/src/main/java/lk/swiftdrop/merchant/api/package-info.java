/**
 * PRESENTATION layer — REST controllers ({@code api.v1}), request/response DTOs ({@code api.dto})
 * and DTO mappers ({@code api.mapper}).
 * <p>
 * Rules (enforced by {@code LayeredArchitectureTest}): controllers only validate input, read the
 * {@code AuthenticatedUser} and delegate to the application layer. No business logic, no repositories.
 */
package lk.swiftdrop.merchant.api;
