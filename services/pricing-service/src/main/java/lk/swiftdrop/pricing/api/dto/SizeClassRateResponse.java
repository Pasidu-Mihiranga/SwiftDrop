package lk.swiftdrop.pricing.api.dto;

import lk.swiftdrop.pricing.domain.model.SizeClassCode;
import lk.swiftdrop.pricing.domain.model.SizeClassRate;

import java.math.BigDecimal;

/**
 * A size class as published in the OpenAPI contract.
 *
 * @param maxDimensionsCm the display label, rendered from the three measurements
 */
public record SizeClassRateResponse(SizeClassCode code, BigDecimal maxWeightKg, String maxDimensionsCm,
                                    BigDecimal maxLengthCm, BigDecimal maxWidthCm, BigDecimal maxHeightCm,
                                    BigDecimal multiplier) {

    public static SizeClassRateResponse of(SizeClassRate rate) {
        return new SizeClassRateResponse(rate.code(), rate.maxWeightKg(), rate.maxDimensionsLabel(),
                rate.maxDimensions().lengthCm(), rate.maxDimensions().widthCm(),
                rate.maxDimensions().heightCm(), rate.multiplier());
    }
}
