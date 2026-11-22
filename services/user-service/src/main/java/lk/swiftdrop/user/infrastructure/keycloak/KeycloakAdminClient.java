package lk.swiftdrop.user.infrastructure.keycloak;

import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.user.application.exception.IdentityProviderUnavailableException;
import lk.swiftdrop.user.application.model.DirectoryUser;
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
import java.util.function.Function;

/**
 * The connection to the Keycloak Admin REST API that both Keycloak adapters share, signed in as the
 * {@code user-service-admin} service account (OAuth 2.0 client credentials).
 *
 * <p>The service-account token is cached until shortly before it expires. A 401 means Keycloak
 * dropped the token early (for example after a restart), so the call is retried once with a new one.
 * Transport failures become {@link IdentityProviderUnavailableException}; 404 and 409 are left to
 * the adapter, which knows what they mean for its own request.
 */
public class KeycloakAdminClient {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminClient.class);
    private static final int PAGE_SIZE = 200;
    private static final int MAX_RESULTS = 2000;
    private static final long EXPIRY_MARGIN_SECONDS = 30;
    static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_LIST = new ParameterizedTypeReference<>() {
    };

    private final RestClient rest;
    private final String realm;
    private final String clientId;
    private final String clientSecret;
    private final Clock clock;

    private volatile CachedToken cached;

    private record CachedToken(String value, Instant refreshAfter) {
    }

    public KeycloakAdminClient(RestClient rest, String realm, String clientId, String clientSecret, Clock clock) {
        this.rest = rest;
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clock = clock;
    }

    RestClient rest() {
        return rest;
    }

    String realm() {
        return realm;
    }

    /** Runs a call with the service-account token; see the class comment for the error handling. */
    <T> T withToken(Function<String, T> call) {
        try {
            try {
                return call.apply(serviceAccountToken());
            } catch (HttpClientErrorException.Unauthorized expired) {
                cached = null;
                return call.apply(serviceAccountToken());
            }
        } catch (HttpClientErrorException.Unauthorized again) {
            throw new IdentityProviderUnavailableException("The identity provider rejected the service account");
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Conflict forCaller) {
            throw forCaller;
        } catch (HttpClientErrorException.Forbidden forbidden) {
            log.error("Keycloak refused the user-service-admin service account (check its realm-management roles)");
            throw new IdentityProviderUnavailableException("The identity provider refused the request");
        } catch (RestClientException failed) {
            log.warn("Keycloak Admin API call failed: {}", failed.getMessage());
            throw new IdentityProviderUnavailableException("The identity provider is not reachable right now");
        }
    }

    /** A call about one user: an unknown id is reported as 404 USER_NOT_FOUND. */
    <T> T onUser(String userId, Function<String, T> call) {
        try {
            return withToken(call);
        } catch (HttpClientErrorException.NotFound unknown) {
            throw ResourceNotFoundException.of("User", userId);
        }
    }

    /**
     * Reads every page of a list endpoint. The template's variables are the realm, then
     * {@code extraVariables}, then {@code first} and {@code max}.
     */
    List<Map<String, Object>> pages(String uriTemplate, Object... extraVariables) {
        List<Map<String, Object>> all = new ArrayList<>();
        for (int first = 0; first < MAX_RESULTS; first += PAGE_SIZE) {
            Object[] variables = new Object[extraVariables.length + 3];
            variables[0] = realm;
            System.arraycopy(extraVariables, 0, variables, 1, extraVariables.length);
            variables[variables.length - 2] = first;
            variables[variables.length - 1] = PAGE_SIZE;
            List<Map<String, Object>> page = withToken(token -> rest.get()
                    .uri(uriTemplate, variables)
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .body(JSON_LIST));
            if (page == null || page.isEmpty()) {
                return all;
            }
            all.addAll(page);
            if (page.size() < PAGE_SIZE) {
                return all;
            }
        }
        return all;
    }

    static DirectoryUser toDirectoryUser(Map<?, ?> json) {
        return new DirectoryUser(
                String.valueOf(json.get("id")),
                json.get("email") instanceof String email ? email : null,
                json.get("firstName") instanceof String first ? first : null,
                json.get("lastName") instanceof String last ? last : null,
                !Boolean.FALSE.equals(json.get("enabled")));
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
            response = rest.post()
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
}
