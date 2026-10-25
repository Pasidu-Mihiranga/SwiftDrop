package lk.swiftdrop.pricing.application.query;

import lk.swiftdrop.pricing.domain.model.FeeBreakdown;
import lk.swiftdrop.pricing.domain.model.ZoneCode;

/** Calculation ready to be stored as an immutable quote snapshot. */
public record PreparedQuote(QuotePricingInput input, ZoneCode fromZone, ZoneCode toZone,
                            FeeBreakdown breakdown) {
}
