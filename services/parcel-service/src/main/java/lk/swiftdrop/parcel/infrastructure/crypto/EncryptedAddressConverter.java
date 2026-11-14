package lk.swiftdrop.parcel.infrastructure.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lk.swiftdrop.parcel.domain.model.Address;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Stores an address as one encrypted JSON value, so no line of it is readable in the database. */
@Component
@Converter
public class EncryptedAddressConverter implements AttributeConverter<Address, String> {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final PiiCipher cipher;

    public EncryptedAddressConverter(PiiCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(Address address) {
        return address == null ? null : cipher.encrypt(JSON.writeValueAsString(address));
    }

    @Override
    public Address convertToEntityAttribute(String stored) {
        return stored == null ? null : JSON.readValue(cipher.decrypt(stored), Address.class);
    }
}
