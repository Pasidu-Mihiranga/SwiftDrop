package lk.swiftdrop.pricing.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Maps the {@code rate_card} table: fees for one ordered zone pair over an effective period. */
@Entity
@Table(name = "rate_card")
class RateCardEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_zone_id", nullable = false, updatable = false)
    private ZoneEntity fromZone;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_zone_id", nullable = false, updatable = false)
    private ZoneEntity toZone;

    @Column(name = "base_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseFee;

    @Column(name = "per_kg_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal perKgFee;

    @Column(name = "express_multiplier", nullable = false, precision = 5, scale = 3)
    private BigDecimal expressMultiplier;

    @Column(name = "cod_fee_pct", nullable = false, precision = 5, scale = 4)
    private BigDecimal codFeePct;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    protected RateCardEntity() {
        // for JPA
    }

    RateCardEntity(UUID id, ZoneEntity fromZone, ZoneEntity toZone, BigDecimal baseFee, BigDecimal perKgFee,
                   BigDecimal expressMultiplier, BigDecimal codFeePct, LocalDate effectiveFrom, LocalDate effectiveTo) {
        this.id = id;
        this.fromZone = fromZone;
        this.toZone = toZone;
        this.baseFee = baseFee;
        this.perKgFee = perKgFee;
        this.expressMultiplier = expressMultiplier;
        this.codFeePct = codFeePct;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }

    /** Rate cards are history: closing one is the only edit, and it never changes a stored fee. */
    void closeOn(LocalDate endDate) {
        this.effectiveTo = endDate;
    }

    UUID getId() {
        return id;
    }

    ZoneEntity getFromZone() {
        return fromZone;
    }

    ZoneEntity getToZone() {
        return toZone;
    }

    BigDecimal getBaseFee() {
        return baseFee;
    }

    BigDecimal getPerKgFee() {
        return perKgFee;
    }

    BigDecimal getExpressMultiplier() {
        return expressMultiplier;
    }

    BigDecimal getCodFeePct() {
        return codFeePct;
    }

    LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    LocalDate getEffectiveTo() {
        return effectiveTo;
    }
}
