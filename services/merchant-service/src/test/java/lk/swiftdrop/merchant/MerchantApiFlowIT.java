package lk.swiftdrop.merchant;

import lk.swiftdrop.common.security.KeycloakRealmRoleConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import lk.swiftdrop.merchant.application.exception.IdentityProviderUnavailableException;
import lk.swiftdrop.merchant.application.port.out.IdentityProvisioner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Onboarding through HTTP against real PostgreSQL, with signed tokens simulated: the whole path
 * from the request to the stored row and its outbox event, including who may do what.
 */
@Testcontainers
@SpringBootTest(properties = {
        // test-only key (32 bytes, base64); real deployments read PII_ENCRYPTION_KEY
        "swiftdrop.pii.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "swiftdrop.outbox.relay-enabled=false"})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class MerchantApiFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.15-alpine");
    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.2");

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String APPLICATION = """
            {"businessName":"Lanka Books","brNumber":"%s","contactEmail":"lankabooks@example.com",
             "contactPhone":"0771234567",
             "pickupAddress":{"line1":"12 Temple Road","city":"Kandy","district":"Kandy"}}
            """;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    /** Keycloak itself is exercised by KeycloakAdminAdapterIT; here it only has to succeed or fail on cue. */
    @MockitoBean
    private IdentityProvisioner identity;

    @BeforeEach
    void cleanUp() {
        jdbc.update("DELETE FROM outbox_event");
        jdbc.update("DELETE FROM api_credential");
        jdbc.update("DELETE FROM merchant");
        when(identity.provisionClient(any())).thenAnswer(call -> "merchant-" + call.getArgument(0));
    }

    @Test
    void anApprovalKeycloakCouldNotCompleteIs503AndCanSimplyBeRepeated() throws Exception {
        String id = id(apply("user-1", "PV12345", 201));
        when(identity.provisionClient(java.util.UUID.fromString(id)))
                .thenThrow(new IdentityProviderUnavailableException("The identity provider is not reachable right now"))
                .thenReturn("merchant-" + id);

        String failed = call(post("/api/v1/admin/merchants/" + id + "/approve").with(as("admin-1", "ADMIN")), 503);
        OpenApiContract.assertProblem(failed);
        assertThat(JSON.readTree(failed).get("code").asString()).isEqualTo("IDENTITY_PROVIDER_UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT status FROM merchant", String.class)).isEqualTo("PENDING");

        String approved = call(post("/api/v1/admin/merchants/" + id + "/approve").with(as("admin-1", "ADMIN")), 200);
        assertThat(JSON.readTree(approved).get("status").asString()).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM api_credential", Integer.class)).isEqualTo(1);
    }

    private static RequestPostProcessor as(String userId, String... roles) {
        return jwt().jwt(token -> token.subject(userId).claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(new KeycloakRealmRoleConverter());
    }

    private String apply(String userId, String brNumber, int expectedStatus) throws Exception {
        String body = mvc.perform(post("/api/v1/merchants/register").with(as(userId, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(APPLICATION.formatted(brNumber)))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        if (expectedStatus == 201) {
            OpenApiContract.assertMerchantSchema("Merchant", body);
        } else {
            OpenApiContract.assertProblem(body);
        }
        return body;
    }

    /** Performs the request, checks the status and returns the body. */
    private String call(org.springframework.test.web.servlet.RequestBuilder request, int expectedStatus)
            throws Exception {
        return mvc.perform(request).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }

    private static String id(String body) {
        return JSON.readTree(body).get("id").asString();
    }

    @Test
    void anApplicantIsRejectedCorrectsTheirDetailsAndIsApproved() throws Exception {
        String first = id(apply("user-1", "PV 12345", 201));

        String queue = call(get("/api/v1/admin/merchants").with(as("admin-1", "ADMIN")), 200);
        OpenApiContract.assertMerchantSchema("MerchantPage", queue);
        assertThat(JSON.readTree(queue).get("content").get(0).get("id").asString()).isEqualTo(first);
        assertThat(JSON.readTree(queue).get("page").get("totalElements").asInt()).isEqualTo(1);

        String rejected = call(post("/api/v1/admin/merchants/" + first + "/reject").with(as("admin-1", "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" BR certificate is unreadable \"}"), 200);
        OpenApiContract.assertMerchantSchema("Merchant", rejected);
        String mine = call(get("/api/v1/merchants/me").with(as("user-1", "CUSTOMER")), 200);
        OpenApiContract.assertMerchantSchema("Merchant", mine);
        assertThat(JSON.readTree(mine).get("status").asString()).isEqualTo("REJECTED");
        assertThat(JSON.readTree(mine).get("rejectionReason").asString()).isEqualTo("BR certificate is unreadable");

        String second = id(apply("user-1", "PV12345", 201));
        String approved = call(post("/api/v1/admin/merchants/" + second + "/approve").with(as("admin-1", "ADMIN")), 200);
        OpenApiContract.assertMerchantSchema("Merchant", approved);
        assertThat(JSON.readTree(approved).get("status").asString()).isEqualTo("APPROVED");

        String suspended = call(post("/api/v1/admin/merchants/" + second + "/suspend").with(as("admin-1", "ADMIN")), 200);
        OpenApiContract.assertMerchantSchema("Merchant", suspended);

        mvc.perform(get("/api/v1/merchants/me").with(as("user-1", "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(second))
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event", Integer.class)).isEqualTo(5);
    }

    @Test
    void errorAnswersFollowTheSharedProblemShape() throws Exception {
        String id = id(apply("user-1", "PV12345", 201));

        OpenApiContract.assertProblem(call(get("/api/v1/merchants/me").with(as("user-2", "CUSTOMER")), 404));
        OpenApiContract.assertProblem(call(post("/api/v1/admin/merchants/" + id + "/suspend")
                .with(as("admin-1", "ADMIN")), 422));
        OpenApiContract.assertProblem(call(post("/api/v1/admin/merchants/" + id + "/reject")
                .with(as("admin-1", "ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"), 400));
        OpenApiContract.assertProblem(call(get("/api/v1/admin/merchants?size=500").with(as("admin-1", "ADMIN")), 400));
    }

    @Test
    void aSecondBusinessWithTheSameRegistrationNumberGets409() throws Exception {
        apply("user-1", "PV12345", 201);

        String body = apply("user-2", "pv 12345", 409);

        assertThat(JSON.readTree(body).get("code").asString()).isEqualTo("MERCHANT_BR_NUMBER_TAKEN");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM merchant", Integer.class)).isEqualTo(1);
    }

    @Test
    void oneApplicantCannotSeeAnotherApplicantsApplication() throws Exception {
        apply("user-1", "PV12345", 201);

        mvc.perform(get("/api/v1/merchants/me").with(as("user-2", "CUSTOMER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void aNonAdminCannotApproveTheirOwnApplication() throws Exception {
        String id = id(apply("user-1", "PV12345", 201));

        mvc.perform(post("/api/v1/admin/merchants/" + id + "/approve").with(as("user-1", "CUSTOMER")))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("SELECT status FROM merchant", String.class)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event", Integer.class)).isEqualTo(1);
    }

    @Test
    void suspendingAPendingApplicationIs422AndChangesNothing() throws Exception {
        String id = id(apply("user-1", "PV12345", 201));

        mvc.perform(post("/api/v1/admin/merchants/" + id + "/suspend").with(as("admin-1", "ADMIN")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("MERCHANT_INVALID_TRANSITION"));

        assertThat(jdbc.queryForObject("SELECT status FROM merchant", String.class)).isEqualTo("PENDING");
    }

    // --- credential rotation

    private static final String ROTATE = "/api/v1/merchants/me/credentials/rotate";
    private static final String NEW_SECRET = "rotated-secret-5d1c9a7e3b";

    /** The owner as they sign in after approval: MERCHANT role and the merchant_id Keycloak now adds. */
    private static RequestPostProcessor merchant(String userId, String merchantId, String... roles) {
        return jwt().jwt(token -> token.subject(userId).claim("merchant_id", merchantId)
                        .claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(new KeycloakRealmRoleConverter());
    }

    private String approvedMerchant(String userId, String brNumber) throws Exception {
        String id = id(apply(userId, brNumber, 201));
        call(post("/api/v1/admin/merchants/" + id + "/approve").with(as("admin-1", "ADMIN")), 200);
        return id;
    }

    private java.sql.Timestamp rotatedAt() {
        return jdbc.queryForObject("SELECT rotated_at FROM api_credential", java.sql.Timestamp.class);
    }

    @Test
    void theOwnerRotatesAndTheSecretIsShownOnceButNeverStoredOrLogged(CapturedOutput output) throws Exception {
        String id = approvedMerchant("user-1", "PV12345");
        when(identity.regenerateClientSecret("merchant-" + id)).thenReturn(NEW_SECRET);
        assertThat(rotatedAt()).isNull();

        var response = mvc.perform(post(ROTATE).with(merchant("user-1", id, "MERCHANT")))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        String body = response.getContentAsString();
        OpenApiContract.assertMerchantSchema("ApiCredentialSecret", body);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(JSON.readTree(body).get("clientId").asString()).isEqualTo("merchant-" + id);
        assertThat(JSON.readTree(body).get("clientSecret").asString()).isEqualTo(NEW_SECRET);
        assertThat(rotatedAt()).isNotNull();

        // Nothing in the database holds the secret: not the credential row, not any event.
        String everyStoredValue = String.join("|", jdbc.queryForList(
                "SELECT concat_ws('|', a::text, m::text, (SELECT string_agg(o::text, '|') FROM outbox_event o)) "
                        + "FROM api_credential a, merchant m", String.class));
        assertThat(everyStoredValue).contains("merchant-" + id).doesNotContain(NEW_SECRET);
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'api_credential'", String.class)).noneMatch(c -> c.contains("secret"));
        assertThat(output.getAll()).doesNotContain(NEW_SECRET);
    }

    @Test
    void whenKeycloakFailsTheRotationIsRolledBackAndCanBeRetried() throws Exception {
        String id = approvedMerchant("user-1", "PV12345");
        when(identity.regenerateClientSecret("merchant-" + id))
                .thenThrow(new IdentityProviderUnavailableException("The identity provider is not reachable right now"))
                .thenReturn(NEW_SECRET);

        String failed = call(post(ROTATE).with(merchant("user-1", id, "MERCHANT")), 503);
        OpenApiContract.assertProblem(failed);
        assertThat(JSON.readTree(failed).get("code").asString()).isEqualTo("IDENTITY_PROVIDER_UNAVAILABLE");
        assertThat(rotatedAt()).as("the write before Keycloak was rolled back").isNull();

        String retried = call(post(ROTATE).with(merchant("user-1", id, "MERCHANT")), 200);
        assertThat(JSON.readTree(retried).get("clientSecret").asString()).isEqualTo(NEW_SECRET);
        assertThat(rotatedAt()).isNotNull();
    }

    @Test
    void nobodyCanRotateAnotherMerchantsCredentials() throws Exception {
        String id = approvedMerchant("user-1", "PV12345");

        // A token claiming user-1's merchant but belonging to someone else is answered as "no merchant".
        OpenApiContract.assertProblem(call(post(ROTATE).with(merchant("user-2", id, "MERCHANT")), 404));
        call(post(ROTATE).with(merchant("service-account-merchant-x", id, "MERCHANT_API")), 403);
        call(post(ROTATE).with(as("user-1", "CUSTOMER")), 403);

        assertThat(rotatedAt()).isNull();
        org.mockito.Mockito.verify(identity, org.mockito.Mockito.never()).regenerateClientSecret(any());
    }

    @Test
    void aSuspendedMerchantCannotRotate() throws Exception {
        String id = approvedMerchant("user-1", "PV12345");
        call(post("/api/v1/admin/merchants/" + id + "/suspend").with(as("admin-1", "ADMIN")), 200);

        String refused = call(post(ROTATE).with(merchant("user-1", id, "MERCHANT")), 422);

        OpenApiContract.assertProblem(refused);
        assertThat(JSON.readTree(refused).get("code").asString()).isEqualTo("MERCHANT_NOT_APPROVED");
        assertThat(rotatedAt()).isNull();
        org.mockito.Mockito.verify(identity, org.mockito.Mockito.never()).regenerateClientSecret(any());
    }
}
