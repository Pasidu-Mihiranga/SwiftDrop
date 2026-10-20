package lk.swiftdrop.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticatedUserTest {

    private static Jwt token(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "RS256").subject("sub-1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        claims.forEach(builder::claim);
        return builder.build();
    }

    @Test
    void mapsOnlySwiftDropRealmRolesToAuthorities() {
        Jwt jwt = token(Map.of("realm_access", Map.of("roles", List.of("MERCHANT", "offline_access", "default-roles-swiftdrop"))));

        assertThat(new KeycloakRealmRoleConverter().convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_MERCHANT");
    }

    @Test
    void tokenWithoutRealmAccessHasNoRoles() {
        Jwt jwt = token(Map.of("scope", "openid"));

        assertThat(new KeycloakRealmRoleConverter().convert(jwt)).isEmpty();
        assertThat(AuthenticatedUser.from(jwt).roles()).isEmpty();
    }

    @Test
    void readsMerchantClaimAndRoleHelpers() {
        Jwt jwt = token(Map.of(
                "email", "merchant@swiftdrop.lk",
                "name", "Malith Merchant",
                "merchant_id", "00000000-0000-4000-8000-000000000001",
                "realm_access", Map.of("roles", List.of("MERCHANT"))));

        AuthenticatedUser user = AuthenticatedUser.from(jwt);

        assertThat(user.id()).isEqualTo("sub-1");
        assertThat(user.email()).contains("merchant@swiftdrop.lk");
        assertThat(user.name()).contains("Malith Merchant");
        assertThat(user.merchantId()).contains("00000000-0000-4000-8000-000000000001");
        assertThat(user.hasRole(Roles.MERCHANT)).isTrue();
        assertThat(user.hasAnyRole(Roles.ADMIN, Roles.MERCHANT)).isTrue();
        assertThat(user.hasAnyRole(Roles.ADMIN, Roles.COURIER)).isFalse();
        assertThat(user.isStaff()).isFalse();
    }

    @Test
    void dispatchersAndAdminsAreStaff() {
        Jwt jwt = token(Map.of("realm_access", Map.of("roles", List.of("DISPATCHER"))));

        assertThat(AuthenticatedUser.from(jwt).isStaff()).isTrue();
    }
}
