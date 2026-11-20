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
}
