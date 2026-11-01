package lk.swiftdrop.pricing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

interface InsuranceBandJpaRepository extends JpaRepository<InsuranceBandEntity, UUID> {

    /** Bands in force, open-ended last, which is the order the premium search walks them in. */
    @Query("""
            SELECT b FROM InsuranceBandEntity b
            WHERE b.effectiveFrom <= :on
              AND (b.effectiveTo IS NULL OR b.effectiveTo > :on)
            ORDER BY CASE WHEN b.upToDeclaredValue IS NULL THEN 1 ELSE 0 END,
                     b.upToDeclaredValue ASC
            """)
    List<InsuranceBandEntity> findInForce(@Param("on") LocalDate on);
}
