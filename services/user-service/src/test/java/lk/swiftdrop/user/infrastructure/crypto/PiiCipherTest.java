package lk.swiftdrop.user.infrastructure.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PiiCipherTest {

    /** 32 bytes, base64 encoded: the same shape {@code pnpm env:init} generates. */
    static final String TEST_KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    private static final String OTHER_KEY = Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes());

    private final PiiCipher cipher = new PiiCipher(TEST_KEY);

    @Test
    void decryptsWhatItEncrypted() {
        assertThat(cipher.decrypt(cipher.encrypt("+94771234567"))).isEqualTo("+94771234567");
        assertThat(cipher.decrypt(cipher.encrypt("No. 12, Temple Road, Kandy"))).isEqualTo("No. 12, Temple Road, Kandy");
    }

    @Test
    void theStoredValueRevealsNothingAndDiffersEveryTime() {
        String first = cipher.encrypt("+94771234567");
        String second = cipher.encrypt("+94771234567");

        assertThat(first).startsWith("v1:").doesNotContain("771234567");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void aTamperedValueFailsInsteadOfReturningGarbage() {
        String stored = cipher.encrypt("+94771234567");
        byte[] bytes = Base64.getDecoder().decode(stored.substring(3));
        bytes[bytes.length - 1] ^= 1;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aDifferentKeyCannotReadTheValue() {
        String stored = cipher.encrypt("+94771234567");

        assertThatThrownBy(() -> new PiiCipher(OTHER_KEY).decrypt(stored)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsValuesInAnUnknownFormat() {
        assertThatThrownBy(() -> cipher.decrypt("+94771234567")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt("v1:AAAA")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt("v1:not base64!")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesToStartWithoutAProperKey() {
        assertThatThrownBy(() -> new PiiCipher(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PiiCipher("")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PiiCipher("__generate_base64_32__")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PiiCipher(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32-byte");
    }

    @Test
    void theJpaConverterPassesNullsThrough() {
        EncryptedStringConverter converter = new EncryptedStringConverter(cipher);

        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn("+94771234567"))).isEqualTo("+94771234567");
    }
}
