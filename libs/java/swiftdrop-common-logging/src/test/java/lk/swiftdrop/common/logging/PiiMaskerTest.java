package lk.swiftdrop.common.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskerTest {

    @Test
    void masksEmailKeepingFirstCharacterAndDomain() {
        assertThat(PiiMasker.mask("Sent receipt to nimali.perera@gmail.com"))
                .isEqualTo("Sent receipt to n***@gmail.com");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Call 0771234567 now     | Call 07******67 now",
            "Call +94771234567 now   | Call +94*******67 now",
            "Call +94 77 123 4567    | Call +94 ** *** **67"
    })
    void masksPhoneNumbers(String input, String expected) {
        assertThat(PiiMasker.mask(input)).isEqualTo(expected);
    }

    @Test
    void masksCredentialsInJsonAndQueryStrings() {
        assertThat(PiiMasker.mask("{\"username\":\"a\",\"password\":\"S3cret!\"}"))
                .isEqualTo("{\"username\":\"a\",\"password\":\"***\"}");
        assertThat(PiiMasker.mask("grant_type=client_credentials&client_secret=abc123&x=1"))
                .isEqualTo("grant_type=client_credentials&client_secret=***&x=1");
    }

    @Test
    void masksBearerTokens() {
        assertThat(PiiMasker.mask("Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.payload.sig"))
                .isEqualTo("Authorization: Bearer ***");
    }

    @Test
    void leavesDatesIdsAndTrackingNumbersAlone() {
        String text = "Parcel SD261020ABC123 created 2026-10-20T10:15:30Z id=7f9c2ba4-e88f-4c2b-9d3a-1b2c3d4e5f60 at 1729419330000";
        assertThat(PiiMasker.mask(text)).isEqualTo(text);
    }

    @Test
    void isNullSafe() {
        assertThat(PiiMasker.mask(null)).isNull();
        assertThat(PiiMasker.mask("")).isEmpty();
    }
}
