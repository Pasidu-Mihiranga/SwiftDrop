package lk.swiftdrop.merchant.infrastructure.persistence;

import lk.swiftdrop.merchant.domain.model.MerchantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data access to {@code merchant}. Only {@link MerchantJpaRepository} uses it. */
interface SpringDataMerchantRepository extends JpaRepository<MerchantEntity, UUID> {

    Optional<MerchantEntity> findFirstByOwnerUserIdOrderByCreatedAtDesc(String ownerUserId);

    boolean existsByOwnerUserIdAndStatusNot(String ownerUserId, MerchantStatus status);

    boolean existsByBrNumberAndStatusNot(String brNumber, MerchantStatus status);

    Page<MerchantEntity> findByStatus(MerchantStatus status, Pageable pageable);

    /** One row per status that has applications, for the monitoring gauges. */
    @Query("select m.status as status, count(m) as total from MerchantEntity m group by m.status")
    List<StatusCount> countGroupedByStatus();

    /** A row of {@link #countGroupedByStatus()}. */
    interface StatusCount {
        MerchantStatus getStatus();

        long getTotal();
    }
}
