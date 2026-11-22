package lk.swiftdrop.merchant.infrastructure.keycloak;

import lk.swiftdrop.merchant.application.exception.IdentityProviderUnavailableException;
import lk.swiftdrop.merchant.application.exception.MerchantOwnerMissingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KeycloakAdminAdapterTest {

    private static final UUID MERCHANT = UUID.fromString("7e726c28-4a72-4fea-9ec1-c56b08262ddc");
    private static final String CLIENT_ID = "merchant-" + MERCHANT;
    private static final String BASE = "http://kc";
    private static final String TOKEN_URL = BASE + "/realms/swiftdrop/protocol/openid-connect/token";
    private static final String ADMIN = BASE + "/admin/realms/swiftdrop";
    private static final String FIND_CLIENT = ADMIN + "/clients?clientId=" + CLIENT_ID;
    private static final String FOUND_CLIENT = "[{\"id\":\"client-uuid\",\"clientId\":\"" + CLIENT_ID + "\"}]";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-19T08:00:00Z"), ZoneOffset.UTC);

    private MockRestServiceServer server;
    private KeycloakAdminAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new KeycloakAdminAdapter(builder.build(), "swiftdrop", "merchant-service-admin", "s3cret", CLOCK);
    }

    private void expectToken(String token) {
        server.expect(once(), requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(Map.of("grant_type", "client_credentials",
                        "client_id", "merchant-service-admin", "client_secret", "s3cret")))
                .andRespond(withSuccess("{\"access_token\":\"" + token + "\",\"expires_in\":300}",
                        MediaType.APPLICATION_JSON));
    }

    private void expectGet(String url, String json) {
        server.expect(once(), requestTo(url))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-1"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private void expectRoleMapping(String userId, String role, String roleId) {
        expectGet(ADMIN + "/roles/" + role, "{\"id\":\"" + roleId + "\",\"name\":\"" + role + "\"}");
        server.expect(once(), requestTo(ADMIN + "/users/" + userId + "/role-mappings/realm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$[0].id").value(roleId))
                .andExpect(jsonPath("$[0].name").value(role))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
    }

    /** The machine client gets MERCHANT_API and loses the default roles that would make it a CUSTOMER. */
    private void expectServiceAccountWithMerchantApi() {
        expectGet(ADMIN + "/clients/client-uuid/service-account-user", "{\"id\":\"sa-1\"}");
        expectRoleMapping("sa-1", "MERCHANT_API", "role-api");
        expectGet(ADMIN + "/roles/default-roles-swiftdrop", "{\"id\":\"role-default\",\"name\":\"default-roles-swiftdrop\"}");
        server.expect(once(), requestTo(ADMIN + "/users/sa-1/role-mappings/realm"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(jsonPath("$[0].id").value("role-default"))
                .andExpect(jsonPath("$[0].name").value("default-roles-swiftdrop"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
    }

    @Test
    void aNewMerchantGetsAMachineClientWhoseTokensNameIt() {
        expectToken("token-1");
        expectGet(FIND_CLIENT, "[]");
        server.expect(once(), requestTo(ADMIN + "/clients"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.clientId").value(CLIENT_ID))
                .andExpect(jsonPath("$.publicClient").value(false))
                .andExpect(jsonPath("$.serviceAccountsEnabled").value(true))
                .andExpect(jsonPath("$.standardFlowEnabled").value(false))
                .andExpect(jsonPath("$.directAccessGrantsEnabled").value(false))
                .andExpect(jsonPath("$.protocolMappers[0].config['included.custom.audience']").value("swiftdrop-api"))
                .andExpect(jsonPath("$.protocolMappers[1].protocolMapper").value("oidc-hardcoded-claim-mapper"))
                .andExpect(jsonPath("$.protocolMappers[1].config['claim.name']").value("merchant_id"))
                .andExpect(jsonPath("$.protocolMappers[1].config['claim.value']").value(MERCHANT.toString()))
                .andExpect(jsonPath("$.secret").doesNotExist())
                .andRespond(withStatus(HttpStatus.CREATED));
        expectGet(FIND_CLIENT, FOUND_CLIENT);
        expectServiceAccountWithMerchantApi();

        assertThat(adapter.provisionClient(MERCHANT)).isEqualTo(CLIENT_ID);
        server.verify();
    }

    @Test
    void anExistingClientIsReusedRatherThanCreatedAgain() {
        expectToken("token-1");
        expectGet(FIND_CLIENT, FOUND_CLIENT);
        expectServiceAccountWithMerchantApi();

        assertThat(adapter.provisionClient(MERCHANT)).isEqualTo(CLIENT_ID);
        server.verify();
    }

    @Test
    void aClientCreatedByAConcurrentAttemptIsReused() {
        expectToken("token-1");
        expectGet(FIND_CLIENT, "[]");
        server.expect(once(), requestTo(ADMIN + "/clients")).andRespond(withStatus(HttpStatus.CONFLICT));
        expectGet(FIND_CLIENT, FOUND_CLIENT);
        expectServiceAccountWithMerchantApi();

        assertThat(adapter.provisionClient(MERCHANT)).isEqualTo(CLIENT_ID);
        server.verify();
    }

    @Test
    void theOwnerGetsTheMerchantIdAndRoleWithoutLosingOtherAttributes() {
        expectToken("token-1");
        expectGet(ADMIN + "/users/owner-1", """
                {"id":"owner-1","username":"lanka","email":"owner@example.com","firstName":"Nimal","lastName":"Perera",
                 "attributes":{"locale":["en"]}}""");
        server.expect(once(), requestTo(ADMIN + "/users/owner-1"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.email").value("owner@example.com"))
                .andExpect(jsonPath("$.attributes.locale[0]").value("en"))
                .andExpect(jsonPath("$.attributes.merchant_id[0]").value(MERCHANT.toString()))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
        expectRoleMapping("owner-1", "MERCHANT", "role-merchant");

        adapter.grantMerchantRole("owner-1", MERCHANT);
        server.verify();
    }

    @Test
    void anOwnerWhoseAccountIsGoneCannotBeMadeAMerchant() {
        expectToken("token-1");
        server.expect(once(), requestTo(ADMIN + "/users/owner-1")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> adapter.grantMerchantRole("owner-1", MERCHANT))
                .isInstanceOf(MerchantOwnerMissingException.class);
    }

    @Test
    void theServiceAccountTokenIsReusedUntilItNearlyExpires() {
        expectToken("token-1");
        expectGet(FIND_CLIENT, FOUND_CLIENT);
        expectServiceAccountWithMerchantApi();
        expectGet(FIND_CLIENT, FOUND_CLIENT);
        expectServiceAccountWithMerchantApi();

        adapter.provisionClient(MERCHANT);
        adapter.provisionClient(MERCHANT);
        server.verify();
    }

    @Test
    void aTokenDroppedByKeycloakIsReplacedAndTheOperationRetriedOnce() {
        server.expect(once(), requestTo(TOKEN_URL)).andRespond(
                withSuccess("{\"access_token\":\"stale\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(FIND_CLIENT)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        expectToken("token-1");
        expectGet(FIND_CLIENT, FOUND_CLIENT);
        expectServiceAccountWithMerchantApi();

        assertThat(adapter.provisionClient(MERCHANT)).isEqualTo(CLIENT_ID);
        server.verify();
    }

    @Test
    void aServiceAccountWithoutTheNeededRolesIs503() {
        expectToken("token-1");
        server.expect(once(), requestTo(FIND_CLIENT)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> adapter.provisionClient(MERCHANT))
                .isInstanceOfSatisfying(IdentityProviderUnavailableException.class,
                        e -> assertThat(e.status().value()).isEqualTo(503));
    }

    @Test
    void anUnreachableKeycloakIs503() {
        server.expect(once(), requestTo(TOKEN_URL)).andRespond(withException(new IOException("Connection refused")));

        assertThatThrownBy(() -> adapter.provisionClient(MERCHANT)).isInstanceOf(IdentityProviderUnavailableException.class);
    }

    @Test
    void rejectedServiceAccountCredentialsAre503() {
        server.expect(once(), requestTo(TOKEN_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> adapter.provisionClient(MERCHANT))
                .isInstanceOf(IdentityProviderUnavailableException.class)
                .hasMessageNotContaining("s3cret");
    }

    @Test
    void withoutAConfiguredSecretNothingIsSentToKeycloak() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer silent = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminAdapter unconfigured = new KeycloakAdminAdapter(builder.build(), "swiftdrop",
                "merchant-service-admin", " ", CLOCK);

        assertThatThrownBy(() -> unconfigured.provisionClient(MERCHANT))
                .isInstanceOf(IdentityProviderUnavailableException.class)
                .hasMessageContaining("not configured");
        silent.verify();
    }

    @Test
    void theClientIsAConfidentialMachineClientOnly() {
        Map<String, Object> client = KeycloakAdminAdapter.clientRepresentation(CLIENT_ID, MERCHANT);

        assertThat(client).containsEntry("publicClient", false)
                .containsEntry("clientAuthenticatorType", "client-secret")
                .containsEntry("serviceAccountsEnabled", true)
                .containsEntry("standardFlowEnabled", false)
                .containsEntry("implicitFlowEnabled", false)
                .containsEntry("directAccessGrantsEnabled", false)
                .doesNotContainKey("secret");
        assertThat((List<?>) client.get("protocolMappers")).hasSize(2);
    }

    // --- secret rotation

    private static final String CLIENT_SECRET = ADMIN + "/clients/client-uuid/client-secret";

    @Test
    void keycloakRegeneratesTheSecretAndTheNewValueIsReturned() {
        expectToken("token-1");
        expectGet(FIND_CLIENT, FOUND_CLIENT);
        server.expect(once(), requestTo(CLIENT_SECRET))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-1"))
                .andRespond(withSuccess("{\"type\":\"secret\",\"value\":\"fresh-secret\"}", MediaType.APPLICATION_JSON));

        assertThat(adapter.regenerateClientSecret(CLIENT_ID)).isEqualTo("fresh-secret");
        server.verify();
    }

    @Test
    void aClientMissingFromKeycloakIs503AndNothingIsRegenerated() {
        expectToken("token-1");
        expectGet(FIND_CLIENT, "[]");

        assertThatThrownBy(() -> adapter.regenerateClientSecret(CLIENT_ID))
                .isInstanceOfSatisfying(IdentityProviderUnavailableException.class,
                        e -> assertThat(e.status().value()).isEqualTo(503));
        server.verify();
    }

    @Test
    void anAnswerWithoutASecretIs503() {
        expectToken("token-1");
        expectGet(FIND_CLIENT, FOUND_CLIENT);
        server.expect(once(), requestTo(CLIENT_SECRET))
                .andRespond(withSuccess("{\"type\":\"secret\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.regenerateClientSecret(CLIENT_ID))
                .isInstanceOf(IdentityProviderUnavailableException.class);
    }

    @Test
    void aKeycloakErrorWhileRegeneratingIs503() {
        expectToken("token-1");
        expectGet(FIND_CLIENT, FOUND_CLIENT);
        server.expect(once(), requestTo(CLIENT_SECRET)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> adapter.regenerateClientSecret(CLIENT_ID))
                .isInstanceOf(IdentityProviderUnavailableException.class);
    }
}
