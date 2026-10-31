package lk.swiftdrop.reporting;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lk.swiftdrop.common.security.KeycloakRealmRoleConverter;
import lk.swiftdrop.events.Topics;
import lk.swiftdrop.reporting.support.ContractEvents;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Event flow against real Postgres and Kafka: contract-shaped events go onto the topics, the
 * listeners project them, and the report endpoints return the result. Each test uses its own
 * business day so the tests cannot see each other's data.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ReportingProjectionIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.15-alpine");
    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.2");

    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TestConfiguration
    static class Topology {
        @Bean
        NewTopic parcelCreated() {
            return TopicBuilder.name(Topics.PARCEL_CREATED).partitions(3).build();
        }

        @Bean
        NewTopic parcelStatusChanged() {
            return TopicBuilder.name(Topics.PARCEL_STATUS_CHANGED).partitions(3).build();
        }

        @Bean
        NewTopic parcelAssigned() {
            return TopicBuilder.name(Topics.PARCEL_ASSIGNED).partitions(3).build();
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private KafkaTemplate<String, String> kafka;
    @Autowired
    private MeterRegistry meters;

    @Test
    void parcelLifecycleShowsUpInEveryReport() throws Exception {
        UUID parcel = UUID.randomUUID();
        publish(Topics.PARCEL_CREATED, parcel, ContractEvents.created(UUID.randomUUID(), parcel, "CP-KDY",
                Instant.parse("2026-01-05T03:00:00Z")));
        publish(Topics.PARCEL_ASSIGNED, parcel, ContractEvents.assigned(UUID.randomUUID(), parcel, "courier-a",
                Instant.parse("2026-01-05T04:00:00Z")));
        publish(Topics.PARCEL_STATUS_CHANGED, parcel, ContractEvents.statusChanged(UUID.randomUUID(), parcel,
                "DELIVERED", "courier-a", Instant.parse("2026-01-05T10:00:00Z")));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            JsonNode zone = zone("2026-01-05", "CP-KDY");
            assertThat(zone).isNotNull();
            assertThat(zone.get("created").asLong()).isEqualTo(1);
            assertThat(zone.get("delivered").asLong()).isEqualTo(1);
        });

        JsonNode daily = report("/api/v1/reports/daily?from=2026-01-04&to=2026-01-06");
        assertThat(daily.get("days")).hasSize(3);
        assertThat(daily.get("days").get(1).get("delivered").asLong()).isEqualTo(1);
        assertThat(daily.get("days").get(0).get("created").asLong()).isZero();

        JsonNode courier = report("/api/v1/reports/couriers?from=2026-01-05&to=2026-01-05").get(0);
        assertThat(courier.get("courierId").asString()).isEqualTo("courier-a");
        assertThat(courier.get("successRate").asDouble()).isEqualTo(1.0);

        JsonNode rate = report("/api/v1/reports/success-rate?from=2026-01-05&to=2026-01-05&zone=CP-KDY");
        assertThat(rate.get("delivered").asLong()).isEqualTo(1);
    }

    @Test
    void eventsArrivingBeforeParcelCreatedAreReattributedWhenItArrives() throws Exception {
        UUID parcel = UUID.randomUUID();
        // Delivered with no courier id on the event, before dispatch or parcel-service have been heard from.
        publish(Topics.PARCEL_STATUS_CHANGED, parcel, ContractEvents.statusChanged(UUID.randomUUID(), parcel,
                "DELIVERED", null, Instant.parse("2026-01-10T09:00:00Z")));

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(zone("2026-01-10", "UNKNOWN")).isNotNull());
        assertThat(report("/api/v1/reports/couriers?from=2026-01-10&to=2026-01-10")).isEmpty();

        publish(Topics.PARCEL_ASSIGNED, parcel, ContractEvents.assigned(UUID.randomUUID(), parcel, "courier-b",
                Instant.parse("2026-01-10T05:00:00Z")));
        publish(Topics.PARCEL_CREATED, parcel, ContractEvents.created(UUID.randomUUID(), parcel, "SP-GAL",
                Instant.parse("2026-01-10T02:00:00Z")));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            JsonNode zone = zone("2026-01-10", "SP-GAL");
            assertThat(zone).isNotNull();
            assertThat(zone.get("created").asLong()).isEqualTo(1);
            assertThat(zone.get("delivered").asLong()).isEqualTo(1);
            assertThat(zone("2026-01-10", "UNKNOWN")).isNull();
            JsonNode couriers = report("/api/v1/reports/couriers?from=2026-01-10&to=2026-01-10");
            assertThat(couriers).hasSize(1);
            assertThat(couriers.get(0).get("courierId").asString()).isEqualTo("courier-b");
        });
    }

    @Test
    void redeliveredEventsAreCountedOnce() throws Exception {
        UUID parcel = UUID.randomUUID();
        String event = ContractEvents.created(UUID.randomUUID(), parcel, "WP-COL", Instant.parse("2026-01-15T06:00:00Z"));
        double duplicatesBefore = count("reporting.events.processed", "type", "ParcelCreated", "outcome", "duplicate");

        publish(Topics.PARCEL_CREATED, parcel, event);
        publish(Topics.PARCEL_CREATED, parcel, event);

        await().atMost(TIMEOUT).untilAsserted(() ->
                assertThat(count("reporting.events.processed", "type", "ParcelCreated", "outcome", "duplicate"))
                        .isGreaterThan(duplicatesBefore));
        assertThat(zone("2026-01-15", "WP-COL").get("created").asLong()).isEqualTo(1);
    }

    @Test
    void poisonRecordIsSkippedAndTheNextOneIsStillApplied() throws Exception {
        UUID parcel = UUID.randomUUID();
        double rejectedBefore = count("reporting.events.rejected", "reason", "malformed");

        // Same key, so both land on one partition and the valid record waits behind the poison one.
        kafka.send(Topics.PARCEL_CREATED, parcel.toString(), "{ this is not json").get();
        publish(Topics.PARCEL_CREATED, parcel, ContractEvents.created(UUID.randomUUID(), parcel, "NW-KUR",
                Instant.parse("2026-01-20T06:00:00Z")));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            assertThat(zone("2026-01-20", "NW-KUR")).isNotNull();
            assertThat(count("reporting.events.rejected", "reason", "malformed")).isGreaterThan(rejectedBefore);
        });
    }

    @Test
    void failedAttemptFollowedByDeliveryCountsBoth() throws Exception {
        UUID parcel = UUID.randomUUID();
        publish(Topics.PARCEL_CREATED, parcel, ContractEvents.created(UUID.randomUUID(), parcel, "CP-NUW",
                Instant.parse("2026-01-25T02:00:00Z")));
        publish(Topics.PARCEL_STATUS_CHANGED, parcel, ContractEvents.statusChanged(UUID.randomUUID(), parcel,
                "FAILED", "courier-c", Instant.parse("2026-01-25T06:00:00Z")));
        publish(Topics.PARCEL_STATUS_CHANGED, parcel, ContractEvents.statusChanged(UUID.randomUUID(), parcel,
                "DELIVERED", "courier-c", Instant.parse("2026-01-25T11:00:00Z")));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            JsonNode courier = report("/api/v1/reports/couriers?from=2026-01-25&to=2026-01-25").get(0);
            assertThat(courier).isNotNull();
            assertThat(courier.get("delivered").asLong()).isEqualTo(1);
            assertThat(courier.get("failed").asLong()).isEqualTo(1);
            assertThat(courier.get("successRate").asDouble()).isEqualTo(0.5);
        });
    }

    private void publish(String topic, UUID parcel, String value) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, parcel.toString(), value);
        record.headers().add(Topics.HEADER_EVENT_ID,
                JSON.readTree(value).get("eventId").asString().getBytes(StandardCharsets.UTF_8));
        kafka.send(record).get();
    }

    private JsonNode zone(String day, String zoneCode) throws Exception {
        for (JsonNode zone : report("/api/v1/reports/zones?from=" + day + "&to=" + day)) {
            if (zoneCode.equals(zone.get("zoneCode").asString())) {
                return zone;
            }
        }
        return null;
    }

    private JsonNode report(String uri) throws Exception {
        String body = mvc.perform(get(uri).with(jwt()
                        .jwt(token -> token.subject("admin-1").claim("realm_access", Map.of("roles", List.of("ADMIN"))))
                        .authorities(new KeycloakRealmRoleConverter())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body);
    }

    private double count(String name, String... tags) {
        Counter counter = meters.find(name).tags(tags).counter();
        return counter == null ? 0 : counter.count();
    }
}
