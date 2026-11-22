package lk.swiftdrop.parcel.application.command;

import lk.swiftdrop.events.common.Types;
import lk.swiftdrop.events.parcel.ParcelStatusChanged;
import lk.swiftdrop.outbox.OutboxWriter;
import lk.swiftdrop.parcel.ParcelFixtures;
import lk.swiftdrop.parcel.application.port.out.DeliveryProofStore;
import lk.swiftdrop.parcel.application.port.out.ParcelRepository;
import lk.swiftdrop.parcel.domain.model.Parcel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourierStatusServiceTest {

    @Mock ParcelRepository parcels;
    @Mock DeliveryProofStore proofs;
    @Mock OutboxWriter outbox;
    private CourierStatusService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-20T09:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new CourierStatusService(parcels, proofs, outbox, clock);
    }

    @Test
    void assignedCourierCanAdvanceAndPublishesAfterSaving() {
        Parcel initial = ParcelFixtures.bookedBy("customer-1").assignCourier("courier-1", clock.instant());
        when(parcels.findById(initial.id())).thenReturn(Optional.of(initial));
        when(parcels.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Parcel result = service.update(initial.id(), Types.ParcelStatus.PICKED_UP, "Collected", null, null, null,
                ParcelFixtures.user("courier-1", "COURIER", null));

        org.assertj.core.api.Assertions.assertThat(result.status()).isEqualTo(Types.ParcelStatus.PICKED_UP);
        verify(parcels).save(any());
        verify(outbox).publish(any(ParcelStatusChanged.class));
    }

    @Test
    void anotherCourierCannotUpdateTheParcel() {
        Parcel initial = ParcelFixtures.bookedBy("customer-1").assignCourier("courier-1", clock.instant());
        when(parcels.findById(initial.id())).thenReturn(Optional.of(initial));

        assertThatThrownBy(() -> service.update(initial.id(), Types.ParcelStatus.PICKED_UP, null, null, null, null,
                ParcelFixtures.user("courier-2", "COURIER", null)))
                .hasMessageContaining("not found");
    }

    @Test
    void failedDeliveryRequiresAReason() {
        Parcel initial = ParcelFixtures.bookedBy("customer-1").assignCourier("courier-1", clock.instant())
                .changeStatus(Types.ParcelStatus.PICKED_UP, "courier-1", Types.ActorRole.COURIER, null, null, null,
                        null, null, null, clock.instant())
                .changeStatus(Types.ParcelStatus.IN_TRANSIT, "courier-1", Types.ActorRole.COURIER, null, null, null,
                        null, null, null, clock.instant())
                .changeStatus(Types.ParcelStatus.OUT_FOR_DELIVERY, "courier-1", Types.ActorRole.COURIER, null, null,
                        null, null, null, null, clock.instant());
        when(parcels.findById(initial.id())).thenReturn(Optional.of(initial));

        assertThatThrownBy(() -> service.update(initial.id(), Types.ParcelStatus.FAILED, null, null, null, null,
                ParcelFixtures.user("courier-1", "COURIER", null)))
                .hasMessageContaining("failure reason");
    }
}
