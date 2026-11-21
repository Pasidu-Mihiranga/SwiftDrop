package lk.swiftdrop.parcel.infrastructure.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Wires the S3 proof storage from {@code swiftdrop.s3.*}. */
@Configuration
@EnableConfigurationProperties(S3ProofProperties.class)
public class ProofStorageConfiguration {

    @Bean
    S3ProofStorage proofStorage(S3ProofProperties properties, Clock clock) {
        return new S3ProofStorage(properties, clock);
    }
}
