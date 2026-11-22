package lk.swiftdrop.merchant.infrastructure.persistence;

import lk.swiftdrop.merchant.application.port.out.ApiCredentialRepository;
import lk.swiftdrop.merchant.domain.model.ApiCredential;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** JPA adapter for {@link ApiCredentialRepository}. */
@Repository
public class ApiCredentialJpaRepository implements ApiCredentialRepository {

    private final SpringDataApiCredentialRepository rows;

    ApiCredentialJpaRepository(SpringDataApiCredentialRepository rows) {
        this.rows = rows;
    }

    @Override
    public ApiCredential save(ApiCredential credential) {
        ApiCredentialEntity saved = rows.saveAndFlush(new ApiCredentialEntity(credential.id(), credential.merchantId(),
                credential.keycloakClientId(), credential.createdAt(), credential.rotatedAt(), credential.revokedAt()));
        return toDomain(saved);
    }

    @Override
    public Optional<ApiCredential> findActiveByMerchant(UUID merchantId) {
        return rows.findByMerchantIdAndRevokedAtIsNull(merchantId).map(ApiCredentialJpaRepository::toDomain);
    }

    private static ApiCredential toDomain(ApiCredentialEntity entity) {
        return new ApiCredential(entity.getId(), entity.getMerchantId(), entity.getKeycloakClientId(),
                entity.getCreatedAt(), entity.getRotatedAt(), entity.getRevokedAt());
    }
}
