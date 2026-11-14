package lk.swiftdrop.pricing.domain.model;

import java.math.BigDecimal;

/**
 * What a same-day job costs per kilometre.
 *
 * @param flagFall        charged before the first kilometre: going to collect at all costs money
 * @param minChargeableKm the shortest distance billed, so a job across the street is not free
 * @param roadFactor      what to multiply a straight line by when no router answered
 */
public record DistanceRate(BigDecimal flagFall, BigDecimal perKmFee, BigDecimal minChargeableKm,
                           BigDecimal roadFactor) {

    public DistanceRate {
        if (flagFall == null || flagFall.signum() < 0) {
            throw new IllegalArgumentException("Flag fall must not be negative");
        }
        if (perKmFee == null || perKmFee.signum() < 0) {
            throw new IllegalArgumentException("Per kilometre fee must not be negative");
        }
        minChargeableKm = minChargeableKm == null ? BigDecimal.ZERO : minChargeableKm;
        roadFactor = roadFactor == null ? BigDecimal.ONE : roadFactor;
        if (minChargeableKm.signum() < 0) {
            throw new IllegalArgumentException("Minimum chargeable distance must not be negative");
        }
        if (roadFactor.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("Road factor must be at least one");
        }
    }

    /** What to bill for, never less than the minimum. */
    public BigDecimal chargeableKilometres(BigDecimal travelled) {
        return travelled.max(minChargeableKm);
    }
}
