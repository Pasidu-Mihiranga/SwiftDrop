package lk.swiftdrop.merchant.infrastructure.crypto;

import jakarta.persistence.AttributeConverter;
import lk.swiftdrop.common.pii.PiiCipher;
import jakarta.persistence.Converter;
import lk.swiftdrop.merchant.domain.model.PickupAddress;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Stores the pickup address as one encrypted JSON value, so no line of it is readable in the database. */
@Component
@Converter
public class EncryptedPickupAddressConverter implements AttributeConverter<PickupAddress, String> {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final PiiCipher cipher;

    public EncryptedPickupAddressConverter(PiiCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(PickupAddress address) {
        return address == null ? null : cipher.encrypt(JSON.writeValueAsString(address));
    }

    @Override
    public PickupAddress convertToEntityAttribute(String stored) {
        return stored == null ? null : JSON.readValue(cipher.decrypt(stored), PickupAddress.class);
    }
}
