package lk.swiftdrop.parcel.application.command;

import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.events.common.Types.ActorRole;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.events.parcel.ParcelStatusChanged;
import lk.swiftdrop.outbox.OutboxWriter;
import lk.swiftdrop.parcel.ParcelFixtures;
import lk.swiftdrop.parcel.application.port.out.ParcelRepository;
import lk.swiftdrop.parcel.domain.exception.ParcelNotCancellableException;
import lk.swiftdrop.parcel.domain.model.Parcel;
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
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelParcelUseCaseTest {

    private static final Instant LATER = ParcelFixtures.NOW.plusSeconds(900);

    @Mock
    private ParcelRepository parcels;
    @Mock
    private OutboxWriter outbox;

    private CancelParcelUseCase useCase;
    private Parcel created;

    @BeforeEach
    void setUp() {
        useCase = new CancelParcelUseCase(parcels, outbox, Clock.fixed(LATER, ZoneOffset.UTC));
        created = ParcelFixtures.bookedBy("cust-1");
        lenient().when(parcels.findById(created.id())).thenReturn(Optional.of(created));
        lenient().when(parcels.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void theOwnerCancelsAParcelThatIsStillCreated() {
        Parcel cancelled = useCase.cancel(ParcelFixtures.customer("cust-1"), created.id());

        assertThat(cancelled.status()).isEqualTo(ParcelStatus.CANCELLED);
        assertThat(cancelled.history()).hasSize(2);
        verify(parcels).save(cancelled);
    }

    @Test
    void publishesTheStatusChangeFromCreatedToCancelled() {
        useCase.cancel(ParcelFixtures.customer("cust-1"), created.id());

        ArgumentCaptor<ParcelStatusChanged> published = ArgumentCaptor.forClass(ParcelStatusChanged.class);
        verify(outbox).publish(published.capture());
        ParcelStatusChanged event = published.getValue();
        assertThat(event.parcelId()).isEqualTo(created.id());
        assertThat(event.fromStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(event.toStatus()).isEqualTo(ParcelStatus.CANCELLED);
        assertThat(event.changedBy().userId()).isEqualTo("cust-1");
        assertThat(event.changedBy().role()).isEqualTo(ActorRole.CUSTOMER);
        assertThat(event.changedAt()).isEqualTo(LATER);
    }

    @Test
    void anotherCustomerGetsTheSameNotFoundAsAMissingParcel() {
        UUID missing = UUID.randomUUID();
        when(parcels.findById(missing)).thenReturn(Optional.empty());

        ResourceNotFoundException refused = catchThrowableOfType(ResourceNotFoundException.class,
                () -> useCase.cancel(ParcelFixtures.customer("cust-2"), created.id()));
        ResourceNotFoundException notThere = catchThrowableOfType(ResourceNotFoundException.class,
                () -> useCase.cancel(ParcelFixtures.customer("cust-2"), missing));

        assertThat(refused.code()).isEqualTo("PARCEL_NOT_FOUND").isEqualTo(notThere.code());
        assertThat(refused.status()).isEqualTo(notThere.status());

        verify(parcels, never()).save(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void staffCannotCancelOnBehalfOfTheCustomer() {
        assertThatThrownBy(() -> useCase.cancel(ParcelFixtures.user("admin-1", "ADMIN", null), created.id()))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(parcels, never()).save(any());
    }

    @Test
    void aParcelThatHasMovedOnIsNotCancelledAgain() {
        Parcel alreadyCancelled = created.cancel("cust-1", ActorRole.CUSTOMER, ParcelFixtures.NOW);
        when(parcels.findById(created.id())).thenReturn(Optional.of(alreadyCancelled));

        assertThatThrownBy(() -> useCase.cancel(ParcelFixtures.customer("cust-1"), created.id()))
                .isInstanceOf(ParcelNotCancellableException.class);

        verify(parcels, never()).save(any());
        verifyNoInteractions(outbox);
    }
}
