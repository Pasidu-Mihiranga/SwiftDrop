package lk.swiftdrop.user.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNumberTest {

    @Test
    void localAndInternationalFormsNormaliseToTheSameNumber() {
        assertThat(PhoneNumber.parse("0771234567")).isEqualTo(PhoneNumber.parse("+94771234567"));
        assertThat(PhoneNumber.parse(" 0771234567 ").value()).isEqualTo("+94771234567");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0112345678", "077123456", "07712345678", "+9477123456", "94771234567", "077-1234567"})
    void rejectsNumbersThatAreNotSriLankanMobiles(String input) {
        assertThatThrownBy(() -> PhoneNumber.parse(input)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> PhoneNumber.parse(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PhoneNumber(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theConstructorOnlyAcceptsTheNormalisedForm() {
        assertThatThrownBy(() -> new PhoneNumber("0771234567")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toStringNeverRevealsTheNumber() {
        assertThat(PhoneNumber.parse("0771234567").toString()).doesNotContain("77123").endsWith("67]");
    }
}
