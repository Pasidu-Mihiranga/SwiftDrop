package lk.swiftdrop.user.domain.model;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourierTest {

    private static final UUID ZONE = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private static CourierProfile profile(boolean active) {
        return new CourierProfile("c-1", VehicleType.BIKE, ZONE, 25, active, null);
    }

    @Test
    void onlyAnEnabledCourierWithActiveDetailsCanBeAssigned() {
        assertThat(new Courier("c-1", "Chamath", "c@x.lk", true, Optional.of(profile(true))).assignable()).isTrue();
        assertThat(new Courier("c-1", "Chamath", "c@x.lk", false, Optional.of(profile(true))).assignable()).isFalse();
        assertThat(new Courier("c-1", "Chamath", "c@x.lk", true, Optional.of(profile(false))).assignable()).isFalse();
        assertThat(new Courier("c-1", "Chamath", "c@x.lk", true, Optional.empty()).assignable()).isFalse();
        assertThat(new Courier("c-1", "Chamath", "c@x.lk", true, null).profile()).isEmpty();
    }

    @Test
    void aProfileNeedsAVehicleAZoneAndASensibleCapacity() {
        assertThat(new CourierProfile("c-1", VehicleType.VAN, ZONE, 200, true, null).maxDailyCapacity()).isEqualTo(200);
        assertThatThrownBy(() -> new CourierProfile(" ", VehicleType.BIKE, ZONE, 10, true, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CourierProfile("c-1", null, ZONE, 10, true, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CourierProfile("c-1", VehicleType.BIKE, null, 10, true, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CourierProfile("c-1", VehicleType.BIKE, ZONE, 0, true, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CourierProfile("c-1", VehicleType.BIKE, ZONE, 201, true, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
