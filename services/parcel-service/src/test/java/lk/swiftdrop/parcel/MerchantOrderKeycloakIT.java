package lk.swiftdrop.parcel;

import lk.swiftdrop.events.EventEnvelope;
import lk.swiftdrop.events.Topics;
import lk.swiftdrop.events.common.Types.Actor;
import lk.swiftdrop.events.common.Types.ActorRole;
import lk.swiftdrop.events.merchant.MerchantLifecycleChanged;
import lk.swiftdrop.events.merchant.MerchantLifecycleChanged.MerchantStatus;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The merchant order API end to end with <b>real Keycloak tokens</b>: nothing about authentication
 * is simulated. Keycloak runs in a container with the project's own realm file; each merchant gets
 * a confidential client provisioned through Keycloak's Admin REST API, signed in as the
 * merchant-service-admin service account, with the same shape merchant-service gives it
 * (KeycloakAdminAdapter: client credentials only, swiftdrop-api audience, hard-coded merchant_id,
 * MERCHANT_API and no default roles). Each test then asks Keycloak for an access token with the
 * client-credentials grant and calls parcel-service over HTTP, so the production security
 * configuration checks the signature (keys fetched from Keycloak), issuer, audience, expiry and
 * roles exactly as it does in a deployment.
 *
 * <p>A merchant's standing reaches parcel-service the way it does in production too: as a
 * MerchantLifecycleChanged record on Kafka, consumed by the real listener. Only pricing-service is
 * a stub. Secrets here are test-only values for this throwaway container; tokens and secrets are
 * never printed, and failure messages show only status codes and response bodies.
 *
 * <p><b>Not covered here.</b> merchant-service does not run in this test. The merchant clients are
 * created by this test, mirroring KeycloakAdminAdapter's client representation rather than calling
 * it (it lives in another module), so the two must be kept in step; the adapter itself is tested
 * against real Keycloak in merchant-service's KeycloakAdminAdapterIT. Likewise the lifecycle
 * records are written by this test in the outbox relay's format: merchant-service's outbox and
 * relay are not exercised. The API gateway is not in the path either (requests go straight to
 * parcel-service), and expired or foreign-realm tokens are not tried.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // test-only key (32 bytes, base64); real deployments read PII_ENCRYPTION_KEY
        "swiftdrop.pii.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "swiftdrop.outbox.relay-enabled=false"})
class MerchantOrderKeycloakIT {

    private static final String REALM = "swiftdrop";
    /** Test-only secrets for the realm's placeholders; nothing here is used outside this container. */
    private static final String MERCHANT_SERVICE_SECRET = "merchant-service-it-secret";
    private static final String SEED_PASSWORD = "Seed-Password-IT-1";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Duration WAIT = Duration.ofSeconds(30);
    private static final String ORDERS = "/api/v1/merchant/orders";
    private static final String ORDER = """
            {"merchantOrderRef":"%s",
             "pickup":{"line1":"12 Galle Road","city":"Colombo 03","district":"Colombo"},
             "dropoff":{"line1":"45 Peradeniya Road","city":"Kandy","district":"Kandy"},
             "recipient":{"name":"Saman Perera","phone":"0712345678"},
             "weightKg":2,"sizeClass":"S"}
            """;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.15-alpine");
    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.2");
    /** The same image and realm file docker-compose runs. */
    @Container
    static final GenericContainer<?> KEYCLOAK = new GenericContainer<>("quay.io/keycloak/keycloak:26.8.1")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of("../../infra/keycloak/realm-swiftdrop.json").toAbsolutePath()),
                    "/opt/keycloak/data/import/realm-swiftdrop.json")
            .withCommand("start-dev", "--import-realm")
            .withEnv(Map.of(
                    "KC_BOOTSTRAP_ADMIN_USERNAME", "admin",
                    "KC_BOOTSTRAP_ADMIN_PASSWORD", "admin-it",
                    "KC_WEB_CLIENT_SECRET", "web-it-secret",
                    "KC_USER_SERVICE_CLIENT_SECRET", "user-service-it-secret",
                    "KC_MERCHANT_SERVICE_CLIENT_SECRET", MERCHANT_SERVICE_SECRET,
                    "KC_DEMO_MERCHANT_CLIENT_SECRET", "demo-it-secret",
                    "KC_SEED_USER_PASSWORD", SEED_PASSWORD,
                    "GOOGLE_CLIENT_ID", "not-configured",
                    "GOOGLE_CLIENT_SECRET", "not-configured"))
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/" + REALM).forPort(8080).withStartupTimeout(Duration.ofMinutes(4)));

    private static final PricingStub PRICING = new PricingStub();
    private static RestClient keycloak;

    @LocalServerPort
    private int port;
    @Autowired
    private KafkaTemplate<String, String> kafka;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;

    private RestClient api;

    private static String keycloakUrl() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
    }

    /**
     * Only the addresses change: the issuer and key set point at the test Keycloak, and the
     * required audience (swiftdrop-api) and everything else stay as application.yml sets them.
     */
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("swiftdrop.pricing.base-url", PRICING::baseUrl);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> keycloakUrl() + "/realms/" + REALM);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> keycloakUrl() + "/realms/" + REALM + "/protocol/openid-connect/certs");
    }

    @BeforeAll
    static void connect() {
        keycloak = RestClient.builder().baseUrl(keycloakUrl()).build();
    }

    @AfterAll
    static void stopPricing() {
        PRICING.close();
    }

    @BeforeEach
    void setUp() {
        PRICING.reset();
        jdbc.update("DELETE FROM outbox_event");
        jdbc.update("DELETE FROM idempotency_record");
        jdbc.update("DELETE FROM parcel");
        api = RestClient.builder().baseUrl("http://localhost:" + port)
                // Every status comes back as a response; the tests check it themselves.
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    // ---------------------------------------------------------------- the tests

    @Test
    void anApprovedMerchantCreatesAnOrderWithARealClientCredentialsTokenAndARetryReplaysIt() {
        Merchant merchant = provision(true);
        approve(merchant.id());
        String token = clientCredentialsToken(merchant);

        JsonNode claims = claims(token);
        assertThat(claims.get("merchant_id").asString()).isEqualTo(merchant.id().toString());
        assertThat(audiences(claims)).contains("swiftdrop-api");
        assertThat(realmRoles(claims)).contains("MERCHANT_API").doesNotContain("CUSTOMER", "MERCHANT", "ADMIN");

        ResponseEntity<String> created = order(token, "it-key-approved-1", ORDER.formatted("KC-1001"));
        assertThat(created.getStatusCode().value()).as(created.getBody()).isEqualTo(201);
        OpenApiContract.assertParcelSchema("MerchantOrder", created.getBody());
        JsonNode parcel = read(created);
        assertThat(parcel.get("trackingNumber").asString()).startsWith("SD");
        assertThat(parcel.get("merchantOrderRef").asString()).isEqualTo("KC-1001");
        assertThat(jdbc.queryForObject("SELECT merchant_id FROM parcel WHERE id = ?::uuid", String.class,
                parcel.get("id").asString())).isEqualTo(merchant.id().toString());

        // Retry with the same key and body: the same parcel, marked as a replay, and nothing new stored.
        ResponseEntity<String> replayed = order(token, "it-key-approved-1", ORDER.formatted("KC-1001"));
        assertThat(replayed.getStatusCode().value()).as(replayed.getBody()).isEqualTo(200);
        assertThat(replayed.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(read(replayed).get("id").asString()).isEqualTo(parcel.get("id").asString());
        assertThat(count("parcel")).isEqualTo(1);

        // The same key for a different order is refused.
        ResponseEntity<String> reused = order(token, "it-key-approved-1", ORDER.formatted("KC-9999"));
        assertProblem(reused, 409, "IDEMPOTENCY_KEY_REUSED");
        // And a request without a key never reaches the order logic.
        ResponseEntity<String> keyless = api.post().uri(ORDERS).headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON).body(ORDER.formatted("KC-1002")).retrieve().toEntity(String.class);
        assertProblem(keyless, 400, "IDEMPOTENCY_KEY_INVALID");
        assertThat(count("parcel")).isEqualTo(1);
    }

    @Test
    void aMerchantParcelServiceHasNeverSeenApprovedIsRefused() {
        Merchant pending = provision(true);
        String token = clientCredentialsToken(pending);

        assertProblem(order(token, "it-key-pending-1", ORDER.formatted("P-1")), 403, "MERCHANT_NOT_APPROVED");
        assertThat(count("parcel")).isZero();
    }

    @Test
    void aSuspensionFromKafkaClosesTheApiToAMerchantThatCouldOrderBefore() throws Exception {
        Merchant merchant = provision(true);
        Instant approvedAt = approve(merchant.id());
        String token = clientCredentialsToken(merchant);
        assertThat(order(token, "it-key-susp-1", ORDER.formatted("S-1")).getStatusCode().value()).isEqualTo(201);

        publish(merchant.id(), MerchantStatus.SUSPENDED, approvedAt.plusSeconds(60));
        await().atMost(WAIT).until(() -> "SUSPENDED".equals(statusOf(merchant.id())));

        // The token itself is still valid: it is the merchant's standing that refuses the order.
        assertProblem(order(token, "it-key-susp-2", ORDER.formatted("S-2")), 403, "MERCHANT_NOT_APPROVED");
        assertThat(count("parcel")).isEqualTo(1);
        // A suspended merchant can still read its history.
        ResponseEntity<String> history = list(token, Map.of());
        assertThat(history.getStatusCode().value()).isEqualTo(200);
        assertThat(read(history).get("page").get("totalElements").asInt()).isEqualTo(1);
    }

    @Test
    void oneMerchantsRealTokenNeverReachesAnotherMerchantsOrders() {
        Merchant a = provision(true);
        Merchant b = provision(true);
        approve(a.id());
        approve(b.id());
        String tokenA = clientCredentialsToken(a);
        String tokenB = clientCredentialsToken(b);

        String parcelA = read(order(tokenA, "it-key-shared-1", ORDER.formatted("SHOP-A-1"))).get("id").asString();
        // The same Idempotency-Key is independent per merchant: B gets its own new parcel.
        ResponseEntity<String> createdB = order(tokenB, "it-key-shared-1", ORDER.formatted("SHOP-B-1"));
        assertThat(createdB.getStatusCode().value()).as(createdB.getBody()).isEqualTo(201);
        assertThat(read(createdB).get("id").asString()).isNotEqualTo(parcelA);

        // B cannot open A's parcel, and A can.
        assertThat(get(tokenB, "/api/v1/parcels/" + parcelA).getStatusCode().value()).isEqualTo(404);
        assertThat(get(tokenA, "/api/v1/parcels/" + parcelA).getStatusCode().value()).isEqualTo(200);

        // B's list holds only B's order, even when it names A or searches for A's reference.
        ResponseEntity<String> listB = list(tokenB, Map.of("merchantId", a.id().toString()));
        OpenApiContract.assertParcelSchema("MerchantOrderPage", listB.getBody());
        assertThat(refs(listB)).containsExactly("SHOP-B-1");
        assertThat(refs(list(tokenB, Map.of("orderRef", "SHOP-A")))).isEmpty();
        assertThat(refs(list(tokenA, Map.of()))).containsExactly("SHOP-A-1");
    }

    @Test
    void theApiRefusesTokensItWasNotMeantToAccept() {
        Merchant merchant = provision(true);
        approve(merchant.id());

        // No token at all.
        assertThat(api.post().uri(ORDERS).header("Idempotency-Key", "it-key-anon-1")
                .contentType(MediaType.APPLICATION_JSON).body(ORDER.formatted("X-1")).retrieve()
                .toEntity(String.class).getStatusCode().value()).isEqualTo(401);

        // A genuine Keycloak token for the same merchant, but without the swiftdrop-api audience.
        Merchant withoutAudience = provision(false, merchant.id());
        assertThat(order(clientCredentialsToken(withoutAudience), "it-key-aud-1", ORDER.formatted("X-2"))
                .getStatusCode().value()).isEqualTo(401);

        // A signed-in customer (password grant on the realm's test client) is not a merchant system.
        String customer = passwordToken("customer@swiftdrop.lk");
        assertThat(realmRoles(claims(customer))).contains("CUSTOMER").doesNotContain("MERCHANT_API");
        assertThat(order(customer, "it-key-cust-1", ORDER.formatted("X-3")).getStatusCode().value()).isEqualTo(403);

        // A merchant's machine token is not a customer: customer-only endpoints refuse it.
        assertThat(get(clientCredentialsToken(merchant), "/api/v1/parcels/mine").getStatusCode().value())
                .isEqualTo(403);
        assertThat(count("parcel")).isZero();
    }

    // ---------------------------------------------------------------- Keycloak

    private record Merchant(UUID id, String clientId, String clientSecret) {
    }

    private static Merchant provision(boolean withApiAudience) {
        return provision(withApiAudience, UUID.randomUUID());
    }

    /**
     * Creates a merchant API client as merchant-service does on approval, through the Admin REST API
     * as merchant-service-admin. {@code withApiAudience=false} leaves out the swiftdrop-api audience,
     * to show the API refuses such a token.
     */
    private static Merchant provision(boolean withApiAudience, UUID merchantId) {
        String admin = adminToken();
        String clientId = "merchant-" + merchantId + (withApiAudience ? "" : "-noaud");
        List<Map<String, Object>> mappers = new ArrayList<>();
        if (withApiAudience) {
            mappers.add(Map.of("name", "audience-swiftdrop-api", "protocol", "openid-connect",
                    "protocolMapper", "oidc-audience-mapper",
                    "config", Map.of("included.custom.audience", "swiftdrop-api", "access.token.claim", "true",
                            "id.token.claim", "false", "introspection.token.claim", "true")));
        }
        mappers.add(Map.of("name", "merchant-id", "protocol", "openid-connect",
                "protocolMapper", "oidc-hardcoded-claim-mapper",
                "config", Map.of("claim.name", "merchant_id", "claim.value", merchantId.toString(),
                        "jsonType.label", "String", "access.token.claim", "true", "id.token.claim", "false",
                        "userinfo.token.claim", "false", "introspection.token.claim", "true")));
        Map<String, Object> client = Map.of("clientId", clientId, "enabled", true, "publicClient", false,
                "clientAuthenticatorType", "client-secret", "standardFlowEnabled", false,
                "implicitFlowEnabled", false, "directAccessGrantsEnabled", false, "serviceAccountsEnabled", true,
                "protocolMappers", mappers);

        keycloak.post().uri("/admin/realms/{realm}/clients", REALM).headers(h -> h.setBearerAuth(admin))
                .contentType(MediaType.APPLICATION_JSON).body(client).retrieve().toBodilessEntity();
        String clientUuid = JSON.readTree(keycloak.get().uri("/admin/realms/{realm}/clients?clientId={id}", REALM, clientId)
                .headers(h -> h.setBearerAuth(admin)).retrieve().body(String.class)).get(0).get("id").asString();
        String serviceAccount = adminGet(admin, "/admin/realms/{realm}/clients/{id}/service-account-user", clientUuid)
                .get("id").asString();
        changeRealmRole(admin, serviceAccount, "MERCHANT_API", HttpMethod.POST);
        changeRealmRole(admin, serviceAccount, "default-roles-" + REALM, HttpMethod.DELETE);
        String secret = adminGet(admin, "/admin/realms/{realm}/clients/{id}/client-secret", clientUuid)
                .get("value").asString();
        return new Merchant(merchantId, clientId, secret);
    }

    private static void changeRealmRole(String admin, String userId, String roleName, HttpMethod method) {
        JsonNode role = adminGet(admin, "/admin/realms/{realm}/roles/{name}", roleName);
        keycloak.method(method).uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", REALM, userId)
                .headers(h -> h.setBearerAuth(admin)).contentType(MediaType.APPLICATION_JSON)
                .body(List.of(Map.of("id", role.get("id").asString(), "name", roleName)))
                .retrieve().toBodilessEntity();
    }

    private static JsonNode adminGet(String admin, String path, String id) {
        return JSON.readTree(keycloak.get().uri(path, REALM, id).headers(h -> h.setBearerAuth(admin))
                .retrieve().body(String.class));
    }

    /** merchant-service-admin's own client-credentials token, as merchant-service signs in. */
    private static String adminToken() {
        return token(form("grant_type", "client_credentials", "client_id", "merchant-service-admin",
                "client_secret", MERCHANT_SERVICE_SECRET));
    }

    /** What a merchant's own system does: the client-credentials grant with its id and secret. */
    private static String clientCredentialsToken(Merchant merchant) {
        return token(form("grant_type", "client_credentials", "client_id", merchant.clientId(),
                "client_secret", merchant.clientSecret()));
    }

    private static String passwordToken(String username) {
        return token(form("grant_type", "password", "client_id", "swiftdrop-test", "username", username,
                "password", SEED_PASSWORD));
    }

    private static String token(MultiValueMap<String, String> form) {
        JsonNode response = JSON.readTree(keycloak.post().uri("/realms/{realm}/protocol/openid-connect/token", REALM)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(String.class));
        return response.get("access_token").asString();
    }

    private static MultiValueMap<String, String> form(String... pairs) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            form.add(pairs[i], pairs[i + 1]);
        }
        return form;
    }

    /** Reads the token's claims to check them; the token itself is never printed. */
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

    // ---------------------------------------------------------------- Kafka (merchant standing)

    /** Approves the merchant the production way: a lifecycle record on Kafka, applied by the listener. */
    private Instant approve(UUID merchantId) {
        Instant changedAt = Instant.now().minusSeconds(60);
        try {
            publish(merchantId, MerchantStatus.APPROVED, changedAt);
        } catch (Exception e) {
            throw new IllegalStateException("Could not publish the approval", e);
        }
        await().atMost(WAIT).until(() -> "APPROVED".equals(statusOf(merchantId)));
        return changedAt;
    }

    /** As merchant-service's outbox relay sends it: the envelope as JSON, keyed by merchant id. */
    private void publish(UUID merchantId, MerchantStatus status, Instant changedAt) throws Exception {
        MerchantLifecycleChanged payload = new MerchantLifecycleChanged(merchantId, "IT Shop", "owner-it",
                "shop@example.com", null, status, null, new Actor("admin-it", ActorRole.ADMIN), changedAt);
        EventEnvelope<MerchantLifecycleChanged> envelope = EventEnvelope.wrap(payload, "merchant-service", "corr-it");
        ProducerRecord<String, String> record = new ProducerRecord<>(Topics.MERCHANT_LIFECYCLE,
                merchantId.toString(), json.writeValueAsString(envelope));
        record.headers().add(Topics.HEADER_EVENT_ID, envelope.eventId().toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add(Topics.HEADER_EVENT_TYPE, MerchantLifecycleChanged.TYPE.getBytes(StandardCharsets.UTF_8));
        record.headers().add(Topics.HEADER_CORRELATION_ID, "corr-it".getBytes(StandardCharsets.UTF_8));
        kafka.send(record).get();
    }

    private String statusOf(UUID merchantId) {
        List<String> found = jdbc.queryForList("SELECT status FROM merchant_status WHERE merchant_id = ?",
                String.class, merchantId.toString());
        return found.isEmpty() ? null : found.get(0);
    }

    // ---------------------------------------------------------------- parcel-service over HTTP

    private ResponseEntity<String> order(String token, String key, String body) {
        return api.post().uri(ORDERS).headers(h -> {
                    h.setBearerAuth(token);
                    h.set("Idempotency-Key", key);
                }).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toEntity(String.class);
    }

    private ResponseEntity<String> list(String token, Map<String, String> query) {
        return api.get().uri(builder -> {
                    builder.path(ORDERS);
                    query.forEach(builder::queryParam);
                    return builder.build();
                }).headers(h -> h.setBearerAuth(token)).retrieve().toEntity(String.class);
    }

    private ResponseEntity<String> get(String token, String path) {
        return api.get().uri(path).headers(h -> h.setBearerAuth(token)).retrieve().toEntity(String.class);
    }

    private static void assertProblem(ResponseEntity<String> response, int status, String code) {
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(status);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).startsWith("application/problem+json");
        OpenApiContract.assertProblem(response.getBody());
        assertThat(read(response).get("code").asString()).isEqualTo(code);
    }

    private static JsonNode read(ResponseEntity<String> response) {
        return JSON.readTree(response.getBody());
    }

    private static List<String> refs(ResponseEntity<String> page) {
        return read(page).get("content").valueStream().map(order -> order.get("merchantOrderRef").asString()).toList();
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
