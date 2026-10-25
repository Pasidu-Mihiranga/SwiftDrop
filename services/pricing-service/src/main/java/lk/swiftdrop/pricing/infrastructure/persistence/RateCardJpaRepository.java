package lk.swiftdrop.pricing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

interface RateCardJpaRepository extends JpaRepository<RateCardEntity, UUID> {

    /**
     * Cards for the ordered pair that are in force on {@code on}, newest start date first.
     * {@code effective_to} is an exclusive bound, so a card ending today no longer applies today.
     */
    @Query("""
            SELECT rc FROM RateCardEntity rc
            WHERE rc.fromZone.code = :fromZone
              AND rc.toZone.code = :toZone
              AND rc.effectiveFrom <= :on
              AND (rc.effectiveTo IS NULL OR rc.effectiveTo > :on)
            ORDER BY rc.effectiveFrom DESC
            """)
    List<RateCardEntity> findInForce(@Param("fromZone") String fromZone,
                                     @Param("toZone") String toZone,
                                     @Param("on") LocalDate on);

    @Query("""
            SELECT rc FROM RateCardEntity rc
            ORDER BY rc.fromZone.code ASC, rc.toZone.code ASC, rc.effectiveFrom DESC
            """)
    List<RateCardEntity> findAllOrdered();
}
