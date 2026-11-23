package lk.swiftdrop.parcel.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import lk.swiftdrop.parcel.application.port.out.ProofStorage.PresignedUrl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The presigned links against a real RustFS (the image compose runs): the phone uploads straight to
 * the store with the upload link, the proof can be fetched with the download link, and a link cannot
 * be bent to another content type or used after it expires.
 */
@Testcontainers
class S3ProofStorageIT {

    private static final String ACCESS_KEY = "proof-it-access";
    private static final String SECRET_KEY = "proof-it-secret-key";
    private static final byte[] PHOTO = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4, 5, 6};

    @Container
    static final GenericContainer<?> RUSTFS = new GenericContainer<>("rustfs/rustfs:1.0.3")
            .withEnv("RUSTFS_ACCESS_KEY", ACCESS_KEY)
            .withEnv("RUSTFS_SECRET_KEY", SECRET_KEY)
            .withEnv("RUSTFS_ADDRESS", ":9000")
            .withExposedPorts(9000)
            .waitingFor(Wait.forListeningPort());

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static S3ProofStorage storage;

    private static S3ProofStorage storage(Duration uploadExpiry) {
        String endpoint = "http://" + RUSTFS.getHost() + ":" + RUSTFS.getMappedPort(9000);
        S3ProofProperties properties = new S3ProofProperties(endpoint, null, "us-east-1", ACCESS_KEY, SECRET_KEY,
                "swiftdrop-proofs", uploadExpiry, Duration.ofMinutes(5));
        return new S3ProofStorage(properties, Clock.systemUTC());
    }

    @BeforeAll
    static void createBucket() {
        storage = storage(Duration.ofMinutes(10));
        storage.ensureBucket();
        storage.ensureBucket(); // a second start-up finds the bucket and leaves it alone
    }

    private static HttpResponse<byte[]> put(PresignedUrl link, String contentType) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(link.url())).header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(PHOTO)).build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    @Test
    void aPhotoUploadedWithTheUploadLinkComesBackWithTheDownloadLink() throws Exception {
        PresignedUrl upload = storage.presignUpload("proofs/p-1/photo.jpg", "image/jpeg", PHOTO.length);

        assertThat(put(upload, "image/jpeg").statusCode()).isEqualTo(200);

        PresignedUrl download = storage.presignDownload("proofs/p-1/photo.jpg");
        HttpResponse<byte[]> fetched = HTTP.send(HttpRequest.newBuilder(URI.create(download.url())).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(fetched.statusCode()).isEqualTo(200);
        assertThat(fetched.body()).isEqualTo(PHOTO);
    }

    @Test
    void theContentTypeIsPartOfTheSignature() throws Exception {
        PresignedUrl upload = storage.presignUpload("proofs/p-2/photo.jpg", "image/jpeg", PHOTO.length);

        assertThat(put(upload, "text/html").statusCode()).isEqualTo(403);
    }

    @Test
    void anExpiredUploadLinkIsRefused() throws Exception {
        PresignedUrl upload = storage(Duration.ofSeconds(1)).presignUpload("proofs/p-3/photo.jpg", "image/jpeg",
                PHOTO.length);
        Thread.sleep(2_500);

        assertThat(put(upload, "image/jpeg").statusCode()).isEqualTo(403);
    }

    @Test
    void anObjectThatWasNeverUploadedIsNotFound() throws Exception {
        PresignedUrl download = storage.presignDownload("proofs/p-4/missing.jpg");

        HttpResponse<byte[]> fetched = HTTP.send(HttpRequest.newBuilder(URI.create(download.url())).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(fetched.statusCode()).isEqualTo(404);
    }
}
