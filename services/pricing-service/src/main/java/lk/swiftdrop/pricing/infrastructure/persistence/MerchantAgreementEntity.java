package lk.swiftdrop.pricing.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Maps the {@code merchant_agreement} table. Versioned like a rate card. */
@Entity
@Table(name = "merchant_agreement")
class MerchantAgreementEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "discount_pct", nullable = false, precision = 5, scale = 4)
    private BigDecimal discountPct;

    @Column(name = "discount_flat", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountFlat;

    @Column(name = "waives_remote_area", nullable = false)
    private boolean waivesRemoteArea;

    @Column(name = "waives_insurance", nullable = false)
    private boolean waivesInsurance;

    @Column(name = "waives_cod_fee", nullable = false)
    private boolean waivesCodFee;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @OneToMany(mappedBy = "agreement", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.EAGER)
    private List<LaneOverrideEntity> laneOverrides = new ArrayList<>();

    protected MerchantAgreementEntity() {
        // for JPA
    }

    MerchantAgreementEntity(UUID id, UUID merchantId, BigDecimal discountPct, BigDecimal discountFlat,
                            boolean waivesRemoteArea, boolean waivesInsurance, boolean waivesCodFee,
                            LocalDate effectiveFrom) {
        this.id = id;
        this.merchantId = merchantId;
        this.discountPct = discountPct;
        this.discountFlat = discountFlat;
        this.waivesRemoteArea = waivesRemoteArea;
        this.waivesInsurance = waivesInsurance;
        this.waivesCodFee = waivesCodFee;
        this.effectiveFrom = effectiveFrom;
    }

    /** Ends this agreement's period. The row survives so an issued quote stays explainable. */
    void closeOn(LocalDate newEffectiveTo) {
        this.effectiveTo = newEffectiveTo;
    }

    void addLaneOverride(LaneOverrideEntity override) {
        override.attachTo(this);
        laneOverrides.add(override);
    }

    UUID getId() {
        return id;
    }

    UUID getMerchantId() {
        return merchantId;
    }

    BigDecimal getDiscountPct() {
        return discountPct;
    }

    BigDecimal getDiscountFlat() {
        return discountFlat;
    }

    boolean isWaivesRemoteArea() {
        return waivesRemoteArea;
    }

    boolean isWaivesInsurance() {
        return waivesInsurance;
    }

    boolean isWaivesCodFee() {
        return waivesCodFee;
    }

    List<LaneOverrideEntity> getLaneOverrides() {
        return laneOverrides;
    }
}
