package lk.swiftdrop.parcel;

import lk.swiftdrop.common.security.KeycloakRealmRoleConverter;
import lk.swiftdrop.parcel.application.port.out.MerchantStatusCache;
import lk.swiftdrop.parcel.domain.model.MerchantStanding;
import lk.swiftdrop.parcel.domain.model.MerchantStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The merchant order API end to end over HTTP, against real PostgreSQL and Kafka, with
 * pricing-service stubbed and tokens simulated. Every response body is checked against the
 * OpenAPI contract.
 */
@Testcontainers
@SpringBootTest(properties = {
        // test-only key (32 bytes, base64); real deployments read PII_ENCRYPTION_KEY
        "swiftdrop.pii.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "swiftdrop.outbox.relay-enabled=false"})
@AutoConfigureMockMvc
class MerchantOrderApiIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.15-alpine");
    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.2");

    private static final PricingStub PRICING = new PricingStub();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ORDERS = "/api/v1/merchant/orders";
    private static final String MERCHANT_A = "00000000-0000-4000-8000-0000000000a1";
    private static final String MERCHANT_B = "00000000-0000-4000-8000-0000000000b2";
    private static final String ORDER = """
            {"merchantOrderRef":"%s",
             "pickup":{"line1":"12 Galle Road","city":"Colombo 03","district":"Colombo","postalCode":"00300"},
             "dropoff":{"line1":"45 Peradeniya Road","city":"Kandy","district":"Kandy"},
             "recipient":{"name":"Saman Perera","phone":"0712345678"},
             "weightKg":2,"sizeClass":"S"}
            """;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MerchantStatusCache merchants;

    @DynamicPropertySource
    static void pricing(DynamicPropertyRegistry registry) {
        registry.add("swiftdrop.pricing.base-url", PRICING::baseUrl);
    }

    @AfterAll
    static void stopPricing() {
        PRICING.close();
    }

    @BeforeEach
    void cleanUp() {
        PRICING.reset();
        jdbc.update("DELETE FROM outbox_event");
        jdbc.update("DELETE FROM idempotency_record");
        jdbc.update("DELETE FROM parcel");
        jdbc.update("DELETE FROM merchant_status");
        merchants.apply(approved(MERCHANT_A));
        merchants.apply(approved(MERCHANT_B));
    }

    private static MerchantStanding approved(String merchantId) {
        return new MerchantStanding(merchantId, MerchantStatus.APPROVED, "Shop " + merchantId.substring(34), null,
                Instant.parse("2026-11-01T00:00:00Z"));
    }

    private static RequestPostProcessor systemOf(String merchantId) {
        return as("service-account-merchant-" + merchantId.substring(34), merchantId, "MERCHANT_API");
    }

    private static RequestPostProcessor as(String userId, String merchantId, String role) {
        return jwt().jwt(token -> token.subject(userId).claim("merchant_id", merchantId)
                .claim("realm_access", Map.of("roles", List.of(role)))).authorities(new KeycloakRealmRoleConverter());
    }

    private MockHttpServletResponse send(RequestBuilder request, int expectedStatus) throws Exception {
        MockHttpServletResponse response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(expectedStatus);
        return response;
    }

    private MockHttpServletResponse order(RequestPostProcessor caller, String key, String body, int expected)
            throws Exception {
        MockHttpServletResponse response = send(post(ORDERS).with(caller).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body), expected);
        if (expected == 200 || expected == 201) {
            OpenApiContract.assertParcelSchema("MerchantOrder", response.getContentAsString());
        } else {
            OpenApiContract.assertProblem(response.getContentAsString());
        }
        return response;
    }

    private static JsonNode json(MockHttpServletResponse response) throws Exception {
        return JSON.readTree(response.getContentAsString());
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    @Test
    void aCreatedOrderCanBeOpenedByItsMerchantButNotByAnother() throws Exception {
        MockHttpServletResponse created = order(systemOf(MERCHANT_A), "order-key-0001", ORDER.formatted("A-1"), 201);
        String id = json(created).get("id").asString();
        assertThat(json(created).get("fee").get("amount").decimalValue()).isEqualByComparingTo("650.00");
        assertThat(created.getHeader("Location")).endsWith("/api/v1/parcels/" + id);

        send(get("/api/v1/parcels/" + id).with(systemOf(MERCHANT_A)), 200);
        send(get("/api/v1/parcels/" + id).with(as("merchant-user-a", MERCHANT_A, "MERCHANT")), 200);
        OpenApiContract.assertProblem(send(get("/api/v1/parcels/" + id).with(systemOf(MERCHANT_B)), 404)
                .getContentAsString());
        assertThat(jdbc.queryForObject("SELECT merchant_id FROM parcel WHERE id = ?::uuid", String.class, id))
                .isEqualTo(MERCHANT_A);
    }

    @Test
    void theSameRequestIsReplayedEvenWhenItsJsonIsWrittenDifferently() throws Exception {
        String first = ORDER.formatted("A-1");
        String reformatted = """
                { "sizeClass" : "S", "weightKg" : 2.00, "merchantOrderRef" : "A-1",
                  "recipient" : { "phone" : "0712345678", "name" : "Saman Perera" },
                  "dropoff" : { "district" : "Kandy", "city" : "Kandy", "line1" : "45 Peradeniya Road" },
                  "pickup" : { "postalCode" : "00300", "district" : "Colombo", "city" : "Colombo 03",
                               "line1" : "12 Galle Road" },
                  "serviceLevel" : "STANDARD", "codAmount" : 0 }""";
        String id = json(order(systemOf(MERCHANT_A), "order-key-0002", first, 201)).get("id").asString();

        MockHttpServletResponse replay = order(systemOf(MERCHANT_A), "order-key-0002", reformatted, 200);

        assertThat(replay.getHeader("Idempotent-Replayed")).isEqualTo("true");
        assertThat(json(replay).get("id").asString()).isEqualTo(id);
        assertThat(count("parcel")).isEqualTo(1);
        assertThat(PRICING.calls()).as("a replay is not priced again").hasSize(1);
    }

    @Test
    void theSameKeyForADifferentOrderIs409AndCreatesNothing() throws Exception {
        order(systemOf(MERCHANT_A), "order-key-0003", ORDER.formatted("A-1"), 201);

        MockHttpServletResponse conflict = order(systemOf(MERCHANT_A), "order-key-0003", ORDER.formatted("A-2"), 409);

        assertThat(json(conflict).get("code").asString()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(count("parcel")).isEqualTo(1);
    }

    @Test
    void twoMerchantsMayUseTheSameKeyIndependently() throws Exception {
        String a = json(order(systemOf(MERCHANT_A), "order-key-0004", ORDER.formatted("A-1"), 201)).get("id").asString();
        String b = json(order(systemOf(MERCHANT_B), "order-key-0004", ORDER.formatted("A-1"), 201)).get("id").asString();

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void aSuspendedOrUnknownMerchantIs403() throws Exception {
        merchants.apply(new MerchantStanding(MERCHANT_A, MerchantStatus.SUSPENDED, "Shop a1", null, Instant.now()));

        MockHttpServletResponse suspended = order(systemOf(MERCHANT_A), "order-key-0005", ORDER.formatted("A-1"), 403);
        MockHttpServletResponse unknown = order(systemOf("00000000-0000-4000-8000-0000000000c3"), "order-key-0005",
                ORDER.formatted("C-1"), 403);

        assertThat(json(suspended).get("code").asString()).isEqualTo("MERCHANT_NOT_APPROVED");
        assertThat(json(unknown).get("code").asString()).isEqualTo("MERCHANT_NOT_APPROVED");
        assertThat(count("parcel") + count("idempotency_record")).isZero();
        assertThat(PRICING.calls()).isEmpty();
    }

    @Test
    void pricingRefusalsAre422AndOutagesAre503AndBothLeaveNothing() throws Exception {
        PRICING.answerNewQuotes(422, "{\"code\":\"DISTRICT_NOT_SERVICED\",\"detail\":\"Not serviced\"}");
        MockHttpServletResponse refused = order(systemOf(MERCHANT_A), "order-key-0006", ORDER.formatted("A-1"), 422);
        assertThat(json(refused).get("code").asString()).isEqualTo("DISTRICT_NOT_SERVICED");

        PRICING.answerNewQuotes(503, "{}");
        MockHttpServletResponse down = order(systemOf(MERCHANT_A), "order-key-0006", ORDER.formatted("A-1"), 503);
        assertThat(json(down).get("code").asString()).isEqualTo("PRICING_UNAVAILABLE");

        assertThat(count("parcel") + count("idempotency_record") + count("outbox_event")).isZero();
        PRICING.reset();
        order(systemOf(MERCHANT_A), "order-key-0006", ORDER.formatted("A-1"), 201);
    }

    @Test
    void aMissingKeyIs400WithTheSharedProblemShape() throws Exception {
        MockHttpServletResponse response = send(post(ORDERS).with(systemOf(MERCHANT_A))
                .contentType(MediaType.APPLICATION_JSON).content(ORDER.formatted("A-1")), 400);

        OpenApiContract.assertProblem(response.getContentAsString());
        assertThat(json(response).get("code").asString()).isEqualTo("IDEMPOTENCY_KEY_INVALID");
    }

    @Test
    void eachMerchantListsOnlyItsOwnOrdersOnePageAtATime() throws Exception {
        for (int i = 1; i <= 3; i++) {
            order(systemOf(MERCHANT_A), "order-key-a-" + i, ORDER.formatted("A-" + i), 201);
        }
        order(systemOf(MERCHANT_B), "order-key-b-1", ORDER.formatted("B-1"), 201);

        JsonNode firstPage = json(send(get(ORDERS).with(systemOf(MERCHANT_A)).param("size", "2"), 200));
        JsonNode secondPage = json(send(get(ORDERS).with(as("merchant-user-a", MERCHANT_A, "MERCHANT"))
                .param("size", "2").param("page", "1"), 200));
        JsonNode merchantB = json(send(get(ORDERS).with(systemOf(MERCHANT_B))
                .param("merchantId", MERCHANT_A), 200));

        OpenApiContract.assertParcelSchema("MerchantOrderPage", firstPage.toString());
        OpenApiContract.assertParcelSchema("MerchantOrderPage", secondPage.toString());
        assertThat(firstPage.get("page").get("totalElements").asInt()).isEqualTo(3);
        assertThat(firstPage.get("content")).hasSize(2);
        assertThat(secondPage.get("content")).hasSize(1);
        assertThat(firstPage.get("content").get(0).get("merchantOrderRef").asString()).as("newest first")
                .isEqualTo("A-3");
        assertThat(merchantB.get("content")).singleElement()
                .satisfies(only -> assertThat(only.get("merchantOrderRef").asString()).isEqualTo("B-1"));
        assertThat(json(send(get(ORDERS).with(systemOf(MERCHANT_A)).param("status", "DELIVERED"), 200))
                .get("page").get("totalElements").asInt()).isZero();
    }

    @Test
    void customersAndDispatchersCannotReachTheMerchantApi() throws Exception {
        for (String role : List.of("CUSTOMER", "DISPATCHER")) {
            OpenApiContract.assertProblem(send(post(ORDERS).with(as("user-1", MERCHANT_A, role))
                    .header("Idempotency-Key", "order-key-0009").contentType(MediaType.APPLICATION_JSON)
                    .content(ORDER.formatted("X-1")), 403).getContentAsString());
            send(get(ORDERS).with(as("user-1", MERCHANT_A, role)), 403);
        }
        assertThat(count("parcel")).isZero();
    }
}
