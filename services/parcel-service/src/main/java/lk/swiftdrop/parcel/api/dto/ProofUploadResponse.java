package lk.swiftdrop.parcel.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ProofUploadResponse(UUID proofId, String uploadUrl, Instant expiresAt) {
}
