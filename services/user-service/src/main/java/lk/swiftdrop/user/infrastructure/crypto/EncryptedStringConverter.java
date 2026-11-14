package lk.swiftdrop.user.infrastructure.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * JPA converter for columns that hold personal data. Entities keep the plain value in memory; the
 * database only ever sees the ciphertext. Hibernate obtains this converter from Spring, so the key
 * is injected rather than read from a static field.
 */
@Component
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
