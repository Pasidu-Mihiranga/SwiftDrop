package lk.swiftdrop.pricing.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Maps the {@code volumetric_divisor} table. Versioned like a rate card: closed, never edited. */
@Entity
@Table(name = "volumetric_divisor")
class VolumetricDivisorEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "divisor", nullable = false, precision = 8, scale = 1)
    private BigDecimal divisor;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    protected VolumetricDivisorEntity() {
        // for JPA
    }

    BigDecimal getDivisor() {
        return divisor;
    }
}
