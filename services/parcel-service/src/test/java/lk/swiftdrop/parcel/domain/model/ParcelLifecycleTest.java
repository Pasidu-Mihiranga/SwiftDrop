package lk.swiftdrop.parcel.domain.model;

import lk.swiftdrop.events.common.Types.ActorRole;
import lk.swiftdrop.events.common.Types.FailureReason;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.parcel.ParcelFixtures;
import lk.swiftdrop.parcel.domain.exception.DeliveryProofRequiredException;
import lk.swiftdrop.parcel.domain.exception.FailureReasonRequiredException;
import lk.swiftdrop.parcel.domain.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParcelLifecycleTest {

    private static final Instant T1 = ParcelFixtures.NOW.plusSeconds(3600);
    private static final ProofOfDelivery SIGNED_BY_SAMAN = new ProofOfDelivery(ProofOfDelivery.Type.NAME, "Saman", null);

    private static Parcel move(Parcel parcel, ParcelStatus to) {
        return parcel.changeStatus(to, "courier-1", ActorRole.COURIER, null, null, null, null, T1);
    }

    private static Parcel outForDelivery() {
        Parcel parcel = ParcelFixtures.bookedBy("cust-1").assignCourier("courier-1", T1);
        return move(move(move(parcel, ParcelStatus.PICKED_UP), ParcelStatus.IN_TRANSIT), ParcelStatus.OUT_FOR_DELIVERY);
    }

    @Test
    void eachStepAppendsOneHistoryEntryAndKeepsTheCourier() {
        Parcel parcel = outForDelivery();

        assertThat(parcel.status()).isEqualTo(ParcelStatus.OUT_FOR_DELIVERY);
        assertThat(parcel.courierId()).isEqualTo("courier-1");
        assertThat(parcel.history()).extracting(StatusChange::toStatus).containsExactly(
                ParcelStatus.CREATED, ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY);
        assertThat(parcel.history().get(3).fromStatus()).isEqualTo(ParcelStatus.IN_TRANSIT);
        assertThat(parcel.updatedAt()).isEqualTo(T1);
    }

    @Test
    void createdCannotJumpToDelivered() {
        Parcel parcel = ParcelFixtures.bookedBy("cust-1");

        assertThatThrownBy(() -> parcel.changeStatus(ParcelStatus.DELIVERED, "courier-1", ActorRole.COURIER, null, null,
                SIGNED_BY_SAMAN, null, T1))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("CREATED to DELIVERED");
    }

    @Test
    void cancellingGoesThroughCancelNotAStatusUpdate() {
        assertThatThrownBy(() -> move(ParcelFixtures.bookedBy("cust-1"), ParcelStatus.CANCELLED))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void aFailedDeliveryNeedsAReasonAndKeepsIt() {
        Parcel parcel = outForDelivery();

        assertThatThrownBy(() -> move(parcel, ParcelStatus.FAILED)).isInstanceOf(FailureReasonRequiredException.class);

        Parcel failed = parcel.changeStatus(ParcelStatus.FAILED, "courier-1", ActorRole.COURIER, "Gate locked",
                FailureReason.RECIPIENT_UNAVAILABLE, null, null, T1);
        StatusChange last = failed.history().get(failed.history().size() - 1);
        assertThat(last.failureReason()).isEqualTo(FailureReason.RECIPIENT_UNAVAILABLE);
        assertThat(last.note()).isEqualTo("Gate locked");
    }

    @Test
    void aDeliveryNeedsCompleteProof() {
        Parcel parcel = outForDelivery();

        assertThatThrownBy(() -> move(parcel, ParcelStatus.DELIVERED)).isInstanceOf(DeliveryProofRequiredException.class);
        assertThatThrownBy(() -> parcel.changeStatus(ParcelStatus.DELIVERED, "courier-1", ActorRole.COURIER, null, null,
                new ProofOfDelivery(ProofOfDelivery.Type.NAME, " ", null), null, T1))
                .isInstanceOf(DeliveryProofRequiredException.class)
                .hasMessageContaining("name");
        assertThatThrownBy(() -> parcel.changeStatus(ParcelStatus.DELIVERED, "courier-1", ActorRole.COURIER, null, null,
                new ProofOfDelivery(ProofOfDelivery.Type.PHOTO, null, null), null, T1))
                .isInstanceOf(DeliveryProofRequiredException.class)
                .hasMessageContaining("photo");
    }

    @Test
    void aDeliveryWithAPhotoRecordsTheProofAndPosition() {
        GeoPoint door = new GeoPoint(new BigDecimal("7.290572"), new BigDecimal("80.633728"));
        Parcel delivered = outForDelivery().changeStatus(ParcelStatus.DELIVERED, "courier-1", ActorRole.COURIER, null,
                FailureReason.OTHER, new ProofOfDelivery(ProofOfDelivery.Type.PHOTO, null, "proof-1"), door, T1);

        StatusChange last = delivered.history().get(delivered.history().size() - 1);
        assertThat(delivered.status()).isEqualTo(ParcelStatus.DELIVERED);
        assertThat(last.proofId()).isEqualTo("proof-1");
        assertThat(last.position()).isEqualTo(door);
        assertThat(last.failureReason()).as("a reason only belongs to a failure").isNull();
    }

    @Test
    void assigningChangesOnlyTheCourier() {
        Parcel parcel = ParcelFixtures.bookedBy("cust-1");
        Parcel assigned = parcel.assignCourier("courier-2", T1);

        assertThat(assigned.courierId()).isEqualTo("courier-2");
        assertThat(assigned.status()).isEqualTo(ParcelStatus.CREATED);
        assertThat(assigned.history()).isEqualTo(parcel.history());
        assertThatThrownBy(() -> parcel.assignCourier(" ", T1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPositionMustBeOnEarth() {
        assertThatThrownBy(() -> new GeoPoint(new BigDecimal("91"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoPoint(BigDecimal.ZERO, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
