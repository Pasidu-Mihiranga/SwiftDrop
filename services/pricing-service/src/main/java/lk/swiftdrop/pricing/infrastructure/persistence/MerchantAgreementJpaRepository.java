package lk.swiftdrop.pricing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

interface MerchantAgreementJpaRepository extends JpaRepository<MerchantAgreementEntity, UUID> {

    /** Half-open period, as every rate-bearing table here: start counts, end does not. */
    @Query("""
            SELECT a FROM MerchantAgreementEntity a
            WHERE a.merchantId = :merchantId
              AND a.effectiveFrom <= :on
              AND (a.effectiveTo IS NULL OR a.effectiveTo > :on)
            ORDER BY a.effectiveFrom DESC
            """)
    List<MerchantAgreementEntity> findInForce(@Param("merchantId") UUID merchantId,
                                              @Param("on") LocalDate on);

    List<MerchantAgreementEntity> findByMerchantIdOrderByEffectiveFromDesc(UUID merchantId);
}
