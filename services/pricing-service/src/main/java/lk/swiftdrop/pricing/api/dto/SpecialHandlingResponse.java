package lk.swiftdrop.pricing.api.dto;

/**
 * One handling option a customer can choose. The fee is not published here: it appears as its
 * own line on the quote, priced from the configuration in force when the quote is issued.
 */
public record SpecialHandlingResponse(String code, String label) { }
