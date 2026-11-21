package lk.swiftdrop.user.domain.model;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * The realm roles a person can hold. {@code MERCHANT_API} is deliberately absent: it belongs to
 * machine clients, which have no profile.
 */
public enum UserRole {
    CUSTOMER, MERCHANT, COURIER, DISPATCHER, ADMIN;

    /**
     * The staff roles an admin hands out. CUSTOMER is every account's default, and MERCHANT is
     * granted by the merchant approval flow (M7), so admin user management leaves both alone.
     */
    public static final Set<UserRole> STAFF = Set.copyOf(EnumSet.of(COURIER, DISPATCHER, ADMIN));

    public boolean isStaff() {
        return STAFF.contains(this);
    }

    /** Keeps only the token roles that are person roles, ignoring Keycloak defaults and unknown names. */
    public static Set<UserRole> fromTokenRoles(Collection<String> tokenRoles) {
        Set<UserRole> roles = EnumSet.noneOf(UserRole.class);
        for (String role : tokenRoles) {
            for (UserRole candidate : values()) {
                if (candidate.name().equals(role)) {
                    roles.add(candidate);
                }
            }
        }
        return roles;
    }
}
