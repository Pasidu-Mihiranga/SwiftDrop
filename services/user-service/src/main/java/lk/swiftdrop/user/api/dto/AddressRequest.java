package lk.swiftdrop.user.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * One address-book entry ({@code common.v1.yaml Address} plus a label). PUT replaces every field.
 *
 * @param isDefault {@code true} makes this the default; {@code false} or omitted leaves the default where it is
 */
public record AddressRequest(
        @NotBlank @Size(max = 40) String label,
        @NotBlank @Size(max = 120) String line1,
        @Size(max = 120) String line2,
        @NotBlank @Size(max = 60) String city,
        @NotBlank @Size(max = 40) String district,
        @Pattern(regexp = "^[0-9]{5}$", message = "must be five digits") String postalCode,
        @DecimalMin("-90") @DecimalMax("90") Double lat,
        @DecimalMin("-180") @DecimalMax("180") Double lng,
        Boolean isDefault) {

    public boolean makeDefault() {
        return Boolean.TRUE.equals(isDefault);
    }
}
