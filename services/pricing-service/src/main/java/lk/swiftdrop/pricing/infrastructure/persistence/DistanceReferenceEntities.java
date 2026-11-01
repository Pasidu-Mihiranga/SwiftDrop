package lk.swiftdrop.pricing.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Entities for the distance reference tables. */
final class DistanceReferenceEntities {

    private DistanceReferenceEntities() {
    }

    /** Maps {@code district_centroid}: roughly where a district is. */
    @Entity
    @Table(name = "district_centroid")
    static class Centroid {

        @Id
        @Column(name = "district", nullable = false, length = 100, updatable = false)
        private String district;

        @Column(name = "latitude", nullable = false, precision = 9, scale = 6)
        private BigDecimal latitude;

        @Column(name = "longitude", nullable = false, precision = 9, scale = 6)
        private BigDecimal longitude;

        protected Centroid() {
            // for JPA
        }

        BigDecimal getLatitude() {
            return latitude;
        }

        BigDecimal getLongitude() {
            return longitude;
        }
    }

    /** Maps {@code distance_rate}. Versioned like a rate card. */
    @Entity
    @Table(name = "distance_rate")
    static class Rate {

        @Id
        @Column(name = "id", nullable = false, updatable = false)
        private UUID id;

        @Column(name = "flag_fall", nullable = false, precision = 12, scale = 2)
        private BigDecimal flagFall;

        @Column(name = "per_km_fee", nullable = false, precision = 12, scale = 2)
        private BigDecimal perKmFee;

        @Column(name = "min_chargeable_km", nullable = false, precision = 8, scale = 2)
        private BigDecimal minChargeableKm;

        @Column(name = "road_factor", nullable = false, precision = 5, scale = 3)
        private BigDecimal roadFactor;

        @Column(name = "effective_from", nullable = false, updatable = false)
        private LocalDate effectiveFrom;

        @Column(name = "effective_to")
        private LocalDate effectiveTo;

        protected Rate() {
            // for JPA
        }

        BigDecimal getFlagFall() {
            return flagFall;
        }

        BigDecimal getPerKmFee() {
            return perKmFee;
        }

        BigDecimal getMinChargeableKm() {
            return minChargeableKm;
        }

        BigDecimal getRoadFactor() {
            return roadFactor;
        }
    }
}
