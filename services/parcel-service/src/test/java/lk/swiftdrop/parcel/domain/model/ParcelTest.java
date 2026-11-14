package lk.swiftdrop.parcel.domain.model;

import lk.swiftdrop.events.common.Types.ActorRole;
import lk.swiftdrop.events.common.Types.ParcelSource;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.parcel.ParcelFixtures;
import lk.swiftdrop.parcel.domain.exception.ParcelNotCancellableException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParcelTest {

    private static final Instant LATER = ParcelFixtures.NOW.plusSeconds(600);

    @Test
    void aNewBookingIsCreatedWithItsFirstHistoryEntry() {
        Parcel parcel = ParcelFixtures.bookedBy("cust-1");

        assertThat(parcel.status()).isEqualTo(ParcelStatus.CREATED);
        assertThat(parcel.source()).isEqualTo(ParcelSource.CUSTOMER);
        assertThat(parcel.isCancellable()).isTrue();
        assertThat(parcel.history()).singleElement().satisfies(first -> {
            assertThat(first.fromStatus()).isNull();
            assertThat(first.toStatus()).isEqualTo(ParcelStatus.CREATED);
            assertThat(first.actorId()).isEqualTo("cust-1");
        });
    }

    @Test
    void cancellingReturnsANewParcelAndLeavesTheOriginalUntouched() {
        Parcel created = ParcelFixtures.bookedBy("cust-1");

        Parcel cancelled = created.cancel("cust-1", ActorRole.CUSTOMER, LATER);

        assertThat(created.status()).isEqualTo(ParcelStatus.CREATED);
        assertThat(created.history()).hasSize(1);
        assertThat(cancelled.status()).isEqualTo(ParcelStatus.CANCELLED);
        assertThat(cancelled.updatedAt()).isEqualTo(LATER);
        assertThat(cancelled.version()).isEqualTo(created.version());
        assertThat(cancelled.history()).hasSize(2).last().satisfies(last -> {
            assertThat(last.fromStatus()).isEqualTo(ParcelStatus.CREATED);
            assertThat(last.toStatus()).isEqualTo(ParcelStatus.CANCELLED);
            assertThat(last.occurredAt()).isEqualTo(LATER);
        });
    }

    @Test
    void aParcelThatHasMovedOnCannotBeCancelled() {
        Parcel cancelled = ParcelFixtures.bookedBy("cust-1").cancel("cust-1", ActorRole.CUSTOMER, LATER);

        assertThat(cancelled.isCancellable()).isFalse();
        assertThatThrownBy(() -> cancelled.cancel("cust-1", ActorRole.CUSTOMER, LATER))
                .isInstanceOf(ParcelNotCancellableException.class);
    }
}
