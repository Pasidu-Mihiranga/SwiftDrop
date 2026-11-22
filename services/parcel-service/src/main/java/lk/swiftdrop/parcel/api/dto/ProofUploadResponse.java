package lk.swiftdrop.parcel.api.dto;

import java.time.Instant;
import java.util.UUID;

/** Where to PUT the file (with the same Content-Type), and the id to send with the status update. */
public record ProofUploadResponse(UUID proofId, String uploadUrl, Instant expiresAt) {
}
