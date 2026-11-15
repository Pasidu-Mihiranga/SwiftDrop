package lk.swiftdrop.pricing.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * What one vehicle can take on a same-day job.
 *
 * @param rateMultiplier scales the distance charge; a van costs more to run per kilometre than a bike
 */
public record SameDayVehicle(VehicleType type, BigDecimal maxWeightKg, BigDecimal maxDistanceKm,
                             BigDecimal rateMultiplier) {

    public SameDayVehicle {
        Objects.requireNonNull(type, "type");
        if (maxWeightKg == null || maxWeightKg.signum() <= 0) {
            throw new IllegalArgumentException("Vehicle weight limit must be positive");
        }
        if (maxDistanceKm == null || maxDistanceKm.signum() <= 0) {
            throw new IllegalArgumentException("Vehicle distance limit must be positive");
        }
        if (rateMultiplier == null || rateMultiplier.signum() <= 0) {
            throw new IllegalArgumentException("Vehicle rate multiplier must be positive");
        }
    }

    public boolean carries(BigDecimal chargeableWeightKg) {
        return chargeableWeightKg.compareTo(maxWeightKg) <= 0;
    }

    public boolean reaches(BigDecimal kilometres) {
        return kilometres.compareTo(maxDistanceKm) <= 0;
    }
}
