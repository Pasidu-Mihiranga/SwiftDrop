package lk.swiftdrop.parcel.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;

@Configuration @ConfigurationProperties(prefix = "swiftdrop.s3")
public class S3ProofConfiguration {
    private String endpoint; private String region; private String accessKey; private String secretKey; private String proofBucket;
    private Duration uploadExpiry = Duration.ofMinutes(10); private Duration downloadExpiry = Duration.ofMinutes(5);
    public String getEndpoint(){return endpoint;} public void setEndpoint(String v){endpoint=v;}
    public String getRegion(){return region;} public void setRegion(String v){region=v;}
    public String getAccessKey(){return accessKey;} public void setAccessKey(String v){accessKey=v;}
    public String getSecretKey(){return secretKey;} public void setSecretKey(String v){secretKey=v;}
    public String getProofBucket(){return proofBucket;} public void setProofBucket(String v){proofBucket=v;}
    public Duration getUploadExpiry(){return uploadExpiry;} public void setUploadExpiry(Duration v){uploadExpiry=v;}
    public Duration getDownloadExpiry(){return downloadExpiry;} public void setDownloadExpiry(Duration v){downloadExpiry=v;}
}
