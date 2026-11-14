package lk.swiftdrop.parcel.domain;

import lk.swiftdrop.parcel.domain.state.ParcelStates;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParcelStateTest {
    @Test void followsTheForwardLifecycle() {
        assertThat(ParcelStates.of(ParcelStatus.CREATED).transitionTo(ParcelStatus.PICKED_UP).status()).isEqualTo(ParcelStatus.PICKED_UP);
        assertThat(ParcelStates.of(ParcelStatus.PICKED_UP).transitionTo(ParcelStatus.IN_TRANSIT).status()).isEqualTo(ParcelStatus.IN_TRANSIT);
        assertThat(ParcelStates.of(ParcelStatus.IN_TRANSIT).transitionTo(ParcelStatus.OUT_FOR_DELIVERY).status()).isEqualTo(ParcelStatus.OUT_FOR_DELIVERY);
        assertThat(ParcelStates.of(ParcelStatus.OUT_FOR_DELIVERY).transitionTo(ParcelStatus.DELIVERED).status()).isEqualTo(ParcelStatus.DELIVERED);
    }
    @Test void supportsCancellationAndReattemptOnlyWhereSpecified() {
        assertThat(ParcelStates.of(ParcelStatus.CREATED).transitionTo(ParcelStatus.CANCELLED).status()).isEqualTo(ParcelStatus.CANCELLED);
        assertThat(ParcelStates.of(ParcelStatus.FAILED).transitionTo(ParcelStatus.OUT_FOR_DELIVERY).status()).isEqualTo(ParcelStatus.OUT_FOR_DELIVERY);
        assertThatThrownBy(() -> ParcelStates.of(ParcelStatus.DELIVERED).transitionTo(ParcelStatus.OUT_FOR_DELIVERY)).hasMessageContaining("cannot move");
    }
}
