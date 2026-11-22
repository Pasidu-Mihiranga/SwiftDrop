package lk.swiftdrop.parcel.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "swiftdrop.s3")
public record ProofStorageConfiguration(String endpoint, String publicEndpoint, String region, String accessKey, String secretKey,
                                        String proofBucket, Duration uploadExpiry, Duration downloadExpiry) {

    public ProofStorageConfiguration {
        endpoint = endpoint == null || endpoint.isBlank() ? "http://localhost:9010" : endpoint;
        publicEndpoint = publicEndpoint == null || publicEndpoint.isBlank() ? endpoint : publicEndpoint;
        region = region == null || region.isBlank() ? "us-east-1" : region;
        proofBucket = proofBucket == null || proofBucket.isBlank() ? "swiftdrop-proofs" : proofBucket;
        uploadExpiry = uploadExpiry == null ? Duration.ofMinutes(10) : uploadExpiry;
        downloadExpiry = downloadExpiry == null ? Duration.ofMinutes(5) : downloadExpiry;
    }
}
