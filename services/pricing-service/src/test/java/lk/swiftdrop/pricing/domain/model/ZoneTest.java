package lk.swiftdrop.pricing.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZoneTest {

    private static final ZoneCode CENTRAL = ZoneCode.of("CP");

    @Test
    void sortsDistrictsAndKeepsThemImmutable() {
        Zone zone = new Zone(UUID.randomUUID(), CENTRAL, "Central", true,
                List.of("Nuwara Eliya", "Kandy", "Matale"));

        assertThat(zone.districts()).containsExactly("Kandy", "Matale", "Nuwara Eliya");
        assertThatThrownBy(() -> zone.districts().add("Galle"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aNewZoneHasNoIdentityAndNoCoverage() {
        Zone zone = Zone.newZone(CENTRAL, "Central", true);

        assertThat(zone.id()).isNull();
        assertThat(zone.hasDistricts()).isFalse();
    }

    @Test
    void updatingKeepsIdentityAndCoverage() {
        UUID id = UUID.randomUUID();
        Zone zone = new Zone(id, CENTRAL, "Central", true, List.of("Kandy"));

        Zone renamed = zone.with("Central Province", false);

        assertThat(renamed.id()).isEqualTo(id);
        assertThat(renamed.code()).isEqualTo(CENTRAL);
        assertThat(renamed.name()).isEqualTo("Central Province");
        assertThat(renamed.active()).isFalse();
        assertThat(renamed.districts()).containsExactly("Kandy");
        assertThat(renamed.hasDistricts()).isTrue();
    }

    @Test
    void stripsTheNameAndTreatsAMissingDistrictListAsEmpty() {
        Zone zone = new Zone(null, CENTRAL, "  Central  ", true, null);

        assertThat(zone.name()).isEqualTo("Central");
        assertThat(zone.districts()).isEmpty();
    }

    @Test
    void requiresACodeAndANonBlankName() {
        assertThatThrownBy(() -> new Zone(null, null, "Central", true, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Zone(null, CENTRAL, "  ", true, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
