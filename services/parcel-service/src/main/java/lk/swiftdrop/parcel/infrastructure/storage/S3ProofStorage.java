package lk.swiftdrop.parcel.infrastructure.storage;

import lk.swiftdrop.parcel.application.port.out.ProofStorage;
import lk.swiftdrop.parcel.infrastructure.config.ProofStorageConfiguration;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class S3ProofStorage implements ProofStorage {

    private static final Logger log = LoggerFactory.getLogger(S3ProofStorage.class);

    private final ProofStorageConfiguration config;
    private final S3Client client;
    private final S3Presigner presigner;

    public S3ProofStorage(ProofStorageConfiguration config) {
        this.config = config;
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(config.accessKey(), config.secretKey()));
        this.client = S3Client.builder().endpointOverride(URI.create(config.endpoint()))
                .region(Region.of(config.region())).credentialsProvider(credentials).forcePathStyle(true).build();
        this.presigner = S3Presigner.builder().endpointOverride(URI.create(config.publicEndpoint()))
                .region(Region.of(config.region())).credentialsProvider(credentials).build();
        try {
            ensureBucket();
        } catch (RuntimeException ex) {
            log.warn("Proof bucket could not be initialised at startup; uploads will fail until object storage is available");
        }
    }

    @Override
    public String objectKey(UUID parcelId, UUID proofId) {
        return "parcels/" + parcelId + "/" + proofId + ".proof";
    }

    @Override
    public Upload uploadUrl(String objectKey, String contentType, long sizeBytes) {
        PutObjectRequest put = PutObjectRequest.builder().bucket(config.proofBucket()).key(objectKey)
                .contentType(contentType).contentLength(sizeBytes).build();
        Instant expiresAt = Instant.now().plus(config.uploadExpiry());
        String url = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(config.uploadExpiry()).putObjectRequest(put).build()).url().toString();
        return new Upload(url, expiresAt);
    }

    @Override
    public Download downloadUrl(String objectKey) {
        GetObjectRequest get = GetObjectRequest.builder().bucket(config.proofBucket()).key(objectKey).build();
        Instant expiresAt = Instant.now().plus(config.downloadExpiry());
        String url = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(config.downloadExpiry()).getObjectRequest(get).build()).url().toString();
        return new Download(url, expiresAt);
    }

    private void ensureBucket() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(config.proofBucket()).build());
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                client.createBucket(CreateBucketRequest.builder().bucket(config.proofBucket()).build());
            } else {
                throw ex;
            }
        }
    }
}
