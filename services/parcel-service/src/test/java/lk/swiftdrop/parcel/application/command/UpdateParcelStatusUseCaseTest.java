package lk.swiftdrop.parcel.application.command;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lk.swiftdrop.parcel.infrastructure.metrics.MicrometerParcelMetrics;
import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.events.common.Types.ActorRole;
import lk.swiftdrop.events.common.Types.FailureReason;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.events.parcel.ParcelStatusChanged;
import lk.swiftdrop.outbox.OutboxWriter;
import lk.swiftdrop.parcel.ParcelFixtures;
import lk.swiftdrop.parcel.application.port.out.DeliveryProofStore;
import lk.swiftdrop.parcel.application.port.out.DeliveryProofStore.StoredProof;
import lk.swiftdrop.parcel.application.port.out.ParcelRepository;
import lk.swiftdrop.parcel.domain.exception.InvalidStatusTransitionException;
import lk.swiftdrop.parcel.domain.model.Parcel;
import lk.swiftdrop.parcel.domain.model.ProofOfDelivery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateParcelStatusUseCaseTest {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private double count(String name, String tag, String value) {
        return registry.get(name).tag(tag, value).counter().count();
    }


    private static final Instant LATER = ParcelFixtures.NOW.plusSeconds(3600);

    @Mock
    private ParcelRepository parcels;
    @Mock
    private DeliveryProofStore proofs;
    @Mock
    private OutboxWriter outbox;

    private UpdateParcelStatusUseCase useCase;
    private Parcel assigned;

    @BeforeEach
    void setUp() {
        useCase = new UpdateParcelStatusUseCase(parcels, proofs, outbox, Clock.fixed(LATER, ZoneOffset.UTC), new MicrometerParcelMetrics(registry));
        assigned = ParcelFixtures.bookedBy("cust-1").assignCourier("courier-1", ParcelFixtures.NOW);
        lenient().when(parcels.findById(assigned.id())).thenReturn(Optional.of(assigned));
        lenient().when(parcels.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private static StatusUpdate to(ParcelStatus status) {
        return new StatusUpdate(status, null, null, null, null);
    }

    private void stored(Parcel parcel) {
        when(parcels.findById(parcel.id())).thenReturn(Optional.of(parcel));
    }

    private ParcelStatusChanged published() {
        ArgumentCaptor<ParcelStatusChanged> event = ArgumentCaptor.forClass(ParcelStatusChanged.class);
        verify(outbox).publish(event.capture());
        return event.getValue();
    }

    @Test
    void theAssignedCourierPicksUpAndTheEventCarriesThePickupTown() {
        Parcel picked = useCase.update(ParcelFixtures.user("courier-1", "COURIER", null), assigned.id(),
                to(ParcelStatus.PICKED_UP));

        assertThat(picked.status()).isEqualTo(ParcelStatus.PICKED_UP);
        ParcelStatusChanged event = published();
        assertThat(event.fromStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(event.toStatus()).isEqualTo(ParcelStatus.PICKED_UP);
        assertThat(event.courierId()).isEqualTo("courier-1");
        assertThat(event.changedBy().role()).isEqualTo(ActorRole.COURIER);
        assertThat(event.location().city()).isEqualTo("Colombo 03");
        assertThat(event.location().zoneCode()).isEqualTo("WP-COL");
        assertThat(event.changedAt()).isEqualTo(LATER);
    }

    @Test
    void anotherCourierGets404AndNothingChanges() {
        assertThatThrownBy(() -> useCase.update(ParcelFixtures.user("courier-2", "COURIER", null), assigned.id(),
                to(ParcelStatus.PICKED_UP))).isInstanceOf(ResourceNotFoundException.class);

        verify(parcels, never()).save(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void aFailureIsPublishedWithItsReasonAndTheDestinationTown() {
        Parcel out = move(move(move(assigned, ParcelStatus.PICKED_UP), ParcelStatus.IN_TRANSIT), ParcelStatus.OUT_FOR_DELIVERY);
        stored(out);

        useCase.update(ParcelFixtures.user("courier-1", "COURIER", null), out.id(),
                new StatusUpdate(ParcelStatus.FAILED, "Nobody home", FailureReason.RECIPIENT_UNAVAILABLE, null, null));

        ParcelStatusChanged event = published();
        assertThat(event.failureReason()).isEqualTo(FailureReason.RECIPIENT_UNAVAILABLE);
        assertThat(event.note()).isEqualTo("Nobody home");
        assertThat(event.location().city()).isEqualTo("Kandy");
        assertThat(count("parcel.status.changes", "status", "failed")).isEqualTo(1);
        assertThat(count("parcel.delivery.failures", "reason", "recipient_unavailable")).isEqualTo(1);
    }

    @Test
    void aPhotoProofMustBelongToTheParcelAndIsMarkedUsed() {
        Parcel out = move(move(move(assigned, ParcelStatus.PICKED_UP), ParcelStatus.IN_TRANSIT), ParcelStatus.OUT_FOR_DELIVERY);
        stored(out);
        UUID proofId = UUID.randomUUID();
        when(proofs.findById(proofId)).thenReturn(Optional.of(
                new StoredProof(proofId, out.id(), "proofs/x.jpg", "image/jpeg", 1000, LATER, null)));

        useCase.update(ParcelFixtures.user("courier-1", "COURIER", null), out.id(), new StatusUpdate(
                ParcelStatus.DELIVERED, null, null, new ProofOfDelivery(ProofOfDelivery.Type.PHOTO, null, proofId.toString()),
                null));

        verify(proofs).markUsed(proofId, LATER);
        ParcelStatusChanged event = published();
        assertThat(event.proof().type()).isEqualTo(ParcelStatusChanged.ProofType.PHOTO);
        assertThat(event.proof().proofId()).isEqualTo(proofId.toString());
    }

    @Test
    void aProofUploadedForAnotherParcelIsRefused() {
        Parcel out = move(move(move(assigned, ParcelStatus.PICKED_UP), ParcelStatus.IN_TRANSIT), ParcelStatus.OUT_FOR_DELIVERY);
        stored(out);
        UUID proofId = UUID.randomUUID();
        when(proofs.findById(proofId)).thenReturn(Optional.of(
                new StoredProof(proofId, UUID.randomUUID(), "proofs/y.jpg", "image/jpeg", 1000, LATER, null)));
        StatusUpdate delivered = new StatusUpdate(ParcelStatus.DELIVERED, null, null,
                new ProofOfDelivery(ProofOfDelivery.Type.PHOTO, null, proofId.toString()), null);

        assertThatThrownBy(() -> useCase.update(ParcelFixtures.user("courier-1", "COURIER", null), out.id(), delivered))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("not uploaded for this parcel");
        assertThatThrownBy(() -> useCase.update(ParcelFixtures.user("courier-1", "COURIER", null), out.id(),
                new StatusUpdate(ParcelStatus.DELIVERED, null, null,
                        new ProofOfDelivery(ProofOfDelivery.Type.PHOTO, null, "not-an-id"), null)))
                .isInstanceOf(BusinessRuleViolationException.class);
        verifyNoInteractions(outbox);
    }

    @Test
    void aCourierCannotReattemptTheirOwnFailureButDispatchCan() {
        Parcel failed = move(move(move(assigned, ParcelStatus.PICKED_UP), ParcelStatus.IN_TRANSIT), ParcelStatus.OUT_FOR_DELIVERY)
                .changeStatus(ParcelStatus.FAILED, "courier-1", ActorRole.COURIER, null, FailureReason.WRONG_ADDRESS,
                        null, null, LATER);
        stored(failed);

        assertThatThrownBy(() -> useCase.update(ParcelFixtures.user("courier-1", "COURIER", null), failed.id(),
                to(ParcelStatus.OUT_FOR_DELIVERY))).isInstanceOf(InvalidStatusTransitionException.class);

        Parcel again = useCase.reattempt(ParcelFixtures.user("disp-1", "DISPATCHER", null), failed.id(), "Call first");
        assertThat(again.status()).isEqualTo(ParcelStatus.OUT_FOR_DELIVERY);
        assertThat(published().changedBy().role()).isEqualTo(ActorRole.DISPATCHER);
    }

    @Test
    void reattemptIsForFailedParcelsAndForStaffOnly() {
        assertThatThrownBy(() -> useCase.reattempt(ParcelFixtures.user("disp-1", "DISPATCHER", null), assigned.id(), null))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(() -> useCase.reattempt(ParcelFixtures.user("courier-1", "COURIER", null), assigned.id(), null))
                .isInstanceOf(ResourceNotFoundException.class);
        UUID missing = UUID.randomUUID();
        when(parcels.findById(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.reattempt(ParcelFixtures.user("admin-1", "ADMIN", null), missing, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void anAssignmentSetsTheCourierOnceAndSkipsUnknownParcels() {
        Parcel unassigned = ParcelFixtures.bookedBy("cust-1");
        stored(unassigned);

        useCase.assign(unassigned.id(), "courier-7");
        ArgumentCaptor<Parcel> saved = ArgumentCaptor.forClass(Parcel.class);
        verify(parcels).save(saved.capture());
        assertThat(saved.getValue().courierId()).isEqualTo("courier-7");

        useCase.assign(assigned.id(), "courier-1");
        verify(parcels).save(any());

        UUID unknown = UUID.randomUUID();
        when(parcels.findById(unknown)).thenReturn(Optional.empty());
        useCase.assign(unknown, "courier-1");
        verify(parcels).save(any());
    }

    private static Parcel move(Parcel parcel, ParcelStatus to) {
        return parcel.changeStatus(to, "courier-1", ActorRole.COURIER, null, null, null, null, LATER);
    }
}
