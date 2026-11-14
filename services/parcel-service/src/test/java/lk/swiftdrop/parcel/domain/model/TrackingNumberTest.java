package lk.swiftdrop.parcel.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrackingNumberTest {

    private static final LocalDate BOOKED_ON = LocalDate.of(2026, 10, 20);

    @Test
    void buildsPrefixDateBodyAndCheckCharacter() {
        TrackingNumber number = TrackingNumber.of(BOOKED_ON, "K7Q2M9");

        assertThat(number.value()).startsWith("SD261020K7Q2M9").hasSize(15);
        assertThat(number.value()).matches("^SD[0-9A-Z]{8,20}$");
        assertThat(TrackingNumber.isValid(number.value())).isTrue();
    }

    @Test
    void checkCharacterCatchesAMistypedCharacter() {
        String valid = TrackingNumber.of(BOOKED_ON, "K7Q2M9").value();
        String mistyped = valid.replace("K7Q2M9", "K7Q2M8");

        assertThat(TrackingNumber.isValid(mistyped)).isFalse();
    }

    @Test
    void checkCharacterCatchesTwoSwappedCharacters() {
        String valid = TrackingNumber.of(BOOKED_ON, "K7Q2M9").value();
        String swapped = valid.replace("K7Q2M9", "7KQ2M9");

        assertThat(TrackingNumber.isValid(swapped)).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "SD261020", "XX261020K7Q2M9X", "SD26102OK7Q2M9X", "sd261020k7q2m9x", "SD261020K7Q2M9XX"})
    void rejectsMalformedValues(String candidate) {
        assertThat(TrackingNumber.isValid(candidate)).isFalse();
        assertThatThrownBy(() -> new TrackingNumber(candidate)).isInstanceOf(IllegalArgumentException.class);
    }
}
