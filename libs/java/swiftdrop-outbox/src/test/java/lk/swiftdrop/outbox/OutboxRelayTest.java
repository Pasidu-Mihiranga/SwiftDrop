package lk.swiftdrop.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final OutboxProperties properties = new OutboxProperties(null, null, 1_000L, 7);
    private final OutboxRelay relay = new OutboxRelay(repository, kafka, properties, Clock.fixed(NOW, ZoneOffset.UTC));

    private static OutboxEvent event(String key, String correlationId) {
        return new OutboxEvent(UUID.randomUUID(), key, "ParcelStatusChanged", "swiftdrop.parcel.status-changed.v1",
                key, "{}", correlationId, NOW);
    }

    private static CompletableFuture<SendResult<String, String>> ok() {
        return CompletableFuture.completedFuture(null);
    }

    @Test
    void publishesBatchInOrderWithHeadersAndMarksRowsPublished() {
        OutboxEvent first = event("parcel-1", "corr-1");
        OutboxEvent second = event("parcel-2", null);
        when(repository.lockNextBatch(100)).thenReturn(List.of(first, second));
        when(kafka.send(any(ProducerRecord.class))).thenReturn(ok());

        int published = relay.publishPending();

        assertThat(published).isEqualTo(2);
        assertThat(first.getPublishedAt()).isEqualTo(NOW);
        assertThat(second.getPublishedAt()).isEqualTo(NOW);
        verify(kafka, times(2)).send(any(ProducerRecord.class));
    }

    @Test
    void stopsAtFirstFailureSoLaterEventsCannotOvertake() {
        OutboxEvent first = event("parcel-1", "corr-1");
        OutboxEvent failing = event("parcel-1", "corr-1");
        OutboxEvent later = event("parcel-1", "corr-1");
        when(repository.lockNextBatch(100)).thenReturn(List.of(first, failing, later));
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(ok())
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        int published = relay.publishPending();

        assertThat(published).isEqualTo(1);
        assertThat(first.getPublishedAt()).isNotNull();
        assertThat(failing.getPublishedAt()).isNull();
        assertThat(failing.getAttempts()).isEqualTo(1);
        assertThat(failing.getLastError()).contains("IllegalStateException").contains("broker down");
        assertThat(later.getPublishedAt()).isNull();
        assertThat(later.getAttempts()).isZero();
        verify(kafka, times(2)).send(any(ProducerRecord.class));
    }

    @Test
    void cleanupDeletesRowsOlderThanRetention() {
        when(repository.deletePublishedBefore(eq(NOW.minusSeconds(7 * 24 * 3600)))).thenReturn(5);

        assertThat(relay.deleteOldPublishedEvents()).isEqualTo(5);
    }

    @Test
    void propertiesHaveSafeDefaults() {
        OutboxProperties defaults = new OutboxProperties(null, null, null, null);

        assertThat(defaults.relayEnabled()).isTrue();
        assertThat(defaults.batchSize()).isEqualTo(100);
        assertThat(defaults.sendTimeoutMs()).isEqualTo(5_000L);
        assertThat(defaults.retentionDays()).isEqualTo(7);
    }

    @Test
    void longErrorsAreTruncated() {
        OutboxEvent e = event("k", null);
        e.recordFailure("x".repeat(2_000));
        assertThat(e.getLastError()).hasSize(500);
        e.recordFailure(null);
        assertThat(e.getLastError()).isEqualTo("unknown");
        assertThat(e.getAttempts()).isEqualTo(2);
    }
}
