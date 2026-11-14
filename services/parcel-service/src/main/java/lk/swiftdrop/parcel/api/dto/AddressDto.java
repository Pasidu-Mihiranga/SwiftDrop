package lk.swiftdrop.parcel.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lk.swiftdrop.parcel.domain.model.Address;

import java.math.BigDecimal;

/** The shared Address schema in contracts/openapi/common.v1.yaml. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AddressDto(
        @NotBlank @Size(max = 120) String line1,
        @Size(max = 120) String line2,
        @NotBlank @Size(max = 60) String city,
        @NotBlank @Size(max = 40) String district,
        @Pattern(regexp = "^[0-9]{5}$", message = "must be a 5-digit postal code") String postalCode,
        @DecimalMin("-90") @DecimalMax("90") BigDecimal lat,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal lng) {

    public Address toDomain() {
        return new Address(line1.strip(), blankToNull(line2), city.strip(), district.strip(), postalCode, lat, lng);
    }

    public static AddressDto from(Address address) {
        return new AddressDto(address.line1(), address.line2(), address.city(), address.district(),
                address.postalCode(), address.lat(), address.lng());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
