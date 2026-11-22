package lk.swiftdrop.parcel.domain.model;

import lk.swiftdrop.events.common.Types.ParcelStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParcelStatusTransitionsTest {

    @Test
    void rejectsSkippingRequiredCourierStates() {
        assertThatThrownBy(() -> ParcelStatusTransitions.validate(ParcelStatus.CREATED, ParcelStatus.DELIVERED))
                .hasMessageContaining("CREATED");
        assertThatThrownBy(() -> ParcelStatusTransitions.validate(ParcelStatus.DELIVERED, ParcelStatus.OUT_FOR_DELIVERY))
                .hasMessageContaining("DELIVERED");
    }

    @Test
    void permitsFailureReattempt() {
        ParcelStatusTransitions.validate(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.FAILED);
        ParcelStatusTransitions.validate(ParcelStatus.FAILED, ParcelStatus.OUT_FOR_DELIVERY);
    }
}
