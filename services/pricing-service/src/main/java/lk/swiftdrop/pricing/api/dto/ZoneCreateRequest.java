package lk.swiftdrop.pricing.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ZoneCreateRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Z][A-Z0-9-]{1,15}$",
                message = "must be 2 to 16 upper-case letters, digits or hyphens")
        String code,

        @NotBlank @Size(max = 128) String name,

        Boolean active) {

    /** Zones are usable unless the caller says otherwise. */
    public boolean activeOrDefault() {
        return active == null || active;
    }
}
