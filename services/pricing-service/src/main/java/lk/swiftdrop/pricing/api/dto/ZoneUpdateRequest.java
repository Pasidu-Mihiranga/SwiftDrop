package lk.swiftdrop.pricing.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The code is deliberately absent: a zone's code is immutable. */
public record ZoneUpdateRequest(
        @NotBlank @Size(max = 128) String name,
        @NotNull Boolean active) {
}
