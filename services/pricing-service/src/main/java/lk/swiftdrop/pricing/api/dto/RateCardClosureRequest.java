package lk.swiftdrop.pricing.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Exclusive end date. Must be after the card's own start date. */
public record RateCardClosureRequest(@NotNull LocalDate effectiveTo) {
}
