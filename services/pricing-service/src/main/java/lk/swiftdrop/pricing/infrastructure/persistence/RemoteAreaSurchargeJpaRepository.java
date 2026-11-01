package lk.swiftdrop.pricing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

interface RemoteAreaSurchargeJpaRepository extends JpaRepository<RemoteAreaSurchargeEntity, UUID> {

    /**
     * Surcharges in force on a date, newest first.
     *
     * <p>The effective period is half-open, matching rate_card: the start date counts and the
     * end date does not. District matching ignores case, as district lookup does elsewhere.
     */
    @Query("""
            SELECT r FROM RemoteAreaSurchargeEntity r
            WHERE lower(r.district) = lower(:district)
              AND r.effectiveFrom <= :on
              AND (r.effectiveTo IS NULL OR r.effectiveTo > :on)
            ORDER BY r.effectiveFrom DESC
            """)
    List<RemoteAreaSurchargeEntity> findInForce(@Param("district") String district,
                                                @Param("on") LocalDate on);
}
