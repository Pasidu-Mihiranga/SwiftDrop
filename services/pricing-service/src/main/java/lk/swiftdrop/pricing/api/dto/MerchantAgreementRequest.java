package lk.swiftdrop.pricing.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Terms to publish for a merchant from a given date. */
public record MerchantAgreementRequest(
        @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 4) BigDecimal discountPct,
        @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal discountFlat,
        Boolean waivesRemoteArea,
        Boolean waivesInsurance,
        Boolean waivesCodFee,
        @NotNull LocalDate effectiveFrom,
        @Valid List<LaneOverrideRequest> laneOverrides) {

    public boolean waivesRemoteAreaOrDefault() {
        return Boolean.TRUE.equals(waivesRemoteArea);
    }

    public boolean waivesInsuranceOrDefault() {
        return Boolean.TRUE.equals(waivesInsurance);
    }

    public boolean waivesCodFeeOrDefault() {
        return Boolean.TRUE.equals(waivesCodFee);
    }

    public List<LaneOverrideRequest> laneOverridesOrEmpty() {
        return laneOverrides == null ? List.of() : laneOverrides;
    }

    /** A lane negotiated separately from the rest of the agreement. */
    public record LaneOverrideRequest(
            @NotNull @Pattern(regexp = "^[A-Z][A-Z0-9-]{1,15}$") String fromZone,
            @NotNull @Pattern(regexp = "^[A-Z][A-Z0-9-]{1,15}$") String toZone,
            @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 4) BigDecimal discountPct,
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal flatBaseFee) {
    }
}
