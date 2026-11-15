package lk.swiftdrop.pricing.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DistrictAssignmentTest {

    @Test
    void stripsTheDistrictName() {
        DistrictAssignment assignment = new DistrictAssignment("  Kandy ", ZoneCode.of("CP"), "Central");

        assertThat(assignment.district()).isEqualTo("Kandy");
    }

    @Test
    void requiresADistrictAndAZone() {
        assertThatThrownBy(() -> new DistrictAssignment(" ", ZoneCode.of("CP"), "Central"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DistrictAssignment("Kandy", null, "Central"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
