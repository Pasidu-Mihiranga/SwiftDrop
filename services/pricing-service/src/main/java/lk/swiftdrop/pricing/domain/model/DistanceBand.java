package lk.swiftdrop.pricing.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A range of road distance, the line-haul charge for carrying a parcel that far, and how long
 * it takes.
 *
 * <p>Half-open, like every period in this service: {@code minKm} is inside the band and
 * {@code maxKm} is not, so consecutive bands never both claim a boundary distance.
 *
 * @param maxKm null for the open-ended last band
 */
public record DistanceBand(UUID id, BigDecimal minKm, BigDecimal maxKm, BigDecimal lineHaulFee,
                           int standardTransitDays, int expressTransitDays) {

    public DistanceBand {
        if (minKm == null || minKm.signum() < 0) {
            throw new IllegalArgumentException("Band start must not be negative");
        }
        if (maxKm != null && maxKm.compareTo(minKm) <= 0) {
            throw new IllegalArgumentException("Band end must be after its start");
        }
        if (lineHaulFee == null || lineHaulFee.signum() < 0) {
            throw new IllegalArgumentException("Line-haul fee must not be negative");
        }
        if (standardTransitDays <= 0 || expressTransitDays <= 0 || expressTransitDays > standardTransitDays) {
            throw new IllegalArgumentException("Transit days must be positive and express no slower than standard");
        }
    }

    public boolean contains(BigDecimal kilometres) {
        return kilometres.compareTo(minKm) >= 0 && (maxKm == null || kilometres.compareTo(maxKm) < 0);
    }

    /** How long this band takes at the given service level. Same-day has no band and never asks. */
    public int transitDays(ServiceLevel serviceLevel) {
        return serviceLevel == ServiceLevel.EXPRESS ? expressTransitDays : standardTransitDays;
    }
}
