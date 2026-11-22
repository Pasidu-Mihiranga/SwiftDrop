package lk.swiftdrop.pricing.application.query;

import lk.swiftdrop.pricing.domain.model.Dimensions;
import lk.swiftdrop.pricing.domain.model.GeoPoint;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;
import lk.swiftdrop.pricing.domain.model.VehicleType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Validated request data passed from the API to the pricing use case.
 *
 * @param sizeClass       the class the customer selected; the charged class may differ once the
 *                        parcel is measured
 * @param dimensions      null when the customer gave no measurements
 * @param declaredValue   what the customer says the parcel is worth; null buys no cover
 * @param merchantId      the merchant the caller acts for; null for an anonymous customer
 * @param origin          exact pickup point; null falls back to the district's centre. When given
 *                        it must lie inside {@code fromDistrict}
 * @param destination     exact drop-off point, under the same rule for {@code toDistrict}
 * @param vehicleType     same-day only; null there means the default vehicle (a bike)
 * @param specialHandling handling option codes asked for; never null, possibly empty
 */
public record QuotePricingInput(String fromDistrict, String toDistrict, BigDecimal weightKg,
                                SizeClassCode sizeClass, ServiceLevel serviceLevel, BigDecimal codAmount,
                                Dimensions dimensions, BigDecimal declaredValue, UUID merchantId,
                                GeoPoint origin, GeoPoint destination, VehicleType vehicleType,
                                List<String> specialHandling, String fromAddress, String toAddress) {

    public QuotePricingInput {
        specialHandling = specialHandling == null ? List.of() : List.copyOf(specialHandling);
    }

    public QuotePricingInput(String fromDistrict, String toDistrict, BigDecimal weightKg,
                             SizeClassCode sizeClass, ServiceLevel serviceLevel, BigDecimal codAmount,
                             Dimensions dimensions, BigDecimal declaredValue, UUID merchantId,
                             GeoPoint origin, GeoPoint destination, VehicleType vehicleType,
                             List<String> specialHandling) {
        this(fromDistrict, toDistrict, weightKg, sizeClass, serviceLevel, codAmount, dimensions, declaredValue,
                merchantId, origin, destination, vehicleType, specialHandling, null, null);
    }

    /** A parcel the customer did not measure and did not insure. */
    public QuotePricingInput(String fromDistrict, String toDistrict, BigDecimal weightKg,
                             SizeClassCode sizeClass, ServiceLevel serviceLevel, BigDecimal codAmount) {
        this(fromDistrict, toDistrict, weightKg, sizeClass, serviceLevel, codAmount, null, null,
                null, null, null);
    }

    /** Any parcel, with no vehicle preference and no special handling. */
    public QuotePricingInput(String fromDistrict, String toDistrict, BigDecimal weightKg,
                             SizeClassCode sizeClass, ServiceLevel serviceLevel, BigDecimal codAmount,
                             Dimensions dimensions, BigDecimal declaredValue, UUID merchantId,
                             GeoPoint origin, GeoPoint destination) {
        this(fromDistrict, toDistrict, weightKg, sizeClass, serviceLevel, codAmount, dimensions, declaredValue,
                merchantId, origin, destination, null, List.of());
    }

    /** The vehicle a same-day job is priced for; meaningless for any other service level. */
    public VehicleType effectiveVehicleType() {
        if (serviceLevel != ServiceLevel.SAME_DAY) {
            return null;
        }
        return vehicleType == null ? VehicleType.BIKE : vehicleType;
    }
}
