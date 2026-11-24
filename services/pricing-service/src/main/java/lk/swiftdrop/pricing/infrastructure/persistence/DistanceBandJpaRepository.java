package lk.swiftdrop.pricing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

interface DistanceBandJpaRepository extends JpaRepository<PricingExtensionEntities.Band, UUID> {

    /** Half-open period; ordered so the newest version of each band comes first. */
    @Query("""
            SELECT b FROM PricingExtensionEntities$Band b
            WHERE b.effectiveFrom <= :on
              AND (b.effectiveTo IS NULL OR b.effectiveTo > :on)
            ORDER BY b.minKm ASC, b.effectiveFrom DESC
            """)
    List<PricingExtensionEntities.Band> findInForce(@Param("on") LocalDate on);

    List<PricingExtensionEntities.Band> findAllByOrderByEffectiveFromDescMinKmAsc();

    boolean existsByEffectiveFromGreaterThanEqual(LocalDate day);
}
