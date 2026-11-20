package lk.swiftdrop.merchant.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiCredentialTest {

    private static final Instant ISSUED = Instant.parse("2026-11-19T08:00:00Z");
    private static final Instant LATER = Instant.parse("2026-11-20T09:30:00Z");

    @Test
    void rotatingRecordsWhenAndKeepsEverythingElse() {
        ApiCredential issued = ApiCredential.issue(UUID.randomUUID(), "merchant-x", ISSUED);

        ApiCredential rotated = issued.rotate(LATER);

        assertThat(rotated.rotatedAt()).isEqualTo(LATER);
        assertThat(rotated).usingRecursiveComparison().ignoringFields("rotatedAt").isEqualTo(issued);
        assertThat(issued.rotatedAt()).as("the original is unchanged").isNull();
    }

    @Test
    void aRevokedCredentialCannotBeRotated() {
        ApiCredential revoked = new ApiCredential(UUID.randomUUID(), UUID.randomUUID(), "merchant-x", ISSUED, null, LATER);

        assertThat(revoked.isRevoked()).isTrue();
        assertThatThrownBy(() -> revoked.rotate(LATER)).isInstanceOf(IllegalStateException.class);
    }
}
