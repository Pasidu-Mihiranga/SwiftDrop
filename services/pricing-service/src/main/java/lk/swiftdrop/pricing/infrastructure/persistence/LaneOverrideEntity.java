package lk.swiftdrop.pricing.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** Maps the {@code merchant_agreement_lane_override} table. */
@Entity
@Table(name = "merchant_agreement_lane_override")
class LaneOverrideEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agreement_id", nullable = false, updatable = false)
    private MerchantAgreementEntity agreement;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "from_zone_id", nullable = false, updatable = false)
    private ZoneEntity fromZone;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "to_zone_id", nullable = false, updatable = false)
    private ZoneEntity toZone;

    @Column(name = "discount_pct", precision = 5, scale = 4)
    private BigDecimal discountPct;

    @Column(name = "flat_base_fee", precision = 12, scale = 2)
    private BigDecimal flatBaseFee;

    protected LaneOverrideEntity() {
        // for JPA
    }

    LaneOverrideEntity(UUID id, ZoneEntity fromZone, ZoneEntity toZone, BigDecimal discountPct,
                       BigDecimal flatBaseFee) {
        this.id = id;
        this.fromZone = fromZone;
        this.toZone = toZone;
        this.discountPct = discountPct;
        this.flatBaseFee = flatBaseFee;
    }

    void attachTo(MerchantAgreementEntity owner) {
        this.agreement = owner;
    }

    ZoneEntity getFromZone() {
        return fromZone;
    }

    ZoneEntity getToZone() {
        return toZone;
    }

    BigDecimal getDiscountPct() {
        return discountPct;
    }

    BigDecimal getFlatBaseFee() {
        return flatBaseFee;
    }
}
