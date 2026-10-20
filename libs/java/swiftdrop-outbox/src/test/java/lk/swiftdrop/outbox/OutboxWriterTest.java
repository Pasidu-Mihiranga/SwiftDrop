package lk.swiftdrop.outbox;

import lk.swiftdrop.events.EventEnvelope;
import lk.swiftdrop.events.Topics;
import lk.swiftdrop.events.common.Types.Actor;
import lk.swiftdrop.events.common.Types.ActorRole;
import lk.swiftdrop.events.dispatch.ParcelAssigned;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxWriterTest {

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final OutboxWriter writer = new OutboxWriter(repository, JsonMapper.builder().build(), "dispatch-service");

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void storesSerialisedEnvelopeWithRoutingInformation() {
        MDC.put("correlationId", "corr-42");
        UUID parcelId = UUID.randomUUID();
        ParcelAssigned payload = new ParcelAssigned(UUID.randomUUID(), parcelId, "SD261020ABCDEFG", "courier-1", null,
                LocalDate.parse("2026-10-20"), new Actor("dispatcher-1", ActorRole.DISPATCHER), Instant.now());

        EventEnvelope<ParcelAssigned> envelope = writer.publish(payload);

        ArgumentCaptor<OutboxEvent> saved = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(saved.capture());
        OutboxEvent row = saved.getValue();
        assertThat(row.getId()).isEqualTo(envelope.eventId());
        assertThat(row.getTopic()).isEqualTo(Topics.PARCEL_ASSIGNED);
        assertThat(row.getMessageKey()).isEqualTo(parcelId.toString());
        assertThat(row.getAggregateId()).isEqualTo(parcelId.toString());
        assertThat(row.getEventType()).isEqualTo("ParcelAssigned");
        assertThat(row.getCorrelationId()).isEqualTo("corr-42");
        assertThat(row.getCreatedAt()).isEqualTo(envelope.occurredAt());
        assertThat(row.getPublishedAt()).isNull();
        assertThat(row.getAttempts()).isZero();
        assertThat(row.getLastError()).isNull();
        assertThat(row.getPayload())
                .contains("\"producer\":\"dispatch-service\"")
                .contains("\"correlationId\":\"corr-42\"")
                .contains("\"runDate\":\"2026-10-20\"");
        assertThat(envelope.producer()).isEqualTo("dispatch-service");
    }

    @Test
    void idempotentConsumerReportsFirstDeliveryOnly() {
        ProcessedEventRepository processed = mock(ProcessedEventRepository.class);
        UUID eventId = UUID.randomUUID();
        when(processed.insertIfAbsent(eq(eventId), any())).thenReturn(1, 0);
        IdempotentConsumer consumer = new IdempotentConsumer(processed);

        assertThat(consumer.firstDelivery(eventId, "tracking")).isTrue();
        assertThat(consumer.firstDelivery(eventId, "tracking")).isFalse();
    }
}
