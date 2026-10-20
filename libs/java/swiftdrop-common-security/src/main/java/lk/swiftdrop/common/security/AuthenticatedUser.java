package lk.swiftdrop.common.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The caller of the current request, built from the Keycloak access token. Inject it into a
 * controller method simply by declaring a parameter of this type.
 *
 * @param id         Keycloak subject (stable user id; for merchant API clients, the service-account id)
 * @param email      email address, empty for machine clients
 * @param name       display name, empty for machine clients
 * @param roles      SwiftDrop realm roles, e.g. {@code COURIER}
 * @param merchantId merchant the caller acts for (merchant users and merchant API clients only)
 */
public record AuthenticatedUser(String id, Optional<String> email, Optional<String> name, Set<String> roles,
                                Optional<String> merchantId) {

    public static AuthenticatedUser from(Jwt jwt) {
        Set<String> roles = KeycloakRealmRoleConverter.realmRoles(jwt).stream()
                .filter(role -> role.equals(role.toUpperCase()))
                .collect(Collectors.toUnmodifiableSet());
        return new AuthenticatedUser(
                jwt.getSubject(),
                Optional.ofNullable(jwt.getClaimAsString("email")),
                Optional.ofNullable(jwt.getClaimAsString("name")),
                roles,
                Optional.ofNullable(jwt.getClaimAsString("merchant_id")));
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean hasAnyRole(String... candidates) {
        for (String candidate : candidates) {
            if (roles.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    /** Staff (dispatcher/admin) can see every parcel; everyone else only their own. */
    public boolean isStaff() {
        return hasAnyRole(Roles.DISPATCHER, Roles.ADMIN);
    }
}
