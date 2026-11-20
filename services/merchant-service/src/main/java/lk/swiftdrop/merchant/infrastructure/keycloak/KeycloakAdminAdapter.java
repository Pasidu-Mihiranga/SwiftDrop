package lk.swiftdrop.merchant.infrastructure.keycloak;

import lk.swiftdrop.merchant.application.exception.IdentityProviderUnavailableException;
import lk.swiftdrop.merchant.application.exception.MerchantOwnerMissingException;
import lk.swiftdrop.merchant.application.port.out.IdentityProvisioner;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Adapter over the Keycloak Admin REST API (Adapter pattern), signed in as the
 * {@code merchant-service-admin} service account with OAuth 2.0 client credentials. Its
 * realm-management roles (manage-clients, manage-users, their view/query companions, and
 * view-realm to look up realm roles by name) are set
 * in infra/keycloak/realm-swiftdrop.json; no other service holds them.
 *
 * <p>Every operation looks before it creates, so repeating it after a partial failure finishes
 * the job instead of failing on what the first attempt already made.
 *
 * <p>The service-account token is cached until shortly before it expires. A 401 means Keycloak
 * dropped it early (for example after a restart), so the operation is retried once with a new one.
 */
public class KeycloakAdminAdapter implements IdentityProvisioner {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminAdapter.class);

    static final String CLIENT_PREFIX = "merchant-";
    static final String API_AUDIENCE = "swiftdrop-api";
    static final String MERCHANT_API_ROLE = "MERCHANT_API";
    static final String MERCHANT_ROLE = "MERCHANT";
    static final String MERCHANT_ID_ATTRIBUTE = "merchant_id";

    private static final long EXPIRY_MARGIN_SECONDS = 30;
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<Map<String, Object>> OBJECT = new ParameterizedTypeReference<>() {
    };

    private final RestClient keycloak;
    private final String realm;
    private final String clientId;
    private final String clientSecret;
    private final Clock clock;

    private volatile CachedToken cached;

    private record CachedToken(String value, Instant refreshAfter) {
    }

    public KeycloakAdminAdapter(RestClient keycloak, String realm, String clientId, String clientSecret, Clock clock) {
        this.keycloak = keycloak;
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clock = clock;
    }

    @Override
    public String provisionClient(UUID merchantId) {
        return withFreshTokenOnce(() -> {
            String merchantClientId = CLIENT_PREFIX + merchantId;
            String clientUuid = findClient(merchantClientId).orElseGet(() -> createClient(merchantClientId, merchantId));
            Map<String, Object> serviceAccount = get("/admin/realms/{realm}/clients/{id}/service-account-user", clientUuid);
            addRealmRole(String.valueOf(serviceAccount.get("id")), MERCHANT_API_ROLE);
            return merchantClientId;
        });
    }

    @Override
    public void grantMerchantRole(String ownerUserId, UUID merchantId) {
        withFreshTokenOnce(() -> {
            Map<String, Object> user;
            try {
                user = get("/admin/realms/{realm}/users/{id}", ownerUserId);
            } catch (HttpClientErrorException.NotFound gone) {
                throw new MerchantOwnerMissingException();
            }
            // The whole representation goes back, so the user's other attributes are kept.
            Map<String, Object> attributes = user.get("attributes") instanceof Map<?, ?> existing
                    ? new HashMap<>(castMap(existing)) : new HashMap<>();
            attributes.put(MERCHANT_ID_ATTRIBUTE, List.of(merchantId.toString()));
            user.put("attributes", attributes);
            keycloak.put().uri("/admin/realms/{realm}/users/{id}", realm, ownerUserId)
                    .headers(h -> h.setBearerAuth(serviceAccountToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(user)
                    .retrieve()
                    .toBodilessEntity();
            addRealmRole(ownerUserId, MERCHANT_ROLE);
            return null;
        });
    }

    private Optional<String> findClient(String merchantClientId) {
        List<Map<String, Object>> found = keycloak.get()
                .uri("/admin/realms/{realm}/clients?clientId={clientId}", realm, merchantClientId)
                .headers(h -> h.setBearerAuth(serviceAccountToken()))
                .retrieve()
                .body(LIST);
        return found == null ? Optional.empty() : found.stream()
                .filter(client -> merchantClientId.equals(client.get("clientId")))
                .map(client -> String.valueOf(client.get("id")))
                .findFirst();
    }

    /** Creates the confidential client; if a concurrent attempt created it first, uses that one. */
    private String createClient(String merchantClientId, UUID merchantId) {
        try {
            keycloak.post().uri("/admin/realms/{realm}/clients", realm)
                    .headers(h -> h.setBearerAuth(serviceAccountToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(clientRepresentation(merchantClientId, merchantId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Conflict alreadyThere) {
            log.debug("Client {} already exists; reusing it", merchantClientId);
        }
        return findClient(merchantClientId).orElseThrow(() ->
                new IdentityProviderUnavailableException("The identity provider did not keep the new API client"));
    }

    /**
     * A machine client: client credentials only, no browser or password login. Its tokens carry the
     * swiftdrop-api audience and a merchant_id that the merchant cannot change.
     */
    static Map<String, Object> clientRepresentation(String merchantClientId, UUID merchantId) {
        return Map.ofEntries(
                Map.entry("clientId", merchantClientId),
                Map.entry("name", "Merchant API client " + merchantId),
                Map.entry("description", "Created by merchant-service when the merchant was approved."),
                Map.entry("enabled", true),
                Map.entry("publicClient", false),
                Map.entry("clientAuthenticatorType", "client-secret"),
                Map.entry("standardFlowEnabled", false),
                Map.entry("implicitFlowEnabled", false),
                Map.entry("directAccessGrantsEnabled", false),
                Map.entry("serviceAccountsEnabled", true),
                Map.entry("protocolMappers", List.of(
                        Map.of("name", "audience-" + API_AUDIENCE, "protocol", "openid-connect",
                                "protocolMapper", "oidc-audience-mapper",
                                "config", Map.of("included.custom.audience", API_AUDIENCE,
                                        "access.token.claim", "true", "id.token.claim", "false",
                                        "introspection.token.claim", "true")),
                        Map.of("name", "merchant-id", "protocol", "openid-connect",
                                "protocolMapper", "oidc-hardcoded-claim-mapper",
                                "config", Map.of("claim.name", MERCHANT_ID_ATTRIBUTE,
                                        "claim.value", merchantId.toString(), "jsonType.label", "String",
                                        "access.token.claim", "true", "id.token.claim", "false",
                                        "userinfo.token.claim", "false", "introspection.token.claim", "true")))));
    }

    /** Adding a role the user already holds changes nothing, so this is safe to repeat. */
    private void addRealmRole(String userId, String roleName) {
        Map<String, Object> role = get("/admin/realms/{realm}/roles/{name}", roleName);
        keycloak.post().uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, userId)
                .headers(h -> h.setBearerAuth(serviceAccountToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(Map.of("id", role.get("id"), "name", role.get("name"))))
                .retrieve()
                .toBodilessEntity();
    }

    private Map<String, Object> get(String path, String id) {
        Map<String, Object> body = keycloak.get().uri(path, realm, id)
                .headers(h -> h.setBearerAuth(serviceAccountToken()))
                .retrieve()
                .body(OBJECT);
        if (body == null) {
            throw new IdentityProviderUnavailableException("The identity provider returned an empty answer");
        }
        return new HashMap<>(body);
    }

    /**
     * Runs one whole operation, translating failures into the 503 the API answers with. A 401 means
     * the cached token was dropped, so the operation runs once more with a new one.
     */
    private <T> T withFreshTokenOnce(Supplier<T> operation) {
        try {
            return translated(operation);
        } catch (HttpClientErrorException.Unauthorized expired) {
            cached = null;
            try {
                return translated(operation);
            } catch (HttpClientErrorException.Unauthorized again) {
                throw new IdentityProviderUnavailableException("The identity provider rejected the service account");
            }
        }
    }

    private <T> T translated(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (HttpClientErrorException.Unauthorized expired) {
            throw expired;
        } catch (HttpClientErrorException.Forbidden forbidden) {
            log.error("Keycloak refused the merchant-service-admin service account "
                    + "(it needs realm-management manage-clients, manage-users and view-realm)");
            throw new IdentityProviderUnavailableException("The identity provider refused to provision the merchant");
        } catch (RestClientException failed) {
            // The message carries only the status and URL path; no token or secret is logged.
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
        Map<String, Object> response;
        try {
            response = keycloak.post()
                    .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(OBJECT);
        } catch (HttpClientErrorException rejected) {
            if (rejected.getStatusCode() == HttpStatus.UNAUTHORIZED || rejected.getStatusCode() == HttpStatus.BAD_REQUEST) {
                log.error("Keycloak rejected the merchant-service-admin client credentials "
                        + "(check KC_MERCHANT_SERVICE_CLIENT_SECRET)");
            }
            throw new IdentityProviderUnavailableException("The identity provider rejected the service account");
        }
        if (response == null || !(response.get("access_token") instanceof String token)) {
            throw new IdentityProviderUnavailableException("The identity provider returned no access token");
        }
        long expiresIn = response.get("expires_in") instanceof Number seconds ? seconds.longValue() : 60;
        cached = new CachedToken(token, clock.instant().plusSeconds(Math.max(0, expiresIn - EXPIRY_MARGIN_SECONDS)));
        return token;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
