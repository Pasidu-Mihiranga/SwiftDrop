package lk.swiftdrop.pricing.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lk.swiftdrop.pricing.application.query.QuotePricingInput;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;

import java.math.BigDecimal;

/** Quote fields accepted from anonymous customers; rates and zones are never caller-supplied. */
public record QuoteRequest(
        @Size(max = 60) String fromCity,
        @NotBlank @Size(max = 100) String fromDistrict,
        @Size(max = 60) String toCity,
        @NotBlank @Size(max = 100) String toDistrict,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") BigDecimal weightKg,
        @NotNull SizeClassCode sizeClass,
        ServiceLevel serviceLevel,
        @DecimalMin("0") BigDecimal codAmount) {

    public QuotePricingInput toPricingInput() {
        return new QuotePricingInput(fromDistrict.strip(), toDistrict.strip(), weightKg, sizeClass,
                serviceLevel == null ? ServiceLevel.STANDARD : serviceLevel,
                codAmount == null ? BigDecimal.ZERO : codAmount);
    }
}
