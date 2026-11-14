package lk.swiftdrop.parcel.application.model;

import lk.swiftdrop.events.common.Types.Money;
import lk.swiftdrop.events.common.Types.ServiceLevel;
import lk.swiftdrop.events.common.Types.SizeClass;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The part of a pricing-service quote a booking copies: the fee and what it was priced for.
 *
 * @param fromDistrict district the quote was priced from; null when pricing-service does not say
 * @param toDistrict   district the quote was priced to; null when pricing-service does not say
 */
public record Quote(UUID quoteId, Money fee, String fromZone, String toZone, String fromDistrict,
                    String toDistrict, BigDecimal weightKg, SizeClass sizeClass, ServiceLevel serviceLevel,
                    Instant expiresAt) {
}
