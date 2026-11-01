package lk.swiftdrop.pricing.application.query;

import lk.swiftdrop.pricing.domain.model.Dimensions;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;

import java.math.BigDecimal;

/**
 * Validated request data passed from the API to the pricing use case.
 *
 * @param sizeClass  the class the customer selected; the charged class may differ once the
 *                   parcel is measured
 * @param dimensions null when the customer gave no measurements
 */
public record QuotePricingInput(String fromDistrict, String toDistrict, BigDecimal weightKg,
                                SizeClassCode sizeClass, ServiceLevel serviceLevel, BigDecimal codAmount,
                                Dimensions dimensions) {

    /** A parcel the customer did not measure. */
    public QuotePricingInput(String fromDistrict, String toDistrict, BigDecimal weightKg,
                             SizeClassCode sizeClass, ServiceLevel serviceLevel, BigDecimal codAmount) {
        this(fromDistrict, toDistrict, weightKg, sizeClass, serviceLevel, codAmount, null);
    }
}
