package lk.swiftdrop.parcel;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import lk.swiftdrop.common.security.KeycloakRealmRoleConverter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The booking flow against real PostgreSQL and Kafka, with pricing-service replaced by a local stub:
 * the parcel, its history row and the outbox row commit together, personal data is encrypted at
 * rest, and a booking that cannot be priced leaves nothing behind.
 */
@Testcontainers
@SpringBootTest(properties = {
        // test-only key (32 bytes, base64); real deployments read PII_ENCRYPTION_KEY
        "swiftdrop.pii.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        // the outbox rows are inspected directly, so the relay must not publish and mark them
        "swiftdrop.outbox.relay-enabled=false"})
@AutoConfigureMockMvc
class BookingFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.15-alpine");
    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.2");

    private static final PricingStub PRICING = new PricingStub();
    private static final String SCHEMA_BASE = "https://swiftdrop.lk/contracts/events/";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

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
        jdbc.update("DELETE FROM parcel");
    }

    private static RequestPostProcessor as(String userId, String role) {
        return jwt().jwt(token -> token.subject(userId).claim("email", userId + "@example.com")
                        .claim("realm_access", Map.of("roles", List.of(role))))
                .authorities(new KeycloakRealmRoleConverter());
    }

    private static String booking(UUID quoteId) {
        return """
                {"quoteId":"%s",
                 "pickup":{"line1":"12 Galle Road","city":"Colombo 03","district":"Colombo","postalCode":"00300"},
                 "dropoff":{"line1":"45 Peradeniya Road","city":"Kandy","district":"Kandy"},
                 "recipient":{"name":"Saman Perera","phone":"0712345678","email":"saman@example.com"}}
                """.formatted(quoteId);
    }

    private JsonNode book(String customerId) throws Exception {
        UUID quoteId = UUID.randomUUID();
        PRICING.validQuote(quoteId, Instant.now().plusSeconds(600));
        String body = mvc.perform(post("/api/v1/parcels").with(as(customerId, "CUSTOMER"))
                        .header("X-Correlation-Id", "corr-booking-1")
                        .contentType(MediaType.APPLICATION_JSON).content(booking(quoteId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    @Test
    void aBookingStoresTheParcelItsHistoryAndParcelCreatedTogether() throws Exception {
        JsonNode parcel = book("cust-1");
        UUID id = UUID.fromString(parcel.get("id").asString());

        assertThat(parcel.get("trackingNumber").asString()).matches("^SD\\d{6}[0-9A-Z]{7}$");
        assertThat(parcel.get("fee").get("amount").decimalValue()).isEqualByComparingTo("650.00");
        assertThat(jdbc.queryForObject("SELECT status FROM parcel WHERE id = ?", String.class, id)).isEqualTo("CREATED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM parcel_status_history WHERE parcel_id = ?",
                Integer.class, id)).isEqualTo(1);

        Map<String, Object> outbox = jdbc.queryForMap(
                "SELECT event_type, topic, message_key, correlation_id, payload::text AS payload FROM outbox_event");
        assertThat(outbox.get("event_type")).isEqualTo("ParcelCreated");
        assertThat(outbox.get("topic")).isEqualTo("swiftdrop.parcel.created.v1");
        assertThat(outbox.get("message_key")).isEqualTo(id.toString());
        assertThat(outbox.get("correlation_id")).isEqualTo("corr-booking-1");
        String payload = (String) outbox.get("payload");
        assertThat(payload).doesNotContain("Galle Road").doesNotContain("Peradeniya Road");
        assertThat(validate("parcel-created.v1.schema.json", payload)).isEmpty();
    }

    @Test
    void addressesAndThePhoneAreEncryptedAtRest() throws Exception {
        UUID id = UUID.fromString(book("cust-1").get("id").asString());

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT pickup_address_enc, dropoff_address_enc, recipient_phone_enc FROM parcel WHERE id = ?", id);
        assertThat(row.values()).allSatisfy(value -> assertThat((String) value).startsWith("v1:")
                .doesNotContain("Galle").doesNotContain("Kandy").doesNotContain("0712345678"));

        mvc.perform(get("/api/v1/parcels/" + id).with(as("cust-1", "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pickup.line1").value("12 Galle Road"))
                .andExpect(jsonPath("$.recipient.phone").value("0712345678"))
                .andExpect(jsonPath("$.history[0].toStatus").value("CREATED"));
    }

    @Test
    void anotherCustomerCannotSeeOrCancelTheParcel() throws Exception {
        String id = book("cust-1").get("id").asString();

        mvc.perform(get("/api/v1/parcels/" + id).with(as("cust-2", "CUSTOMER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PARCEL_NOT_FOUND"));
        mvc.perform(post("/api/v1/parcels/" + id + "/cancel").with(as("cust-2", "CUSTOMER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/parcels/mine").with(as("cust-2", "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
        mvc.perform(get("/api/v1/parcels/" + id).with(as("disp-1", "DISPATCHER")))
                .andExpect(status().isOk());
    }

    @Test
    void myParcelsListsNewestFirstAndFiltersByStatus() throws Exception {
        String first = book("cust-1").get("id").asString();
        String second = book("cust-1").get("id").asString();
        mvc.perform(post("/api/v1/parcels/" + first + "/cancel").with(as("cust-1", "CUSTOMER")))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/parcels/mine").with(as("cust-1", "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(second))
                .andExpect(jsonPath("$.content[0].history").doesNotExist());
        mvc.perform(get("/api/v1/parcels/mine?status=CANCELLED").with(as("cust-1", "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(first));
    }

    @Test
    void cancellingWritesTheHistoryRowAndTheStatusEventOnce() throws Exception {
        String id = book("cust-1").get("id").asString();

        mvc.perform(post("/api/v1/parcels/" + id + "/cancel").with(as("cust-1", "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.history.length()").value(2));
        mvc.perform(post("/api/v1/parcels/" + id + "/cancel").with(as("cust-1", "CUSTOMER")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PARCEL_NOT_CANCELLABLE"));

        assertThat(count("parcel_status_history")).isEqualTo(2);
        String payload = jdbc.queryForObject(
                "SELECT payload::text FROM outbox_event WHERE event_type = 'ParcelStatusChanged'", String.class);
        assertThat(validate("parcel-status-changed.v1.schema.json", payload)).isEmpty();
        assertThat(JSON.readTree(payload).get("data").get("toStatus").asString()).isEqualTo("CANCELLED");
    }

    @Test
    void anExpiredQuoteLeavesNothingBehind() throws Exception {
        mvc.perform(post("/api/v1/parcels").with(as("cust-1", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(booking(UUID.randomUUID())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("QUOTE_EXPIRED"));

        assertThat(count("parcel")).isZero();
        assertThat(count("outbox_event")).isZero();
    }

    @Test
    void whenPricingFailsTheAnswerIs503AndNothingIsStored() throws Exception {
        UUID quoteId = UUID.randomUUID();
        PRICING.answer(quoteId, 500, "{}");

        mvc.perform(post("/api/v1/parcels").with(as("cust-1", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(booking(quoteId)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PRICING_UNAVAILABLE"));

        assertThat(count("parcel")).isZero();
        assertThat(count("parcel_status_history")).isZero();
        assertThat(count("outbox_event")).isZero();
    }

    private static java.util.Set<?> validate(String schemaFile, String json) {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012,
                builder -> builder.schemaMappers(m -> m.mapPrefix(SCHEMA_BASE, "classpath:contracts/events/")));
        JsonSchema schema = factory.getSchema(SchemaLocation.of(SCHEMA_BASE + schemaFile),
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        return schema.validate(json, InputFormat.JSON);
    }
}
