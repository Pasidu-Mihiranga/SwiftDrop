package lk.swiftdrop.parcel.domain.model;

import lk.swiftdrop.events.common.Types.ActorRole;
import lk.swiftdrop.events.common.Types.ParcelSource;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.parcel.ParcelFixtures;
import lk.swiftdrop.parcel.domain.exception.ParcelNotCancellableException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

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

    // --- merchant orders

    private static final TrackingNumber TRACKING = TrackingNumber.of(LocalDate.of(2026, 10, 20), "M3RCH7");

    @Test
    void aMerchantOrderBelongsToTheMerchantAndNoCustomer() {
        Parcel order = Parcel.bookForMerchant(TRACKING, "merchant-1", "ORD-1001", "service-account-merchant-1",
                ParcelFixtures.details(), ParcelFixtures.NOW);

        assertThat(order.source()).isEqualTo(ParcelSource.MERCHANT);
        assertThat(order.merchantId()).isEqualTo("merchant-1");
        assertThat(order.merchantOrderRef()).isEqualTo("ORD-1001");
        assertThat(order.customerId()).isNull();
        assertThat(order.status()).isEqualTo(ParcelStatus.CREATED);
        assertThat(order.version()).isNull();
        assertThat(order.history()).singleElement().satisfies(first -> {
            assertThat(first.fromStatus()).isNull();
            assertThat(first.toStatus()).isEqualTo(ParcelStatus.CREATED);
            assertThat(first.actorId()).isEqualTo("service-account-merchant-1");
            assertThat(first.actorRole()).isEqualTo(ActorRole.MERCHANT_API);
        });
    }

    @Test
    void aMerchantOrderNeedsTheMerchantAndAReference() {
        assertThatThrownBy(() -> Parcel.bookForMerchant(TRACKING, " ", "ORD-1", "sa", ParcelFixtures.details(),
                ParcelFixtures.NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Parcel.bookForMerchant(TRACKING, "merchant-1", "", "sa", ParcelFixtures.details(),
                ParcelFixtures.NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Parcel.bookForMerchant(TRACKING, "merchant-1", "R".repeat(101), "sa",
                ParcelFixtures.details(), ParcelFixtures.NOW)).isInstanceOf(IllegalArgumentException.class);
    }
}
