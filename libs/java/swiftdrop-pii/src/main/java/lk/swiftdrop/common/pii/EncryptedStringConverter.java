package lk.swiftdrop.common.pii;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA converter for columns that hold personal data. Entities keep the plain value in memory; the
 * database only ever sees the ciphertext. {@link PiiAutoConfiguration} registers it as a Spring bean,
 * so Hibernate obtains it from Spring with the key injected rather than read from a static field.
 * Services with structured values (an address stored as one encrypted JSON value) write their own
 * converter on top of {@link PiiCipher}.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final PiiCipher cipher;

    public EncryptedStringConverter(PiiCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(String plainText) {
        return plainText == null ? null : cipher.encrypt(plainText);
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        return stored == null ? null : cipher.decrypt(stored);
    }
}
