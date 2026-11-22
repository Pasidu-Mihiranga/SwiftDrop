package lk.swiftdrop.parcel.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record ProofUploadRequest(
        @NotBlank @Pattern(regexp = "image/(jpeg|png)") String contentType,
        @Positive @Max(5 * 1024 * 1024L) long sizeBytes) {
}
