package lk.swiftdrop.parcel;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import lk.swiftdrop.common.security.KeycloakRealmRoleConverter;
import lk.swiftdrop.events.EventEnvelope;
import lk.swiftdrop.events.Topics;
import lk.swiftdrop.events.common.Types.Actor;
import lk.swiftdrop.events.common.Types.ActorRole;
import lk.swiftdrop.events.dispatch.ParcelAssigned;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Courier operations (M4) against real PostgreSQL and Kafka: dispatch's {@code ParcelAssigned} sets the
 * courier, the courier walks the parcel through its lifecycle, every step writes a history row and a
 * {@code ParcelStatusChanged} that validates against the event schema, and another courier gets 404.
 * Object storage is not needed: presigning is local, and the start-up bucket check only logs.
 */
@Testcontainers
@SpringBootTest(properties = {
        "swiftdrop.pii.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        // the outbox rows are inspected directly, so the relay must not publish and mark them
        "swiftdrop.outbox.relay-enabled=false",
        "swiftdrop.s3.endpoint=http://127.0.0.1:1",
        "swiftdrop.s3.public-endpoint=http://localhost:9010"})
@AutoConfigureMockMvc
class CourierStatusFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.15-alpine");
    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.2");

    private static final PricingStub PRICING = new PricingStub();
    private static final String SCHEMA_BASE = "https://swiftdrop.lk/contracts/events/";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Duration WAIT = Duration.ofSeconds(30);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private KafkaTemplate<String, String> kafka;
    @Autowired
    private ObjectMapper json;

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
        jdbc.update("DELETE FROM processed_event");
    }

    private static RequestPostProcessor as(String userId, String role) {
        return jwt().jwt(token -> token.subject(userId).claim("realm_access", Map.of("roles", List.of(role))))
                .authorities(new KeycloakRealmRoleConverter());
    }

    private UUID book() throws Exception {
        UUID quoteId = UUID.randomUUID();
        PRICING.validQuote(quoteId, Instant.now().plusSeconds(600));
        String body = mvc.perform(post("/api/v1/parcels").with(as("cust-1", "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"quoteId":"%s",
                                 "pickup":{"line1":"12 Galle Road","city":"Colombo 03","district":"Colombo"},
                                 "dropoff":{"line1":"45 Peradeniya Road","city":"Kandy","district":"Kandy"},
                                 "recipient":{"name":"Saman Perera","phone":"0712345678"}}
                                """.formatted(quoteId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JSON.readTree(body).get("id").asString());
    }

    private static EventEnvelope<ParcelAssigned> assignment(UUID parcelId, String courierId) {
        return EventEnvelope.wrap(new ParcelAssigned(UUID.randomUUID(), parcelId, "SD261020K7Q2M9X", courierId, null,
                LocalDate.now(), new Actor("disp-1", ActorRole.DISPATCHER), Instant.now()), "dispatch-service",
                "corr-assign");
    }

    /** As dispatch-service's outbox relay sends it. */
    private UUID assign(UUID parcelId, String courierId) throws Exception {
        EventEnvelope<ParcelAssigned> envelope = assignment(parcelId, courierId);
        send(parcelId, envelope);
        return envelope.eventId();
    }

    private int processed(UUID eventId) {
        return jdbc.queryForObject("SELECT count(*) FROM processed_event WHERE event_id = ?", Integer.class, eventId);
    }

    private void send(UUID parcelId, EventEnvelope<ParcelAssigned> envelope) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(Topics.PARCEL_ASSIGNED, parcelId.toString(),
                json.writeValueAsString(envelope));
        record.headers().add(Topics.HEADER_EVENT_ID, envelope.eventId().toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add(Topics.HEADER_CORRELATION_ID, "corr-assign".getBytes(StandardCharsets.UTF_8));
        kafka.send(record).get();
    }

    private String courierOf(UUID parcelId) {
        return jdbc.queryForObject("SELECT courier_id FROM parcel WHERE id = ?", String.class, parcelId);
    }

    private void move(UUID parcelId, String courier, String body, int expectedStatus) throws Exception {
        mvc.perform(patch("/api/v1/courier/parcels/" + parcelId + "/status").with(as(courier, "COURIER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void aCourierDeliversAnAssignedParcelAndEveryStepIsRecordedAndPublished() throws Exception {
        UUID parcelId = book();
        EventEnvelope<ParcelAssigned> first = assignment(parcelId, "courier-1");
        send(parcelId, first);
        await().atMost(WAIT).untilAsserted(() -> assertThat(courierOf(parcelId)).isEqualTo("courier-1"));

        // A redelivered event is ignored. The next event on the same partition is consumed after it,
        // so once that one is processed the duplicate has been seen too.
        send(parcelId, first);
        UUID next = assign(parcelId, "courier-1");
        await().atMost(WAIT).untilAsserted(() -> assertThat(processed(next)).isEqualTo(1));
        assertThat(processed(first.eventId())).isEqualTo(1);
        assertThat(courierOf(parcelId)).isEqualTo("courier-1");

        move(parcelId, "courier-2", "{\"toStatus\":\"PICKED_UP\"}", 404);
        move(parcelId, "courier-1", "{\"toStatus\":\"DELIVERED\",\"proof\":{\"type\":\"NAME\",\"receivedBy\":\"Saman\"}}", 422);
        move(parcelId, "courier-1", "{\"toStatus\":\"PICKED_UP\",\"location\":{\"lat\":6.9,\"lng\":79.85}}", 200);
        move(parcelId, "courier-1", "{\"toStatus\":\"IN_TRANSIT\"}", 200);
        move(parcelId, "courier-1", "{\"toStatus\":\"OUT_FOR_DELIVERY\"}", 200);
        move(parcelId, "courier-1", "{\"toStatus\":\"FAILED\"}", 422);

        String upload = mvc.perform(post("/api/v1/courier/parcels/" + parcelId + "/proof-upload-url")
                        .with(as("courier-1", "COURIER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\",\"sizeBytes\":2048}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value(org.hamcrest.Matchers.startsWith(
                        "http://localhost:9010/swiftdrop-proofs/proofs/" + parcelId + "/")))
                .andReturn().getResponse().getContentAsString();
        String proofId = JSON.readTree(upload).get("proofId").asString();

        move(parcelId, "courier-1",
                "{\"toStatus\":\"DELIVERED\",\"proof\":{\"type\":\"PHOTO\",\"proofId\":\"" + proofId + "\"}}", 200);

        assertThat(jdbc.queryForObject("SELECT status FROM parcel WHERE id = ?", String.class, parcelId))
                .isEqualTo("DELIVERED");
        assertThat(jdbc.queryForList("SELECT to_status FROM parcel_status_history WHERE parcel_id = ? ORDER BY occurred_at, to_status",
                String.class, parcelId)).containsExactlyInAnyOrder(
                "CREATED", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED");
        assertThat(jdbc.queryForObject("SELECT proof_id FROM parcel_status_history WHERE parcel_id = ? AND to_status = 'DELIVERED'",
                String.class, parcelId)).isEqualTo(proofId);
        assertThat(jdbc.queryForObject("SELECT lat FROM parcel_status_history WHERE parcel_id = ? AND to_status = 'PICKED_UP'",
                java.math.BigDecimal.class, parcelId)).isEqualByComparingTo("6.9");
        assertThat(jdbc.queryForObject("SELECT used_at IS NOT NULL FROM delivery_proof WHERE id = ?::uuid",
                Boolean.class, proofId)).isTrue();

        List<String> events = jdbc.queryForList(
                "SELECT payload::text FROM outbox_event WHERE event_type = 'ParcelStatusChanged' ORDER BY created_at",
                String.class);
        assertThat(events).hasSize(4);
        for (String payload : events) {
            assertThat(validate("parcel-status-changed.v1.schema.json", payload)).isEmpty();
            assertThat(payload).doesNotContain("Galle Road").doesNotContain("Peradeniya Road").doesNotContain("0712345678");
        }
        JsonNode delivered = JSON.readTree(events.get(3)).get("data");
        assertThat(delivered.get("toStatus").asString()).isEqualTo("DELIVERED");
        assertThat(delivered.get("courierId").asString()).isEqualTo("courier-1");
        assertThat(delivered.get("proof").get("proofId").asString()).isEqualTo(proofId);
        assertThat(delivered.get("location").get("city").asString()).isEqualTo("Kandy");
    }

    @Test
    void aFailedDeliveryKeepsItsReasonAndDispatchSendsItOutAgain() throws Exception {
        UUID parcelId = book();
        assign(parcelId, "courier-1");
        await().atMost(WAIT).untilAsserted(() -> assertThat(courierOf(parcelId)).isEqualTo("courier-1"));
        for (String step : List.of("PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY")) {
            move(parcelId, "courier-1", "{\"toStatus\":\"" + step + "\"}", 200);
        }

        move(parcelId, "courier-1", "{\"toStatus\":\"FAILED\",\"failureReason\":\"RECIPIENT_UNAVAILABLE\",\"note\":\"No answer\"}", 200);
        assertThat(jdbc.queryForObject(
                "SELECT failure_reason FROM parcel_status_history WHERE parcel_id = ? AND to_status = 'FAILED'",
                String.class, parcelId)).isEqualTo("RECIPIENT_UNAVAILABLE");

        mvc.perform(post("/api/v1/parcels/" + parcelId + "/reattempt").with(as("disp-1", "DISPATCHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OUT_FOR_DELIVERY"));

        String failedEvent = jdbc.queryForObject("SELECT payload::text FROM outbox_event "
                + "WHERE event_type = 'ParcelStatusChanged' AND payload->'data'->>'toStatus' = 'FAILED'", String.class);
        assertThat(validate("parcel-status-changed.v1.schema.json", failedEvent)).isEmpty();
    }

    @Test
    void aReassignmentMovesTheParcelToTheNewCourier() throws Exception {
        UUID parcelId = book();
        assign(parcelId, "courier-1");
        await().atMost(WAIT).untilAsserted(() -> assertThat(courierOf(parcelId)).isEqualTo("courier-1"));
        assign(parcelId, "courier-2");
        await().atMost(WAIT).untilAsserted(() -> assertThat(courierOf(parcelId)).isEqualTo("courier-2"));

        move(parcelId, "courier-1", "{\"toStatus\":\"PICKED_UP\"}", 404);
        move(parcelId, "courier-2", "{\"toStatus\":\"PICKED_UP\"}", 200);
    }

    private static Set<?> validate(String schemaFile, String payload) {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012,
                builder -> builder.schemaMappers(m -> m.mapPrefix(SCHEMA_BASE, "classpath:contracts/events/")));
        JsonSchema schema = factory.getSchema(SchemaLocation.of(SCHEMA_BASE + schemaFile),
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build());
        return schema.validate(payload, InputFormat.JSON);
    }
}
