package lk.swiftdrop.merchant.infrastructure.keycloak;

import lk.swiftdrop.merchant.application.exception.MerchantOwnerMissingException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The adapter against a real Keycloak, the same version docker-compose runs, loaded with the
 * project's own realm file. This proves three things the mocked test cannot: the requests are
 * what Keycloak accepts, the realm gives merchant-service-admin exactly the rights it needs, and a
 * provisioned client really issues tokens the API will accept for that merchant only.
 */
@Testcontainers
class KeycloakAdminAdapterIT {

    private static final String REALM = "swiftdrop";
    private static final String MERCHANT_SERVICE_SECRET = "merchant-service-test-secret";
    private static final String SEED_PASSWORD = "Seed-Password-1";
    /** customer@swiftdrop.lk in realm-swiftdrop.json: an ordinary user who applies. */
    private static final String APPLICANT = "10000000-0000-4000-8000-000000000005";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Container
    static final GenericContainer<?> KEYCLOAK = new GenericContainer<>("quay.io/keycloak/keycloak:26.8.1")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of("../../infra/keycloak/realm-swiftdrop.json").toAbsolutePath()),
                    "/opt/keycloak/data/import/realm-swiftdrop.json")
            .withCommand("start-dev", "--import-realm")
            .withEnv(Map.of(
                    "KC_BOOTSTRAP_ADMIN_USERNAME", "admin",
                    "KC_BOOTSTRAP_ADMIN_PASSWORD", "admin",
                    "KC_WEB_CLIENT_SECRET", "web-test-secret",
                    "KC_USER_SERVICE_CLIENT_SECRET", "user-service-test-secret",
                    "KC_MERCHANT_SERVICE_CLIENT_SECRET", MERCHANT_SERVICE_SECRET,
                    "KC_DEMO_MERCHANT_CLIENT_SECRET", "demo-test-secret",
                    "KC_SEED_USER_PASSWORD", SEED_PASSWORD,
                    "GOOGLE_CLIENT_ID", "not-configured",
                    "GOOGLE_CLIENT_SECRET", "not-configured"))
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/" + REALM).forPort(8080).withStartupTimeout(Duration.ofMinutes(4)));

    private static RestClient http;
    private static KeycloakAdminAdapter adapter;

    @BeforeAll
    static void connect() {
        http = RestClient.builder().baseUrl("http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080)).build();
        adapter = new KeycloakAdminAdapter(http, REALM, "merchant-service-admin", MERCHANT_SERVICE_SECRET,
                Clock.systemUTC());
    }

    @Test
    void anApprovedMerchantsClientIssuesTokensForThatMerchantOnly() {
        UUID merchantId = UUID.randomUUID();

        String clientId = adapter.provisionClient(merchantId);

        assertThat(clientId).isEqualTo("merchant-" + merchantId);
        JsonNode claims = claims(token(form("grant_type", "client_credentials", "client_id", clientId,
                "client_secret", clientSecret(clientId))));
        assertThat(claims.get("merchant_id").asString()).isEqualTo(merchantId.toString());
        assertThat(audiences(claims)).contains("swiftdrop-api");
        // Not CUSTOMER either: the realm's default roles are taken off the machine client.
        assertThat(realmRoles(claims)).contains("MERCHANT_API").doesNotContain("MERCHANT", "ADMIN", "CUSTOMER");
    }

    @Test
    void provisioningTwiceLeavesOneClient() {
        UUID merchantId = UUID.randomUUID();

        adapter.provisionClient(merchantId);
        adapter.provisionClient(merchantId);

        JsonNode clients = JSON.readTree(admin().get()
                .uri("/admin/realms/{realm}/clients?clientId={id}", REALM, "merchant-" + merchantId)
                .retrieve().body(String.class));
        assertThat(clients.size()).isEqualTo(1);
    }

    @Test
    void theApplicantSignsInAsAMerchantOfTheirBusiness() {
        UUID merchantId = UUID.randomUUID();

        adapter.grantMerchantRole(APPLICANT, merchantId);
        adapter.grantMerchantRole(APPLICANT, merchantId);

        JsonNode user = JSON.readTree(admin().get().uri("/admin/realms/{realm}/users/{id}", REALM, APPLICANT)
                .retrieve().body(String.class));
        assertThat(user.get("attributes").get("merchant_id").get(0).asString()).isEqualTo(merchantId.toString());
        assertThat(user.get("email").asString()).isEqualTo("customer@swiftdrop.lk");

        // A fresh sign-in, as the applicant does after approval, carries the new role and id.
        JsonNode claims = claims(token(form("grant_type", "password", "client_id", "swiftdrop-test",
                "username", "customer@swiftdrop.lk", "password", SEED_PASSWORD)));
        assertThat(realmRoles(claims)).contains("MERCHANT");
        assertThat(claims.get("merchant_id").asString()).isEqualTo(merchantId.toString());
    }

    @Test
    void anApplicantWithoutAnAccountIsReportedAsMissing() {
        assertThatThrownBy(() -> adapter.grantMerchantRole(UUID.randomUUID().toString(), UUID.randomUUID()))
                .isInstanceOf(MerchantOwnerMissingException.class);
    }

    @Test
    void rotatingIssuesAWorkingSecretAndTheOldOneStopsWorkingAtOnce() {
        UUID merchantId = UUID.randomUUID();
        String clientId = adapter.provisionClient(merchantId);
        String oldSecret = clientSecret(clientId);
        assertThat(clientCredentialsStatus(clientId, oldSecret)).isEqualTo(200);

        String newSecret = adapter.regenerateClientSecret(clientId);

        assertThat(newSecret).isNotBlank().isNotEqualTo(oldSecret).hasSizeGreaterThanOrEqualTo(32);
        assertThat(clientSecret(clientId)).as("what Keycloak now holds").isEqualTo(newSecret);
        assertThat(clientCredentialsStatus(clientId, oldSecret)).as("the old secret").isEqualTo(401);
        JsonNode claims = claims(token(form("grant_type", "client_credentials", "client_id", clientId,
                "client_secret", newSecret)));
        assertThat(claims.get("merchant_id").asString()).isEqualTo(merchantId.toString());
        assertThat(realmRoles(claims)).contains("MERCHANT_API");
    }

    @Test
    void rotatingAgainReplacesTheSecretEachTime() {
        String clientId = adapter.provisionClient(UUID.randomUUID());

        String first = adapter.regenerateClientSecret(clientId);
        String second = adapter.regenerateClientSecret(clientId);

        assertThat(second).isNotEqualTo(first);
        assertThat(clientCredentialsStatus(clientId, first)).isEqualTo(401);
        assertThat(clientCredentialsStatus(clientId, second)).isEqualTo(200);
    }

    @Test
    void rotatingAClientKeycloakDoesNotHaveIs503() {
        assertThatThrownBy(() -> adapter.regenerateClientSecret("merchant-" + UUID.randomUUID()))
                .isInstanceOf(lk.swiftdrop.merchant.application.exception.IdentityProviderUnavailableException.class);
    }

    /** The HTTP status Keycloak answers a client credentials request with. */
    private static int clientCredentialsStatus(String clientId, String secret) {
        try {
            http.post().uri("/realms/{realm}/protocol/openid-connect/token", REALM)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form("grant_type", "client_credentials", "client_id", clientId, "client_secret", secret))
                    .retrieve().toBodilessEntity();
            return 200;
        } catch (HttpClientErrorException refused) {
            return refused.getStatusCode().value();
        }
    }

    // --- helpers: the bootstrap admin reads what the adapter did; no adapter code is reused here.

    private static RestClient admin() {
        String token = token("master", form("grant_type", "password", "client_id", "admin-cli",
                "username", "admin", "password", "admin"));
        return http.mutate().defaultHeaders(headers -> headers.setBearerAuth(token)).build();
    }

    private static String clientSecret(String clientId) {
        RestClient admin = admin();
        JsonNode client = JSON.readTree(admin.get()
                .uri("/admin/realms/{realm}/clients?clientId={id}", REALM, clientId)
                .retrieve().body(String.class)).get(0);
        JsonNode secret = JSON.readTree(admin.get()
                .uri("/admin/realms/{realm}/clients/{id}/client-secret", REALM, client.get("id").asString())
                .retrieve().body(String.class));
        return secret.get("value").asString();
    }

    private static String token(MultiValueMap<String, String> form) {
        return token(REALM, form);
    }

    private static String token(String realm, MultiValueMap<String, String> form) {
        JsonNode response = JSON.readTree(http.post().uri("/realms/{realm}/protocol/openid-connect/token", realm)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                .retrieve().body(String.class));
        return response.get("access_token").asString();
    }

    private static MultiValueMap<String, String> form(String... pairs) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            form.add(pairs[i], pairs[i + 1]);
        }
        return form;
    }

    private static JsonNode claims(String jwt) {
        return JSON.readTree(new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8));
    }

    private static List<String> realmRoles(JsonNode claims) {
        return claims.get("realm_access").get("roles").valueStream().map(JsonNode::asString).toList();
    }

    private static List<String> audiences(JsonNode claims) {
        JsonNode aud = claims.get("aud");
        return aud.isArray() ? aud.valueStream().map(JsonNode::asString).toList() : List.of(aud.asString());
    }
}
