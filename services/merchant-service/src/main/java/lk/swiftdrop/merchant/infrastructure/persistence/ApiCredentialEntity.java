package lk.swiftdrop.merchant.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Row of {@code api_credential}. There is deliberately no column for a secret. */
@Entity
@Table(name = "api_credential")
public class ApiCredentialEntity {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "keycloak_client_id", nullable = false, length = 100, updatable = false)
    private String keycloakClientId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected ApiCredentialEntity() {
    }

    ApiCredentialEntity(UUID id, UUID merchantId, String keycloakClientId, Instant createdAt, Instant rotatedAt,
                        Instant revokedAt) {
        this.id = id;
        this.merchantId = merchantId;
        this.keycloakClientId = keycloakClientId;
        this.createdAt = createdAt;
        this.rotatedAt = rotatedAt;
        this.revokedAt = revokedAt;
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getKeycloakClientId() { return keycloakClientId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRotatedAt() { return rotatedAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
