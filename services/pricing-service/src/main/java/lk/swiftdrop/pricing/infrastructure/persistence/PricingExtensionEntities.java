package lk.swiftdrop.pricing.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Entities for the tables V16 adds. All four are read-only from the application: they are
 * seeded configuration, changed by migration.
 */
final class PricingExtensionEntities {

    private PricingExtensionEntities() {
    }

    /** Maps {@code pricing_setting}. Versioned like a rate card. */
    @Entity
    @Table(name = "pricing_setting")
    static class Setting {

        @Id
        @Column(name = "id", nullable = false, updatable = false)
        private UUID id;

        @Column(name = "fuel_surcharge_pct", nullable = false, precision = 6, scale = 4)
        private BigDecimal fuelSurchargePct;

        @Column(name = "tax_pct", nullable = false, precision = 6, scale = 4)
        private BigDecimal taxPct;

        @Column(name = "minimum_charge", nullable = false, precision = 12, scale = 2)
        private BigDecimal minimumCharge;

        @Column(name = "effective_from", nullable = false, updatable = false)
        private LocalDate effectiveFrom;

        @Column(name = "effective_to")
        private LocalDate effectiveTo;

        protected Setting() {
            // for JPA
        }

        UUID getId() {
            return id;
        }

        BigDecimal getFuelSurchargePct() {
            return fuelSurchargePct;
        }

        BigDecimal getTaxPct() {
            return taxPct;
        }

        BigDecimal getMinimumCharge() {
            return minimumCharge;
        }
    }

    /** Maps {@code distance_band}. */
    @Entity
    @Table(name = "distance_band")
    static class Band {

        @Id
        @Column(name = "id", nullable = false, updatable = false)
        private UUID id;

        @Column(name = "min_km", nullable = false, precision = 8, scale = 2)
        private BigDecimal minKm;

        @Column(name = "max_km", precision = 8, scale = 2)
        private BigDecimal maxKm;

        @Column(name = "line_haul_fee", nullable = false, precision = 12, scale = 2)
        private BigDecimal lineHaulFee;

        @Column(name = "standard_transit_days", nullable = false)
        private short standardTransitDays;

        @Column(name = "express_transit_days", nullable = false)
        private short expressTransitDays;

        @Column(name = "effective_from", nullable = false, updatable = false)
        private LocalDate effectiveFrom;

        @Column(name = "effective_to")
        private LocalDate effectiveTo;

        protected Band() {
            // for JPA
        }

        UUID getId() {
            return id;
        }

        BigDecimal getMinKm() {
            return minKm;
        }

        BigDecimal getMaxKm() {
            return maxKm;
        }

        BigDecimal getLineHaulFee() {
            return lineHaulFee;
        }

        short getStandardTransitDays() {
            return standardTransitDays;
        }

        short getExpressTransitDays() {
            return expressTransitDays;
        }

        LocalDate getEffectiveFrom() {
            return effectiveFrom;
        }
    }

    /** Maps {@code special_handling}. */
    @Entity
    @Table(name = "special_handling")
    static class Handling {

        @Id
        @Column(name = "code", nullable = false, length = 32, updatable = false)
        private String code;

        @Column(name = "label", nullable = false, length = 100)
        private String label;

        @Column(name = "fee", nullable = false, precision = 12, scale = 2)
        private BigDecimal fee;

        @Column(name = "active", nullable = false)
        private boolean active;

        @Column(name = "sort_order", nullable = false)
        private short sortOrder;

        protected Handling() {
            // for JPA
        }

        String getCode() {
            return code;
        }

        String getLabel() {
            return label;
        }

        BigDecimal getFee() {
            return fee;
        }
    }

    /** Maps {@code same_day_vehicle}. */
    @Entity
    @Table(name = "same_day_vehicle")
    static class Vehicle {

        @Id
        @Column(name = "code", nullable = false, length = 16, updatable = false)
        private String code;

        @Column(name = "label", nullable = false, length = 60)
        private String label;

        @Column(name = "max_weight_kg", nullable = false, precision = 6, scale = 2)
        private BigDecimal maxWeightKg;

        @Column(name = "max_distance_km", nullable = false, precision = 8, scale = 2)
        private BigDecimal maxDistanceKm;

        @Column(name = "rate_multiplier", nullable = false, precision = 5, scale = 3)
        private BigDecimal rateMultiplier;

        @Column(name = "active", nullable = false)
        private boolean active;

        protected Vehicle() {
            // for JPA
        }

        String getCode() {
            return code;
        }

        BigDecimal getMaxWeightKg() {
            return maxWeightKg;
        }

        BigDecimal getMaxDistanceKm() {
            return maxDistanceKm;
        }

        BigDecimal getRateMultiplier() {
            return rateMultiplier;
        }
    }
}
