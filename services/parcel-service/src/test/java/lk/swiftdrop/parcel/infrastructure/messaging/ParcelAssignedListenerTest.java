package lk.swiftdrop.parcel.infrastructure.messaging;

import lk.swiftdrop.events.Topics;
import lk.swiftdrop.outbox.IdempotentConsumer;
import lk.swiftdrop.parcel.application.command.UpdateParcelStatusUseCase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParcelAssignedListenerTest {

    @Mock
    private IdempotentConsumer idempotency;
    @Mock
    private UpdateParcelStatusUseCase statuses;

    private ParcelAssignedListener listener;

    @BeforeEach
    void setUp() {
        listener = new ParcelAssignedListener(JsonMapper.builder().build(), idempotency, statuses,
                TransactionOperations.withoutTransaction());
    }

    private static ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>(Topics.PARCEL_ASSIGNED, 0, 0L, "key", value);
    }

    private static String assigned(UUID eventId, UUID parcelId, String courierId) {
        return """
                {"eventId":"%s","eventType":"ParcelAssigned","eventVersion":1,"producer":"dispatch-service",
                 "data":{"assignmentId":"%s","parcelId":"%s","trackingNumber":"SD261020K7Q2M9X","courierId":"%s",
                         "assignedAt":"2026-10-20T08:00:00Z"}}
                """.formatted(eventId, UUID.randomUUID(), parcelId, courierId);
    }

    @Test
    void anAssignmentSetsTheCourierOnce() {
        UUID eventId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        when(idempotency.firstDelivery(eventId, ParcelAssignedListener.CONSUMER)).thenReturn(true, false);

        listener.onParcelAssigned(record(assigned(eventId, parcelId, "courier-1")));
        listener.onParcelAssigned(record(assigned(eventId, parcelId, "courier-1")));

        verify(statuses).assign(parcelId, "courier-1");
    }

    @Test
    void otherEventTypesAndVersionsAreSkipped() {
        listener.onParcelAssigned(record("{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"Other\",\"eventVersion\":1}"));
        listener.onParcelAssigned(record("{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"ParcelAssigned\",\"eventVersion\":2}"));

        verifyNoInteractions(idempotency, statuses);
    }

    @Test
    void malformedMessagesAreRejected() {
        assertThatThrownBy(() -> listener.onParcelAssigned(record("not json"))).isInstanceOf(MalformedEventException.class);
        assertThatThrownBy(() -> listener.onParcelAssigned(record(" "))).isInstanceOf(MalformedEventException.class);
        assertThatThrownBy(() -> listener.onParcelAssigned(record("[1]"))).isInstanceOf(MalformedEventException.class);
        assertThatThrownBy(() -> listener.onParcelAssigned(record(assigned(UUID.randomUUID(), UUID.randomUUID(), ""))))
                .isInstanceOf(MalformedEventException.class);
        assertThatThrownBy(() -> listener.onParcelAssigned(record(
                "{\"eventId\":\"nope\",\"eventType\":\"ParcelAssigned\",\"eventVersion\":1,\"data\":{}}")))
                .isInstanceOf(MalformedEventException.class);
        verify(statuses, never()).assign(any(), anyString());
    }
}
