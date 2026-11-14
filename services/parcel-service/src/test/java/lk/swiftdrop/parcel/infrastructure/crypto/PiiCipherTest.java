package lk.swiftdrop.parcel.infrastructure.crypto;

import lk.swiftdrop.parcel.ParcelFixtures;
import lk.swiftdrop.parcel.domain.model.Address;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PiiCipherTest {

    private static final String TEST_KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private final PiiCipher cipher = new PiiCipher(TEST_KEY);

    @Test
    void decryptsWhatItEncrypted() {
        String stored = cipher.encrypt("0712345678");

        assertThat(stored).startsWith("v1:").doesNotContain("0712345678");
        assertThat(cipher.decrypt(stored)).isEqualTo("0712345678");
    }

    @Test
    void theSameValueNeverProducesTheSameCiphertext() {
        assertThat(cipher.encrypt("0712345678")).isNotEqualTo(cipher.encrypt("0712345678"));
    }

    @Test
    void aTamperedValueFailsInsteadOfReturningGarbage() {
        String stored = cipher.encrypt("0712345678");
        char last = stored.charAt(stored.length() - 2);
        String tampered = stored.substring(0, stored.length() - 2) + (last == 'A' ? 'B' : 'A') + stored.charAt(stored.length() - 1);

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt("0712345678")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt("v1:AAAA")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aValueEncryptedWithAnotherKeyCannotBeRead() {
        String other = Base64.getEncoder().encodeToString(new byte[32]);

        assertThatThrownBy(() -> new PiiCipher(other).decrypt(cipher.encrypt("0712345678")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesAMissingOrMalformedKey() {
        assertThatThrownBy(() -> new PiiCipher(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PiiCipher("")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PiiCipher("not base64 !!")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PiiCipher(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void convertersEncryptOnTheWayInAndDecryptOnTheWayOut() {
        EncryptedStringConverter strings = new EncryptedStringConverter(cipher);
        EncryptedAddressConverter addresses = new EncryptedAddressConverter(cipher);

        String storedAddress = addresses.convertToDatabaseColumn(ParcelFixtures.DROPOFF);

        assertThat(storedAddress).doesNotContain("Peradeniya").doesNotContain("Kandy");
        assertThat(addresses.convertToEntityAttribute(storedAddress)).isEqualTo(ParcelFixtures.DROPOFF);
        assertThat(strings.convertToEntityAttribute(strings.convertToDatabaseColumn("0712345678"))).isEqualTo("0712345678");
        assertThat(strings.convertToDatabaseColumn(null)).isNull();
        assertThat(strings.convertToEntityAttribute(null)).isNull();
        assertThat(addresses.convertToDatabaseColumn(null)).isNull();
        assertThat(addresses.convertToEntityAttribute((String) null)).isNull();
        assertThat(addresses.convertToEntityAttribute(addresses.convertToDatabaseColumn(
                new Address("1 Main Street", null, "Galle", "Galle", null, null, null)))).isNotNull();
    }
}
