package lk.swiftdrop.merchant.infrastructure.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class PiiCipherTest {

    private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String OTHER_KEY = "ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA=";

    private final PiiCipher cipher = new PiiCipher(KEY);

    @Test
    void aValueSurvivesTheRoundTripButIsNeverStoredInClear() {
        String stored = cipher.encrypt("+94771234567");

        assertThat(stored).startsWith("v1:").doesNotContain("94771234567");
        assertThat(cipher.decrypt(stored)).isEqualTo("+94771234567");
    }

    @Test
    void theSameValueEncryptsDifferentlyEachTime() {
        assertThat(cipher.encrypt("same")).isNotEqualTo(cipher.encrypt("same"));
    }

    @Test
    void aWrongKeyOrATamperedValueFailsWithoutRevealingIt() {
        String stored = cipher.encrypt("+94771234567");
        String tampered = stored.substring(0, stored.length() - 4) + "AAAA";

        assertThatIllegalStateException().isThrownBy(() -> new PiiCipher(OTHER_KEY).decrypt(stored))
                .withMessageNotContaining("9477");
        assertThatIllegalStateException().isThrownBy(() -> cipher.decrypt(tampered));
        assertThatIllegalStateException().isThrownBy(() -> cipher.decrypt("plain text"));
    }

    @Test
    void theServiceRefusesToStartWithoutAValidKey() {
        assertThatIllegalStateException().isThrownBy(() -> new PiiCipher(null));
        assertThatIllegalStateException().isThrownBy(() -> new PiiCipher("not base64 !!"));
        assertThatIllegalStateException().isThrownBy(() -> new PiiCipher("c2hvcnQ="));
    }
}
