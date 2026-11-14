package lk.swiftdrop.parcel.application.port.out;

import lk.swiftdrop.parcel.application.model.Quote;

import java.util.Optional;
import java.util.UUID;

public interface PricingPort {

    Optional<Quote> findQuote(UUID quoteId);
}
