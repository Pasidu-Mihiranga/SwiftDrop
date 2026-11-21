package lk.swiftdrop.pricing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

interface PricingSettingJpaRepository extends JpaRepository<PricingExtensionEntities.Setting, UUID> {

    /** Half-open period, as every rate-bearing table here: start counts, end does not. */
    @Query("""
            SELECT s FROM PricingExtensionEntities$Setting s
            WHERE s.effectiveFrom <= :on
              AND (s.effectiveTo IS NULL OR s.effectiveTo > :on)
            ORDER BY s.effectiveFrom DESC
            """)
    List<PricingExtensionEntities.Setting> findInForce(@Param("on") LocalDate on);
}
