package lk.swiftdrop.parcel.application.port.out;

import java.time.Instant;

/**
 * Object storage for proof-of-delivery photos and signatures. Files never pass through the
 * service: the courier's phone uploads straight to a presigned URL, and viewers get a short-lived
 * presigned download link.
 */
public interface ProofStorage {

    /** A presigned link and when it stops working. */
    record PresignedUrl(String url, Instant expiresAt) { }

    PresignedUrl presignUpload(String objectKey, String contentType, long sizeBytes);

    PresignedUrl presignDownload(String objectKey);
}
