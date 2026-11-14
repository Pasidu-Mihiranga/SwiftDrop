package lk.swiftdrop.pricing.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The fees charged for one ordered zone pair over a period.
 *
 * <p>Rate cards are versioned rather than edited: a change closes the current card by setting
 * {@code effectiveTo} and inserts a new one. A card is in force on a date when it started on or
 * before that date and has not ended by then, so {@code effectiveTo} is an exclusive bound.
 *
 * @param codFeePct fraction of the cash-on-delivery amount, not a percentage (0.015 is 1.5 per cent)
 */
public record RateCard(
        UUID id,
        ZoneCode fromZone,
        ZoneCode toZone,
        BigDecimal baseFee,
        BigDecimal perKgFee,
        BigDecimal expressMultiplier,
        BigDecimal codFeePct,
        LocalDate effectiveFrom,
        LocalDate effectiveTo) {

    public RateCard {
        if (fromZone == null || toZone == null) {
            throw new IllegalArgumentException("A rate card needs both an origin and a destination zone");
        }
        if (effectiveFrom == null) {
            throw new IllegalArgumentException("A rate card needs a start date");
        }
        if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException("A rate card's end date must be after its start date");
        }
        requireNonNegative(baseFee, "base fee");
        requireNonNegative(perKgFee, "per-kilogram fee");
        requireNonNegative(codFeePct, "cash-on-delivery fee fraction");
        if (expressMultiplier == null || expressMultiplier.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("A rate card's express multiplier must be at least 1");
        }
        if (codFeePct.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("A rate card's cash-on-delivery fee must be a fraction of 1 or less");
        }
    }

    private static void requireNonNegative(BigDecimal amount, String what) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("A rate card's " + what + " must not be negative");
        }
    }

    /** Whether this card governs quotes issued on the given date. */
    public boolean isInForceOn(LocalDate date) {
        return !date.isBefore(effectiveFrom) && (effectiveTo == null || date.isBefore(effectiveTo));
    }

    /** Closes the card so it no longer applies to quotes issued on or after {@code endDate}. */
    public RateCard closedOn(LocalDate endDate) {
        return new RateCard(id, fromZone, toZone, baseFee, perKgFee, expressMultiplier, codFeePct,
                effectiveFrom, endDate);
    }
}
