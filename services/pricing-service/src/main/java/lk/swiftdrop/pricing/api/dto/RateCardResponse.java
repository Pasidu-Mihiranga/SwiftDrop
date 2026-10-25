package lk.swiftdrop.pricing.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A rate card as published by the admin API.
 *
 * @param codFeePct fraction of the cash-on-delivery amount, not a percentage
 * @param effectiveTo exclusive end date; null while the card is still in force
 */
public record RateCardResponse(UUID id, String fromZone, String toZone, BigDecimal baseFee, BigDecimal perKgFee,
                               BigDecimal expressMultiplier, BigDecimal codFeePct,
                               LocalDate effectiveFrom, LocalDate effectiveTo) {
}
