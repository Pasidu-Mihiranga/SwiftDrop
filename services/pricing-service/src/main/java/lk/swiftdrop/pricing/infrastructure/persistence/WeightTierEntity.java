package lk.swiftdrop.pricing.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** Maps the {@code rate_card_weight_tier} table. Bands belong to a card and die with it. */
@Entity
@Table(name = "rate_card_weight_tier")
class WeightTierEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "rate_card_id", nullable = false, updatable = false)
    private UUID rateCardId;

    @Column(name = "up_to_kg", precision = 6, scale = 2)
    private BigDecimal upToKg;

    @Column(name = "per_kg_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal perKgFee;

    protected WeightTierEntity() {
        // for JPA
    }

    WeightTierEntity(UUID id, UUID rateCardId, BigDecimal upToKg, BigDecimal perKgFee) {
        this.id = id;
        this.rateCardId = rateCardId;
        this.upToKg = upToKg;
        this.perKgFee = perKgFee;
    }

    BigDecimal getUpToKg() {
        return upToKg;
    }

    BigDecimal getPerKgFee() {
        return perKgFee;
    }
}
