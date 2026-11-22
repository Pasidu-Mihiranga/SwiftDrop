package lk.swiftdrop.parcel.application.port.out;

import java.time.Instant;
import java.util.UUID;

/** Object-storage port for private delivery-proof objects. */
public interface ProofStorage {

    String objectKey(UUID parcelId, UUID proofId);

    Upload uploadUrl(String objectKey, String contentType, long sizeBytes);

    Download downloadUrl(String objectKey);

    record Upload(String url, Instant expiresAt) { }

    record Download(String url, Instant expiresAt) { }
}
