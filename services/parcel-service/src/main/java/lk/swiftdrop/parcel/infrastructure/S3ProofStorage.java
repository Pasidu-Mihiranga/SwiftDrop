package lk.swiftdrop.parcel.infrastructure;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class S3ProofStorage {
    private static final Logger log = LoggerFactory.getLogger(S3ProofStorage.class);
    private final S3ProofConfiguration config; private final S3Client client; private final S3Presigner presigner;
    public S3ProofStorage(S3ProofConfiguration config) {
        this.config=config; var credentials=StaticCredentialsProvider.create(AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey()));
        this.client=S3Client.builder().endpointOverride(URI.create(config.getEndpoint())).region(Region.of(config.getRegion())).credentialsProvider(credentials).forcePathStyle(true).build();
        this.presigner=S3Presigner.builder().endpointOverride(URI.create(config.getEndpoint())).region(Region.of(config.getRegion())).credentialsProvider(credentials).build();
        try { ensureBucket(); } catch (RuntimeException ex) { log.warn("Proof bucket could not be initialised at startup; uploads will fail until S3 is available"); }
    }
    private void ensureBucket() {
        try { client.headBucket(HeadBucketRequest.builder().bucket(config.getProofBucket()).build()); }
        catch (S3Exception e) { if (e.statusCode() == 404) client.createBucket(CreateBucketRequest.builder().bucket(config.getProofBucket()).build()); else throw e; }
    }
    public String newObjectKey(UUID parcelId, UUID proofId) { return "parcels/" + parcelId + "/" + proofId + ".bin"; }
    public String uploadUrl(String key, String contentType) {
        var req=PutObjectRequest.builder().bucket(config.getProofBucket()).key(key).contentType(contentType).build();
        return presigner.presignPutObject(PutObjectPresignRequest.builder().signatureDuration(config.getUploadExpiry()).putObjectRequest(req).build()).url().toString();
    }
    public String downloadUrl(String key) {
        var req=GetObjectRequest.builder().bucket(config.getProofBucket()).key(key).build();
        return presigner.presignGetObject(GetObjectPresignRequest.builder().signatureDuration(config.getDownloadExpiry()).getObjectRequest(req).build()).url().toString();
    }
}
