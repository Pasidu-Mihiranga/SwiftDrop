package lk.swiftdrop.reporting.application.command;

import lk.swiftdrop.events.EventEnvelope;
import lk.swiftdrop.events.dispatch.ParcelAssigned;
import lk.swiftdrop.events.parcel.ParcelCreated;
import lk.swiftdrop.events.parcel.ParcelStatusChanged;
import lk.swiftdrop.outbox.IdempotentConsumer;
import lk.swiftdrop.reporting.application.port.out.ProjectionStore;
import lk.swiftdrop.reporting.application.port.out.ProjectionStore.AssignmentFact;
import lk.swiftdrop.reporting.application.port.out.ProjectionStore.CreatedFact;
import lk.swiftdrop.reporting.application.port.out.ProjectionStore.TransitionFact;
import lk.swiftdrop.reporting.domain.model.BusinessCalendar;
import lk.swiftdrop.reporting.support.ContractEvents;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProjectionServiceTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final UUID PARCEL = UUID.randomUUID();
    private static final LocalDate OCT_20 = LocalDate.of(2026, 10, 20);
    private static final LocalDate OCT_21 = LocalDate.of(2026, 10, 21);

    private final IdempotentConsumer idempotency = mock(IdempotentConsumer.class);
    private final ProjectionStore store = mock(ProjectionStore.class);
    private final ProjectionService service =
            new ProjectionService(idempotency, store, new BusinessCalendar(ZoneId.of("Asia/Colombo")));

    @Test
    void createdRecordsTheDropoffZoneAndRebuildsEveryTouchedDayInOrder() {
        EventEnvelope<ParcelCreated> event = created(Instant.parse("2026-10-20T08:15:29Z"));
        when(idempotency.firstDelivery(event.eventId(), ProjectionService.CREATED_CONSUMER)).thenReturn(true);
        when(store.daysTouchedBy(PARCEL)).thenReturn(new TreeSet<>(java.util.List.of(OCT_21, OCT_20)));

        assertThat(service.onParcelCreated(event)).isTrue();

        ArgumentCaptor<CreatedFact> fact = ArgumentCaptor.forClass(CreatedFact.class);
        InOrder order = inOrder(store);
        order.verify(store).lockParcel(PARCEL);
        order.verify(store).upsertCreated(fact.capture());
        order.verify(store).recomputeDay(OCT_20);
        order.verify(store).recomputeDay(OCT_21);
        assertThat(fact.getValue().zoneCode()).isEqualTo("CP-KDY");
        assertThat(fact.getValue().source()).isEqualTo("MERCHANT");
        assertThat(fact.getValue().createdDate()).isEqualTo(OCT_20);
    }

    @Test
    void duplicatesAreIgnored() {
        EventEnvelope<ParcelCreated> event = created(Instant.parse("2026-10-20T08:15:29Z"));
        when(idempotency.firstDelivery(event.eventId(), ProjectionService.CREATED_CONSUMER)).thenReturn(false);

        assertThat(service.onParcelCreated(event)).isFalse();
        verifyNoInteractions(store);
    }

    @Test
    void statusChangeIsBucketedOnTheLocalBusinessDay() {
        // 19:30 UTC on the 20th is already the 21st in Colombo.
        EventEnvelope<ParcelStatusChanged> event = read(ContractEvents.statusChanged(UUID.randomUUID(), PARCEL,
                "FAILED", "courier-7", Instant.parse("2026-10-20T19:30:00Z")), new TypeReference<>() { });
        when(idempotency.firstDelivery(event.eventId(), ProjectionService.STATUS_CONSUMER)).thenReturn(true);
        when(store.daysTouchedBy(PARCEL)).thenReturn(new TreeSet<>(java.util.List.of(OCT_21)));

        assertThat(service.onStatusChanged(event)).isTrue();

        ArgumentCaptor<TransitionFact> fact = ArgumentCaptor.forClass(TransitionFact.class);
        verify(store).insertTransition(fact.capture());
        assertThat(fact.getValue().businessDate()).isEqualTo(OCT_21);
        assertThat(fact.getValue().toStatus()).isEqualTo("FAILED");
        assertThat(fact.getValue().failureReason()).isEqualTo("RECIPIENT_UNAVAILABLE");
        assertThat(fact.getValue().courierId()).isEqualTo("courier-7");
        verify(store).recomputeDay(OCT_21);
    }

    @Test
    void assignmentUpdatesTheCourierAndRebuildsTheParcelsDays() {
        EventEnvelope<ParcelAssigned> event = read(ContractEvents.assigned(UUID.randomUUID(), PARCEL, "courier-9",
                Instant.parse("2026-10-20T09:00:04Z")), new TypeReference<>() { });
        when(idempotency.firstDelivery(event.eventId(), ProjectionService.ASSIGNED_CONSUMER)).thenReturn(true);
        when(store.daysTouchedBy(PARCEL)).thenReturn(new TreeSet<>(java.util.List.of(OCT_20)));

        assertThat(service.onParcelAssigned(event)).isTrue();

        ArgumentCaptor<AssignmentFact> fact = ArgumentCaptor.forClass(AssignmentFact.class);
        verify(store).upsertAssignment(fact.capture());
        assertThat(fact.getValue().courierId()).isEqualTo("courier-9");
        verify(store).recomputeDay(OCT_20);
    }

    @Test
    void missingTimestampFallsBackToWhenTheEventOccurred() {
        EventEnvelope<ParcelCreated> original = created(Instant.parse("2026-10-20T08:15:29Z"));
        ParcelCreated d = original.data();
        ParcelCreated withoutCreatedAt = new ParcelCreated(d.parcelId(), d.trackingNumber(), d.source(), d.customerId(),
                d.merchantId(), d.merchantOrderRef(), d.pickup(), d.dropoff(), d.sizeClass(), d.weightKg(),
                d.serviceLevel(), d.fee(), d.contacts(), null);
        EventEnvelope<ParcelCreated> event = new EventEnvelope<>(original.eventId(), original.eventType(),
                original.eventVersion(), Instant.parse("2026-10-20T19:00:00Z"), null, "parcel-service", withoutCreatedAt);
        when(idempotency.firstDelivery(any(), any())).thenReturn(true);
        when(store.daysTouchedBy(PARCEL)).thenReturn(new TreeSet<>());

        service.onParcelCreated(event);

        ArgumentCaptor<CreatedFact> fact = ArgumentCaptor.forClass(CreatedFact.class);
        verify(store).upsertCreated(fact.capture());
        assertThat(fact.getValue().createdDate()).isEqualTo(OCT_21);
    }

    private static EventEnvelope<ParcelCreated> created(Instant createdAt) {
        return read(ContractEvents.created(UUID.randomUUID(), PARCEL, "CP-KDY", createdAt), new TypeReference<>() { });
    }

    private static <T> T read(String json, TypeReference<T> type) {
        return MAPPER.readValue(json, type);
    }
}
