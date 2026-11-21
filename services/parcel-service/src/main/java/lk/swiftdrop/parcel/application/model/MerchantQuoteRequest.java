package lk.swiftdrop.parcel.application.model;

import lk.swiftdrop.events.common.Types.ServiceLevel;
import lk.swiftdrop.events.common.Types.SizeClass;

import java.math.BigDecimal;

/**
 * What pricing-service needs to price a merchant order (its QuoteRequest).
 *
 * @param codAmount cash to collect on delivery; it changes the fee but is not stored by parcel-service
 */
public record MerchantQuoteRequest(String fromCity, String fromDistrict, String toCity, String toDistrict,
                                   BigDecimal weightKg, SizeClass sizeClass, ServiceLevel serviceLevel,
                                   BigDecimal codAmount) {
}
