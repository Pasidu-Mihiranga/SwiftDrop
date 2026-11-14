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

    @Column(name = "max_length_cm", nullable = false, precision = 6, scale = 1)
    private BigDecimal maxLengthCm;

    @Column(name = "max_width_cm", nullable = false, precision = 6, scale = 1)
    private BigDecimal maxWidthCm;

    @Column(name = "max_height_cm", nullable = false, precision = 6, scale = 1)
    private BigDecimal maxHeightCm;

    @Column(name = "multiplier", nullable = false, precision = 5, scale = 3)
    private BigDecimal multiplier;

    protected SizeClassEntity() {
        // for JPA
    }

    SizeClassEntity(String code, BigDecimal maxWeightKg, String maxDimensionsCm,
                    BigDecimal maxLengthCm, BigDecimal maxWidthCm, BigDecimal maxHeightCm,
                    BigDecimal multiplier) {
        this.code = code;
        this.maxWeightKg = maxWeightKg;
        this.maxDimensionsCm = maxDimensionsCm;
        this.maxLengthCm = maxLengthCm;
        this.maxWidthCm = maxWidthCm;
        this.maxHeightCm = maxHeightCm;
        this.multiplier = multiplier;
    }

    /**
     * The label is written from the numbers rather than supplied, so the two can never
     * disagree about how big the class is.
     */
    void reprice(BigDecimal newMaxWeightKg, String newMaxDimensionsCm, BigDecimal newMaxLengthCm,
                 BigDecimal newMaxWidthCm, BigDecimal newMaxHeightCm, BigDecimal newMultiplier) {
        this.maxWeightKg = newMaxWeightKg;
        this.maxDimensionsCm = newMaxDimensionsCm;
        this.maxLengthCm = newMaxLengthCm;
        this.maxWidthCm = newMaxWidthCm;
        this.maxHeightCm = newMaxHeightCm;
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

    BigDecimal getMaxLengthCm() {
        return maxLengthCm;
    }

    BigDecimal getMaxWidthCm() {
        return maxWidthCm;
    }

    BigDecimal getMaxHeightCm() {
        return maxHeightCm;
    }

    BigDecimal getMultiplier() {
        return multiplier;
    }
}
