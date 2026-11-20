package lk.swiftdrop.merchant.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data access to {@code api_credential}. Only {@link ApiCredentialJpaRepository} uses it. */
interface SpringDataApiCredentialRepository extends JpaRepository<ApiCredentialEntity, UUID> {

    Optional<ApiCredentialEntity> findByMerchantIdAndRevokedAtIsNull(UUID merchantId);
}
