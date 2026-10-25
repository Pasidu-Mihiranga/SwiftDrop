package lk.swiftdrop.pricing.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Maps the {@code size_class} table. The four codes are fixed; the rates are configuration. */
@Entity
@Table(name = "size_class")
class SizeClassEntity {

    @Id
    @Column(name = "code", nullable = false, length = 4, updatable = false)
    private String code;

    @Column(name = "max_weight_kg", nullable = false, precision = 6, scale = 2)
    private BigDecimal maxWeightKg;

    @Column(name = "max_dimensions_cm", nullable = false, length = 32)
    private String maxDimensionsCm;

    @Column(name = "multiplier", nullable = false, precision = 5, scale = 3)
    private BigDecimal multiplier;

    protected SizeClassEntity() {
        // for JPA
    }

    SizeClassEntity(String code, BigDecimal maxWeightKg, String maxDimensionsCm, BigDecimal multiplier) {
        this.code = code;
        this.maxWeightKg = maxWeightKg;
        this.maxDimensionsCm = maxDimensionsCm;
        this.multiplier = multiplier;
    }

    void reprice(BigDecimal newMaxWeightKg, String newMaxDimensionsCm, BigDecimal newMultiplier) {
        this.maxWeightKg = newMaxWeightKg;
        this.maxDimensionsCm = newMaxDimensionsCm;
        this.multiplier = newMultiplier;
    }

    String getCode() {
        return code;
    }

    BigDecimal getMaxWeightKg() {
        return maxWeightKg;
    }

    String getMaxDimensionsCm() {
        return maxDimensionsCm;
    }

    BigDecimal getMultiplier() {
        return multiplier;
    }
}
