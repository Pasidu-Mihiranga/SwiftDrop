package lk.swiftdrop.pricing.domain.model;

import java.math.BigDecimal;

/**
 * A lane a merchant negotiated separately from the rest of its agreement.
 *
 * @param discountPct  replaces the agreement's discount for this lane; null to keep it
 * @param flatBaseFee  replaces the rate card's base fee; null to keep it
 */
public record LaneOverride(ZoneCode fromZone, ZoneCode toZone, BigDecimal discountPct,
                           BigDecimal flatBaseFee) {

    public LaneOverride {
        if (fromZone == null || toZone == null) {
            throw new IllegalArgumentException("A lane override needs both zones");
        }
        if (discountPct == null && flatBaseFee == null) {
            throw new IllegalArgumentException("A lane override must change something");
        }
        if (discountPct != null && (discountPct.signum() < 0 || discountPct.compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalArgumentException("Lane discount must be a fraction of one");
        }
        if (flatBaseFee != null && flatBaseFee.signum() < 0) {
            throw new IllegalArgumentException("Lane base fee must not be negative");
        }
    }

    public boolean appliesTo(ZoneCode from, ZoneCode to) {
        return fromZone.equals(from) && toZone.equals(to);
    }
}
