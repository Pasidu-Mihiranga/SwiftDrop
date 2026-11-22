package lk.swiftdrop.merchant.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lk.swiftdrop.merchant.domain.model.PickupAddress;

/** The shared Address schema in contracts/openapi/common.v1.yaml, without coordinates. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PickupAddressDto(
        @NotBlank @Size(max = 120) String line1,
        @Size(max = 120) String line2,
        @NotBlank @Size(max = 60) String city,
        @NotBlank @Size(max = 40) String district,
        @Pattern(regexp = "^[0-9]{5}$", message = "must be a 5-digit postal code") String postalCode) {

    public PickupAddress toDomain() {
        return new PickupAddress(line1.strip(), blankToNull(line2), city.strip(), district.strip(),
                blankToNull(postalCode));
    }

    public static PickupAddressDto from(PickupAddress address) {
        return new PickupAddressDto(address.line1(), address.line2(), address.city(), address.district(),
                address.postalCode());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
