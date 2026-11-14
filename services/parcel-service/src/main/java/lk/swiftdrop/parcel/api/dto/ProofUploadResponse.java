package lk.swiftdrop.parcel.api.dto;
import java.util.UUID;
public record ProofUploadResponse(UUID proofId, String uploadUrl, String contentType, long maxBytes) { }
