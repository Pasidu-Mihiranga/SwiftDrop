package lk.swiftdrop.merchant.infrastructure.persistence;

import lk.swiftdrop.merchant.domain.model.MerchantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data access to {@code merchant}. Only {@link MerchantJpaRepository} uses it. */
interface SpringDataMerchantRepository extends JpaRepository<MerchantEntity, UUID> {

    Optional<MerchantEntity> findFirstByOwnerUserIdOrderByCreatedAtDesc(String ownerUserId);

    boolean existsByOwnerUserIdAndStatusNot(String ownerUserId, MerchantStatus status);

    boolean existsByBrNumberAndStatusNot(String brNumber, MerchantStatus status);

    Page<MerchantEntity> findByStatus(MerchantStatus status, Pageable pageable);
}
