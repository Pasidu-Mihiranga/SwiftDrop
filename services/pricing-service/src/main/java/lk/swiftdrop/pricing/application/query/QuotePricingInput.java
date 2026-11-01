package lk.swiftdrop.pricing.application.query;

import lk.swiftdrop.pricing.domain.model.Dimensions;
import lk.swiftdrop.pricing.domain.model.GeoPoint;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Validated request data passed from the API to the pricing use case.
 *
 * @param sizeClass  the class the customer selected; the charged class may differ once the
 *                   parcel is measured
 * @param dimensions    null when the customer gave no measurements
 * @param declaredValue what the customer says the parcel is worth; null buys no cover
 * @param merchantId    the merchant the caller acts for; null for an anonymous customer
 * @param origin        exact pickup point for a same-day job; null falls back to the district
 */
public record QuotePricingInput(String fromDistrict, String toDistrict, BigDecimal weightKg,
                                SizeClassCode sizeClass, ServiceLevel serviceLevel, BigDecimal codAmount,
                                Dimensions dimensions, BigDecimal declaredValue, UUID merchantId,
                                GeoPoint origin, GeoPoint destination) {

    /** A parcel the customer did not measure and did not insure. */
    public QuotePricingInput(String fromDistrict, String toDistrict, BigDecimal weightKg,
                             SizeClassCode sizeClass, ServiceLevel serviceLevel, BigDecimal codAmount) {
        this(fromDistrict, toDistrict, weightKg, sizeClass, serviceLevel, codAmount, null, null,
                null, null, null);
    }
}
