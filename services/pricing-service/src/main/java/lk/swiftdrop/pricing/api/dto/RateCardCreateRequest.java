package lk.swiftdrop.pricing.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** Bean Validation mirrors the database precision so bad input fails as 400, not on insert. */
public record RateCardCreateRequest(
        @NotNull
        @Pattern(regexp = "^[A-Z][A-Z0-9-]{1,15}$",
                message = "must be 2 to 16 upper-case letters, digits or hyphens")
        String fromZone,

        @NotNull
        @Pattern(regexp = "^[A-Z][A-Z0-9-]{1,15}$",
                message = "must be 2 to 16 upper-case letters, digits or hyphens")
        String toZone,

        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal baseFee,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal perKgFee,
        @NotNull @DecimalMin("1") @Digits(integer = 2, fraction = 3) BigDecimal expressMultiplier,
        @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer = 1, fraction = 4) BigDecimal codFeePct,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,

        /**
         * Optional weight bands. Omit them and the card charges perKgFee for every kilogram.
         * Bands are marginal: each one's rate applies only to the kilograms inside it.
         */
        @Valid List<WeightTierRequest> tiers) {

    public List<WeightTierRequest> tiersOrEmpty() {
        return tiers == null ? List.of() : tiers;
    }

    /**
     * Bands have to cover the whole range in order, so the last one is open ended and no two
     * share a limit. A gap or a duplicate would make the fee depend on row order.
     */
    @AssertTrue(message = "weight bands must have ascending limits, with exactly one open-ended band last")
    public boolean isTiersWellFormed() {
        List<WeightTierRequest> bands = tiersOrEmpty();
        if (bands.isEmpty()) {
            return true;
        }
        long openEnded = bands.stream().filter(band -> band.upToKg() == null).count();
        if (openEnded != 1 || bands.get(bands.size() - 1).upToKg() != null) {
            return false;
        }
        List<BigDecimal> limits = bands.stream().map(WeightTierRequest::upToKg)
                .filter(java.util.Objects::nonNull).toList();
        return limits.stream().distinct().count() == limits.size()
                && limits.equals(limits.stream().sorted(Comparator.naturalOrder()).toList());
    }
}
