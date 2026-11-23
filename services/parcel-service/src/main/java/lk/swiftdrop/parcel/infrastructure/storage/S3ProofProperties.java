package lk.swiftdrop.parcel.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Object storage for proof of delivery (RustFS locally, any S3 in production).
 *
 * @param endpoint       where the service reaches the store (inside Docker: object-storage:9000)
 * @param publicEndpoint where browsers reach it; presigned links are signed for this host, since the
 *                       courier's phone uploads to it directly. Defaults to {@code endpoint}.
 */
@ConfigurationProperties("swiftdrop.s3")
public record S3ProofProperties(
        @DefaultValue("http://localhost:9010") String endpoint,
        String publicEndpoint,
        @DefaultValue("us-east-1") String region,
        String accessKey,
        String secretKey,
        @DefaultValue("swiftdrop-proofs") String proofBucket,
        @DefaultValue("PT10M") Duration uploadExpiry,
        @DefaultValue("PT5M") Duration downloadExpiry) {

    public String browserEndpoint() {
        return publicEndpoint == null || publicEndpoint.isBlank() ? endpoint : publicEndpoint;
    }
}
