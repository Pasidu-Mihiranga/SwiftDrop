package lk.swiftdrop.outbox;

import lk.swiftdrop.events.EventEnvelope;
import lk.swiftdrop.events.Topics;
import lk.swiftdrop.events.common.Types.Actor;
import lk.swiftdrop.events.common.Types.ActorRole;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.events.parcel.ParcelStatusChanged;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Event-flow integration test against real Postgres and Kafka:
 * business transaction -> outbox row (Flyway-created table) -> relay -> Kafka record with headers.
 */
@Testcontainers
@SpringBootTest(classes = OutboxRelayIT.TestApp.class, properties = {
        "spring.application.name=parcel-service",
        "swiftdrop.outbox.poll-interval-ms=100"
})
class OutboxRelayIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.15-alpine");

    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.2");

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
    }

    @Autowired
    private OutboxWriter outbox;
    @Autowired
    private IdempotentConsumer idempotency;
    @Autowired
    private OutboxEventRepository repository;
    @Autowired
    private TransactionTemplate tx;

    private static ParcelStatusChanged pickedUp(UUID parcelId) {
        return new ParcelStatusChanged(parcelId, "SD261020TESTAB1", "cust-1", null, "courier-1",
                ParcelStatus.CREATED, ParcelStatus.PICKED_UP, null, null, null, null,
                new Actor("courier-1", ActorRole.COURIER), Instant.now());
    }

    @Test
    void eventWrittenInTransactionIsRelayedToKafkaWithHeaders() {
        UUID parcelId = UUID.randomUUID();
        MDC.put("correlationId", "corr-outbox-it-1");
        EventEnvelope<ParcelStatusChanged> envelope;
        try {
            envelope = tx.execute(status -> outbox.publish(pickedUp(parcelId)));
        } finally {
            MDC.remove("correlationId");
        }

        List<ConsumerRecord<String, String>> received = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "outbox-it",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of(Topics.PARCEL_STATUS_CHANGED));
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                consumer.poll(Duration.ofMillis(250)).forEach(received::add);
                return received.stream().anyMatch(r -> r.key().equals(parcelId.toString()));
            });
        }

        ConsumerRecord<String, String> record = received.stream()
                .filter(r -> r.key().equals(parcelId.toString())).findFirst().orElseThrow();
        // JSONB normalises formatting/key order, so compare parsed values rather than raw text.
        JsonNode value = JsonMapper.builder().build().readTree(record.value());
        assertThat(value.path("eventType").asString()).isEqualTo("ParcelStatusChanged");
        assertThat(value.path("producer").asString()).isEqualTo("parcel-service");
        assertThat(value.path("correlationId").asString()).isEqualTo("corr-outbox-it-1");
        assertThat(value.path("data").path("toStatus").asString()).isEqualTo("PICKED_UP");
        assertThat(header(record, Topics.HEADER_EVENT_ID)).isEqualTo(envelope.eventId().toString());
        assertThat(header(record, Topics.HEADER_EVENT_TYPE)).isEqualTo("ParcelStatusChanged");
        assertThat(header(record, Topics.HEADER_CORRELATION_ID)).isEqualTo("corr-outbox-it-1");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(repository.findById(envelope.eventId()).orElseThrow().getPublishedAt()).isNotNull());
    }

    @Test
    void publishingOutsideATransactionIsRejected() {
        assertThatThrownBy(() -> outbox.publish(pickedUp(UUID.randomUUID())))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void idempotentConsumerAcceptsAnEventOnlyOncePerConsumer() {
        UUID eventId = UUID.randomUUID();

        Boolean first = tx.execute(s -> idempotency.firstDelivery(eventId, "tracking"));
        Boolean duplicate = tx.execute(s -> idempotency.firstDelivery(eventId, "tracking"));
        Boolean otherConsumer = tx.execute(s -> idempotency.firstDelivery(eventId, "notification"));

        assertThat(first).isTrue();
        assertThat(duplicate).isFalse();
        assertThat(otherConsumer).isTrue();
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }
}
