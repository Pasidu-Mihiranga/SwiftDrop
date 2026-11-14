package lk.swiftdrop.parcel.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lk.swiftdrop.events.common.Types.Money;
import lk.swiftdrop.events.common.Types.ServiceLevel;
import lk.swiftdrop.events.common.Types.SizeClass;
import lk.swiftdrop.parcel.application.model.Quote;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The fields of pricing-service's quote that a booking needs (contracts/openapi/pricing-service.v1.yaml).
 * Anything else in the response is ignored, so pricing-service can add fields freely.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record QuoteResponse(UUID quoteId, Money fee, String fromZone, String toZone, String fromDistrict,
                     String toDistrict, BigDecimal weightKg, SizeClass sizeClass, ServiceLevel serviceLevel,
                     Instant expiresAt) {

    /** Empty when the answer lacks something a booking cannot do without. */
    Optional<Quote> toQuote() {
        if (quoteId == null || fee == null || fee.amount() == null || fee.currency() == null
                || weightKg == null || sizeClass == null || expiresAt == null) {
            return Optional.empty();
        }
        return Optional.of(new Quote(quoteId, fee, fromZone, toZone, fromDistrict, toDistrict, weightKg, sizeClass,
                serviceLevel == null ? ServiceLevel.STANDARD : serviceLevel, expiresAt));
    }
}
