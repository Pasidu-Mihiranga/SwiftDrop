package lk.swiftdrop.pricing.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Maps the {@code remote_area_surcharge} table. Versioned like a rate card. */
@Entity
@Table(name = "remote_area_surcharge")
class RemoteAreaSurchargeEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "district", nullable = false, length = 100, updatable = false)
    private String district;

    @Column(name = "flat_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal flatFee;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    protected RemoteAreaSurchargeEntity() {
        // for JPA
    }

    RemoteAreaSurchargeEntity(UUID id, String district, BigDecimal flatFee, LocalDate effectiveFrom) {
        this.id = id;
        this.district = district;
        this.flatFee = flatFee;
        this.effectiveFrom = effectiveFrom;
    }

    /** Ends this surcharge's period. The row is kept so an issued quote stays explainable. */
    void closeOn(LocalDate newEffectiveTo) {
        this.effectiveTo = newEffectiveTo;
    }

    String getDistrict() {
        return district;
    }

    BigDecimal getFlatFee() {
        return flatFee;
    }

    LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    LocalDate getEffectiveTo() {
        return effectiveTo;
    }
}
