package lk.swiftdrop.user.application.model;

import lk.swiftdrop.user.domain.model.UserRole;

import java.util.Set;

/**
 * What user-service needs to know about the caller, taken from the validated Keycloak token.
 *
 * @param subject Keycloak user id (JWT {@code sub})
 * @param email   e-mail address claim
 * @param name    display name claim, may be {@code null}
 * @param roles   person roles from {@code realm_access.roles}
 */
public record IdentityClaims(String subject, String email, String name, Set<UserRole> roles) {

    public IdentityClaims {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }
}
