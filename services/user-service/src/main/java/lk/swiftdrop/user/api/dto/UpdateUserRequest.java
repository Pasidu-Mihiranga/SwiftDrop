package lk.swiftdrop.user.api.dto;

import lk.swiftdrop.user.domain.model.UserRole;

import java.util.Set;

/**
 * Changes to one account; a field left out is not changed.
 *
 * @param enabled {@code false} disables the account and signs the user out everywhere
 * @param roles   the complete set of staff roles (COURIER, DISPATCHER, ADMIN) the user should hold
 */
public record UpdateUserRequest(Boolean enabled, Set<UserRole> roles) {
}
