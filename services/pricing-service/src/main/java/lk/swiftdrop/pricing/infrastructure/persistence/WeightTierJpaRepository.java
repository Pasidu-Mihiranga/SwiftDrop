package lk.swiftdrop.pricing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface WeightTierJpaRepository extends JpaRepository<WeightTierEntity, UUID> {

    /** Ordered so the open-ended band is last, which is the order the charge walks them in. */
    @Query("""
            SELECT t FROM WeightTierEntity t
            WHERE t.rateCardId = :rateCardId
            ORDER BY CASE WHEN t.upToKg IS NULL THEN 1 ELSE 0 END, t.upToKg ASC
            """)
    List<WeightTierEntity> findByRateCardOrdered(@Param("rateCardId") UUID rateCardId);
}
