package lk.swiftdrop.common.security;

/**
 * Keycloak realm roles. Use with {@code @PreAuthorize("hasRole('COURIER')")} or
 * {@code hasAnyRole(Roles.DISPATCHER, Roles.ADMIN)}.
 */
public final class Roles {

    public static final String CUSTOMER = "CUSTOMER";
    public static final String MERCHANT = "MERCHANT";
    /** Machine client of an approved merchant (OAuth2 client credentials). */
    public static final String MERCHANT_API = "MERCHANT_API";
    public static final String COURIER = "COURIER";
    public static final String DISPATCHER = "DISPATCHER";
    public static final String ADMIN = "ADMIN";

    private Roles() {
    }
}
