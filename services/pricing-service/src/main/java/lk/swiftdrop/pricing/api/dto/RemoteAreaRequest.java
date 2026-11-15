package lk.swiftdrop.pricing.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Marks a district hard to reach from a date, at a flat extra charge. */
public record RemoteAreaRequest(
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal flatFee,
        @NotNull LocalDate effectiveFrom) {
}
