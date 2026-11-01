package lk.swiftdrop.pricing.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What a merchant has negotiated.
 *
 * <p>The agreement sits over the standard rate cards rather than replacing them, so a change to
 * a published rate still reaches every merchant. It discounts the delivery charge, may waive
 * particular surcharges, and may carry per-lane terms for the routes actually negotiated.
 */
public record MerchantAgreement(UUID id, UUID merchantId, BigDecimal discountPct, BigDecimal discountFlat,
                                boolean waivesRemoteArea, boolean waivesInsurance, boolean waivesCodFee,
                                List<LaneOverride> laneOverrides) {

    public MerchantAgreement {
        if (merchantId == null) {
            throw new IllegalArgumentException("An agreement needs a merchant");
        }
        discountPct = discountPct == null ? BigDecimal.ZERO : discountPct;
        discountFlat = discountFlat == null ? BigDecimal.ZERO : discountFlat;
        if (discountPct.signum() < 0 || discountPct.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Discount must be a fraction of one");
        }
        if (discountFlat.signum() < 0) {
            throw new IllegalArgumentException("Flat discount must not be negative");
        }
        laneOverrides = laneOverrides == null ? List.of() : List.copyOf(laneOverrides);
    }

    public Optional<LaneOverride> overrideFor(ZoneCode fromZone, ZoneCode toZone) {
        return laneOverrides.stream().filter(o -> o.appliesTo(fromZone, toZone)).findFirst();
    }

    /** The discount rate for a lane: the lane's own where it has one, otherwise the agreement's. */
    public BigDecimal discountPctFor(ZoneCode fromZone, ZoneCode toZone) {
        return overrideFor(fromZone, toZone)
                .map(LaneOverride::discountPct)
                .filter(pct -> pct != null)
                .orElse(discountPct);
    }
}
