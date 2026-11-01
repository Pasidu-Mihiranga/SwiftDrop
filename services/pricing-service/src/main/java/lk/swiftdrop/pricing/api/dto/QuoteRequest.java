package lk.swiftdrop.pricing.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lk.swiftdrop.pricing.application.query.QuotePricingInput;
import lk.swiftdrop.pricing.domain.model.Dimensions;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;

import java.math.BigDecimal;

/**
 * Quote fields accepted from anonymous customers; rates and zones are never caller-supplied.
 *
 * <p>The three measurements are optional. Given them, the parcel is charged on the greater of
 * its weight and its volume and the size class is derived rather than taken on trust. Without
 * them the quote is priced exactly as it was before, on weight and the declared class.
 */
public record QuoteRequest(
        @Size(max = 60) String fromCity,
        @NotBlank @Size(max = 100) String fromDistrict,
        @Size(max = 60) String toCity,
        @NotBlank @Size(max = 100) String toDistrict,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") BigDecimal weightKg,
        @NotNull SizeClassCode sizeClass,
        ServiceLevel serviceLevel,
        @DecimalMin("0") BigDecimal codAmount,
        @DecimalMin(value = "0", inclusive = false) @DecimalMax("500") @Digits(integer = 3, fraction = 1)
        BigDecimal lengthCm,
        @DecimalMin(value = "0", inclusive = false) @DecimalMax("500") @Digits(integer = 3, fraction = 1)
        BigDecimal widthCm,
        @DecimalMin(value = "0", inclusive = false) @DecimalMax("500") @Digits(integer = 3, fraction = 1)
        BigDecimal heightCm) {

    /**
     * Two sides and no third is not a parcel. Taking a partial set would mean guessing the
     * missing one, and a guess that lowers a price is not a guess worth making.
     */
    @AssertTrue(message = "give all three of lengthCm, widthCm and heightCm, or none")
    public boolean isDimensionsAllOrNothing() {
        int given = (lengthCm == null ? 0 : 1) + (widthCm == null ? 0 : 1) + (heightCm == null ? 0 : 1);
        return given == 0 || given == 3;
    }

    public QuotePricingInput toPricingInput() {
        return new QuotePricingInput(fromDistrict.strip(), toDistrict.strip(), weightKg, sizeClass,
                serviceLevel == null ? ServiceLevel.STANDARD : serviceLevel,
                codAmount == null ? BigDecimal.ZERO : codAmount,
                lengthCm == null ? null : new Dimensions(lengthCm, widthCm, heightCm));
    }
}
