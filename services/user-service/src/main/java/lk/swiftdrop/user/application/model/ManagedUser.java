package lk.swiftdrop.user.application.model;

import lk.swiftdrop.user.domain.model.UserRole;

import java.util.EnumSet;
import java.util.Set;

/**
 * A person account as admin user management shows it.
 *
 * @param roles effective person roles; every account holds CUSTOMER through Keycloak's default roles
 */
public record ManagedUser(String id, String email, String fullName, boolean enabled, Set<UserRole> roles) {

    public ManagedUser {
        roles = roles == null || roles.isEmpty() ? Set.of() : Set.copyOf(roles);
    }

    /** Direct role mappings plus CUSTOMER, which Keycloak's default roles give every account. */
    public static Set<UserRole> effectiveRoles(Set<UserRole> direct) {
        Set<UserRole> roles = EnumSet.of(UserRole.CUSTOMER);
        roles.addAll(direct);
        return roles;
    }

    public static ManagedUser of(DirectoryUser user, Set<UserRole> directRoles) {
        return new ManagedUser(user.id(), user.email(), user.displayName(), user.enabled(), effectiveRoles(directRoles));
    }
}
