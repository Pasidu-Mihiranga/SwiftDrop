package lk.swiftdrop.pricing.domain.model;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

/**
 * The fee-wide settings in force on a date: what applies to every quote rather than to a lane.
 *
 * @param fuelSurchargePct fraction of the transport charge added for fuel, e.g. 0.05
 * @param taxPct           fraction of the finished fee added as tax, e.g. 0.18
 * @param minimumCharge    the least a delivery may cost before fuel and the optional extras
 * @param sameDayCutoff    local time from which same-day work is no longer taken for today;
 *                         null when same-day can be ordered at any time
 */
public record PricingSettings(UUID id, BigDecimal fuelSurchargePct, BigDecimal taxPct, BigDecimal minimumCharge,
                              LocalTime sameDayCutoff) {

    public PricingSettings {
        requireFraction(fuelSurchargePct, "Fuel surcharge");
        requireFraction(taxPct, "Tax");
        if (minimumCharge == null || minimumCharge.signum() < 0) {
            throw new IllegalArgumentException("Minimum charge must not be negative");
        }
    }

    /** Settings without a same-day cut-off. */
    public PricingSettings(UUID id, BigDecimal fuelSurchargePct, BigDecimal taxPct, BigDecimal minimumCharge) {
        this(id, fuelSurchargePct, taxPct, minimumCharge, null);
    }

    /**
     * Whether a same-day job can still be taken at this local time. The cut-off itself is already
     * too late, so a 14:00 cut-off accepts 13:59 and refuses 14:00.
     */
    public boolean acceptsSameDayAt(LocalTime localTime) {
        return sameDayCutoff == null || localTime.isBefore(sameDayCutoff);
    }

    private static void requireFraction(BigDecimal value, String name) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException(name + " must be a fraction from 0 up to, not including, 1");
        }
    }
}
