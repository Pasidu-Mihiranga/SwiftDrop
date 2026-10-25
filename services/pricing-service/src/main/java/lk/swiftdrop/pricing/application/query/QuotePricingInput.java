package lk.swiftdrop.pricing.application.query;

import lk.swiftdrop.pricing.domain.model.ServiceLevel;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;

import java.math.BigDecimal;

/** Validated request data passed from the API to the pricing use case. */
public record QuotePricingInput(String fromDistrict, String toDistrict, BigDecimal weightKg,
                                SizeClassCode sizeClass, ServiceLevel serviceLevel, BigDecimal codAmount) {
}
