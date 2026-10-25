package lk.swiftdrop.pricing.api.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsValidRequestAndAppliesOptionalDefaults() {
        QuoteRequest request = request(" Colombo ", "Kandy", new BigDecimal("2.5"),
                SizeClassCode.M, null, null);

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.toPricingInput().fromDistrict()).isEqualTo("Colombo");
        assertThat(request.toPricingInput().serviceLevel()).isEqualTo(ServiceLevel.STANDARD);
        assertThat(request.toPricingInput().codAmount()).isEqualByComparingTo("0");
    }

    @Test
    void rejectsMissingDistrictWeightAndSizeClass() {
        QuoteRequest request = request("  ", null, null, null, null, null);

        assertThat(validator.validate(request)).extracting(v -> v.getPropertyPath().toString())
                .contains("fromDistrict", "toDistrict", "weightKg", "sizeClass");
    }

    @Test
    void rejectsOutOfRangeWeightNegativeCodAndOverlongDistrict() {
        QuoteRequest request = request("x".repeat(101), "Kandy", new BigDecimal("100.01"),
                SizeClassCode.M, ServiceLevel.EXPRESS, new BigDecimal("-0.01"));

        assertThat(validator.validate(request)).extracting(v -> v.getPropertyPath().toString())
                .contains("fromDistrict", "weightKg", "codAmount");
        assertThat(validator.validate(request("Colombo", "Kandy", BigDecimal.ZERO,
                SizeClassCode.S, null, null))).extracting(v -> v.getPropertyPath().toString())
                .contains("weightKg");
    }

    private static QuoteRequest request(String from, String to, BigDecimal weight, SizeClassCode size,
                                        ServiceLevel level, BigDecimal cod) {
        return new QuoteRequest(null, from, null, to, weight, size, level, cod);
    }
}
