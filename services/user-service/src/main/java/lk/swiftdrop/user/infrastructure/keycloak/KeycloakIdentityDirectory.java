package lk.swiftdrop.user.infrastructure.keycloak;

import lk.swiftdrop.user.application.exception.IdentityProviderUnavailableException;
import lk.swiftdrop.user.application.model.DirectoryUser;
import lk.swiftdrop.user.application.port.out.IdentityDirectory;
import lk.swiftdrop.user.domain.model.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Adapter over the Keycloak Admin REST API, signed in as the {@code user-service-admin} service
 * account (OAuth 2.0 client credentials, realm-management view-users / query-users).
 *
 * <p>The service-account token is cached until shortly before it expires. A 401 means Keycloak
 * dropped the token early (for example after a restart), so the call is retried once with a new one.
 */
public class KeycloakIdentityDirectory implements IdentityDirectory {

    private static final Logger log = LoggerFactory.getLogger(KeycloakIdentityDirectory.class);
    private static final int PAGE_SIZE = 200;
    private static final long EXPIRY_MARGIN_SECONDS = 30;
    private static final ParameterizedTypeReference<List<Map<String, Object>>> USER_LIST = new ParameterizedTypeReference<>() {
    };

    private final RestClient keycloak;
    private final String realm;
    private final String clientId;
    private final String clientSecret;
    private final Clock clock;

    private volatile CachedToken cached;

    private record CachedToken(String value, Instant refreshAfter) {
    }

    public KeycloakIdentityDirectory(RestClient keycloak, String realm, String clientId, String clientSecret, Clock clock) {
        this.keycloak = keycloak;
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clock = clock;
    }

    @Override
    public List<DirectoryUser> usersWithRole(UserRole role) {
        try {
            return fetchUsersWithRole(role);
        } catch (HttpClientErrorException.Unauthorized expired) {
            cached = null;
            try {
                return fetchUsersWithRole(role);
            } catch (HttpClientErrorException.Unauthorized again) {
                throw new IdentityProviderUnavailableException("The identity provider rejected the service account");
            }
        }
    }

    private List<DirectoryUser> fetchUsersWithRole(UserRole role) {
        List<DirectoryUser> users = new ArrayList<>();
        try {
            for (int first = 0; ; first += PAGE_SIZE) {
                List<Map<String, Object>> page = keycloak.get()
                        .uri("/admin/realms/{realm}/roles/{role}/users?first={first}&max={max}&briefRepresentation=true",
                                realm, role.name(), first, PAGE_SIZE)
                        .headers(headers -> headers.setBearerAuth(serviceAccountToken()))
                        .retrieve()
                        .body(USER_LIST);
                if (page == null || page.isEmpty()) {
                    return users;
                }
                page.forEach(json -> users.add(toDirectoryUser(json)));
                if (page.size() < PAGE_SIZE) {
                    return users;
                }
            }
        } catch (HttpClientErrorException.Unauthorized expired) {
            throw expired;
        } catch (HttpClientErrorException.Forbidden forbidden) {
            log.error("Keycloak refused the user-service-admin service account (needs realm-management view-users)");
            throw new IdentityProviderUnavailableException("The identity provider refused to list users");
        } catch (RestClientException failed) {
            log.warn("Keycloak Admin API call failed: {}", failed.getMessage());
            throw new IdentityProviderUnavailableException("The identity provider is not reachable right now");
        }
    }

    private String serviceAccountToken() {
        CachedToken current = cached;
        if (current != null && clock.instant().isBefore(current.refreshAfter())) {
            return current.value();
        }
        if (clientSecret == null || clientSecret.isBlank()) {
            throw new IdentityProviderUnavailableException("The identity provider client secret is not configured");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        Map<?, ?> response;
        try {
            response = keycloak.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
        } catch (HttpClientErrorException rejected) {
            if (rejected.getStatusCode() == HttpStatus.UNAUTHORIZED || rejected.getStatusCode() == HttpStatus.BAD_REQUEST) {
                log.error("Keycloak rejected the user-service-admin client credentials (check KC_USER_SERVICE_CLIENT_SECRET)");
            }
            throw new IdentityProviderUnavailableException("The identity provider rejected the service account");
        } catch (RestClientException failed) {
            log.warn("Keycloak token request failed: {}", failed.getMessage());
            throw new IdentityProviderUnavailableException("The identity provider is not reachable right now");
        }
        if (response == null || !(response.get("access_token") instanceof String token)) {
            throw new IdentityProviderUnavailableException("The identity provider returned no access token");
        }
        long expiresIn = response.get("expires_in") instanceof Number seconds ? seconds.longValue() : 60;
        cached = new CachedToken(token, clock.instant().plusSeconds(Math.max(0, expiresIn - EXPIRY_MARGIN_SECONDS)));
        return token;
    }

    private static DirectoryUser toDirectoryUser(Map<String, Object> json) {
        return new DirectoryUser(
                String.valueOf(json.get("id")),
                json.get("email") instanceof String email ? email : null,
                json.get("firstName") instanceof String first ? first : null,
                json.get("lastName") instanceof String last ? last : null,
                !Boolean.FALSE.equals(json.get("enabled")));
    }
}
