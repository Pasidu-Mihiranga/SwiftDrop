package lk.swiftdrop.pricing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

interface DistanceRateJpaRepository extends JpaRepository<DistanceReferenceEntities.Rate, UUID> {

    /** Half-open period, as every rate-bearing table here: start counts, end does not. */
    @Query("""
            SELECT r FROM DistanceReferenceEntities$Rate r
            WHERE r.effectiveFrom <= :on
              AND (r.effectiveTo IS NULL OR r.effectiveTo > :on)
            ORDER BY r.effectiveFrom DESC
            """)
    List<DistanceReferenceEntities.Rate> findInForce(@Param("on") LocalDate on);
}
