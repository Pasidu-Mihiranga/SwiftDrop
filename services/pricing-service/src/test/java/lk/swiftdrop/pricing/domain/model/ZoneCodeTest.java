package lk.swiftdrop.pricing.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZoneCodeTest {

    @Test
    void normalisesCaseAndSurroundingSpace() {
        assertThat(ZoneCode.of("  nwp ").value()).isEqualTo("NWP");
    }

    @ParameterizedTest
    @ValueSource(strings = {"W", "1WP", "WP!", "W P", "", " ", "ABCDEFGHIJKLMNOPQ"})
    void rejectsCodesThatDoNotMatchTheContract(String candidate) {
        assertThatThrownBy(() -> ZoneCode.of(candidate)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> ZoneCode.of(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sortsAlphabeticallyAndPrintsAsItsValue() {
        assertThat(ZoneCode.of("CP")).isLessThan(ZoneCode.of("WP"));
        assertThat(ZoneCode.of("CP")).hasToString("CP");
    }

    @Test
    void equalCodesAreInterchangeable() {
        assertThat(ZoneCode.of("wp")).isEqualTo(ZoneCode.of("WP"));
    }
}
