package lk.swiftdrop.parcel.infrastructure.storage;

import lk.swiftdrop.parcel.application.port.out.ProofStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Clock;

/**
 * S3 adapter for proof files (AWS SDK v2; RustFS speaks the same API). Path-style addressing,
 * because RustFS and MinIO serve buckets under the path. The bucket is private; presigned links
 * expire after minutes. It is created on start-up when missing; if the store is not reachable yet,
 * start-up continues and uploads fail until it is.
 */
public class S3ProofStorage implements ProofStorage {

    private static final Logger log = LoggerFactory.getLogger(S3ProofStorage.class);

    private final S3ProofProperties properties;
    private final S3Client client;
    private final S3Presigner presigner;
    private final Clock clock;

    public S3ProofStorage(S3ProofProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(blankToDefault(properties.accessKey()), blankToDefault(properties.secretKey())));
        S3Configuration pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        this.client = S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials)
                .serviceConfiguration(pathStyle)
                .build();
        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(properties.browserEndpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials)
                .serviceConfiguration(pathStyle)
                .build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureBucket() {
        String bucket = properties.proofBucket();
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException missing) {
            createBucket(bucket);
        } catch (S3Exception failure) {
            if (failure.statusCode() == 404) {
                createBucket(bucket);
            } else {
                log.warn("Proof bucket {} could not be checked (HTTP {}); uploads fail until it can",
                        bucket, failure.statusCode());
            }
        } catch (RuntimeException unreachable) {
            log.warn("Object storage is not reachable at start-up; proof uploads fail until it is: {}",
                    unreachable.getClass().getSimpleName());
        }
    }

    private void createBucket(String bucket) {
        try {
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("Created proof bucket {}", bucket);
        } catch (RuntimeException failure) {
            log.warn("Proof bucket {} could not be created: {}", bucket, failure.getClass().getSimpleName());
        }
    }

    /** The size is checked before a link is issued; it is not signed, so the phone's own Content-Length is accepted. */
    @Override
    public PresignedUrl presignUpload(String objectKey, String contentType, long sizeBytes) {
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(properties.proofBucket()).key(objectKey).contentType(contentType).build();
        String url = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(properties.uploadExpiry()).putObjectRequest(put).build()).url().toString();
        return new PresignedUrl(url, clock.instant().plus(properties.uploadExpiry()));
    }

    @Override
    public PresignedUrl presignDownload(String objectKey) {
        GetObjectRequest get = GetObjectRequest.builder().bucket(properties.proofBucket()).key(objectKey).build();
        String url = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(properties.downloadExpiry()).getObjectRequest(get).build()).url().toString();
        return new PresignedUrl(url, clock.instant().plus(properties.downloadExpiry()));
    }

    /** The SDK refuses empty credentials; a store without them fails at request time instead of start-up. */
    private static String blankToDefault(String value) {
        return value == null || value.isBlank() ? "unset" : value;
    }
}
