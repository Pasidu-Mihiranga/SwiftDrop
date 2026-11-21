package lk.swiftdrop.merchant.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * This service's record of a merchant's OAuth 2.0 client in Keycloak. It holds no secret: the
 * secret lives only in Keycloak and is shown to the merchant once.
 *
 * @param rotatedAt when the secret last changed; null until the merchant first collects one
 * @param revokedAt when the client was withdrawn; null while it is in use
 */
public record ApiCredential(UUID id, UUID merchantId, String keycloakClientId, Instant createdAt, Instant rotatedAt,
                            Instant revokedAt) {

    public ApiCredential {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(keycloakClientId, "keycloakClientId");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /** The record of a client provisioned on approval. */
    public static ApiCredential issue(UUID merchantId, String keycloakClientId, Instant now) {
        return new ApiCredential(UUID.randomUUID(), merchantId, keycloakClientId, now, null, null);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /**
     * Records that the client's secret was replaced. Only the time is kept; the secret itself is
     * never part of this record.
     *
     * @throws IllegalStateException when the credential has been revoked
     */
    public ApiCredential rotate(Instant now) {
        Objects.requireNonNull(now, "now");
        if (isRevoked()) {
            throw new IllegalStateException("A revoked API credential cannot be rotated");
        }
        return new ApiCredential(id, merchantId, keycloakClientId, createdAt, now, null);
    }
}
