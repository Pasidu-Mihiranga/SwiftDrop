package lk.swiftdrop.pricing.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Maps the {@code insurance_band} table. Versioned like a rate card. */
@Entity
@Table(name = "insurance_band")
class InsuranceBandEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "up_to_declared_value", precision = 12, scale = 2)
    private BigDecimal upToDeclaredValue;

    @Column(name = "flat_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal flatFee;

    @Column(name = "pct_of_declared_value", nullable = false, precision = 6, scale = 5)
    private BigDecimal pctOfDeclaredValue;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    protected InsuranceBandEntity() {
        // for JPA
    }

    BigDecimal getUpToDeclaredValue() {
        return upToDeclaredValue;
    }

    BigDecimal getFlatFee() {
        return flatFee;
    }

    BigDecimal getPctOfDeclaredValue() {
        return pctOfDeclaredValue;
    }
}
