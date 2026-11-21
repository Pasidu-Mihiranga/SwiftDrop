package lk.swiftdrop.parcel.infrastructure.storage;

import lk.swiftdrop.parcel.application.port.out.ProofStorage.PresignedUrl;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** Presigning is local signing, so these run without object storage. */
class S3ProofStorageTest {

    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");

    private static S3ProofStorage storage(String publicEndpoint) {
        S3ProofProperties properties = new S3ProofProperties("http://object-storage:9000", publicEndpoint, "us-east-1",
                "access", "secret", "swiftdrop-proofs", Duration.ofMinutes(10), Duration.ofMinutes(5));
        return new S3ProofStorage(properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void uploadLinksArePathStyleSignedForTheBrowserHostAndExpire() {
        PresignedUrl upload = storage("http://localhost:9010").presignUpload("proofs/p/1.jpg", "image/jpeg", 1000);

        assertThat(upload.url()).startsWith("http://localhost:9010/swiftdrop-proofs/proofs/p/1.jpg?")
                .contains("X-Amz-Signature=").contains("X-Amz-Expires=600");
        assertThat(upload.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
    }

    @Test
    void downloadLinksExpireSoonerAndDefaultToTheServiceEndpoint() {
        PresignedUrl download = storage(null).presignDownload("proofs/p/1.jpg");

        assertThat(download.url()).startsWith("http://object-storage:9000/swiftdrop-proofs/proofs/p/1.jpg?")
                .contains("X-Amz-Expires=300");
        assertThat(download.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
    }

    @Test
    void anUnreachableStoreDoesNotStopStartUp() {
        S3ProofProperties properties = new S3ProofProperties("http://127.0.0.1:1", null, "us-east-1", null, null,
                "swiftdrop-proofs", Duration.ofMinutes(10), Duration.ofMinutes(5));

        assertThatCode(() -> new S3ProofStorage(properties, Clock.systemUTC()).ensureBucket()).doesNotThrowAnyException();
    }
}
