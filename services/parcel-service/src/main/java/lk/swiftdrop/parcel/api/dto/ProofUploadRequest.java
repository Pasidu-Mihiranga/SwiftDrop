package lk.swiftdrop.parcel.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** The file the courier is about to upload: its type (JPEG or PNG) and size in bytes. */
public record ProofUploadRequest(@NotBlank String contentType, @Positive long sizeBytes) {
}
