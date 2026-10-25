package lk.swiftdrop.pricing.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DistrictAssignmentRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9-]{1,15}$",
                message = "must be 2 to 16 upper-case letters, digits or hyphens")
        String zoneCode) {
}
