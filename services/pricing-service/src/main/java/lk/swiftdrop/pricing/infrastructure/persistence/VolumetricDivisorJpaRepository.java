package lk.swiftdrop.pricing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

interface VolumetricDivisorJpaRepository extends JpaRepository<VolumetricDivisorEntity, UUID> {

    /**
     * Divisors in force on a date, newest first.
     *
     * <p>The effective period is half-open, matching every other rate-bearing table here: the
     * start date counts and the end date does not, so a divisor replaced on a given day leaves
     * neither a gap nor an overlap.
     */
    @Query("""
            SELECT vd FROM VolumetricDivisorEntity vd
            WHERE vd.effectiveFrom <= :on
              AND (vd.effectiveTo IS NULL OR vd.effectiveTo > :on)
            ORDER BY vd.effectiveFrom DESC
            """)
    List<VolumetricDivisorEntity> findInForce(@Param("on") LocalDate on);
}
