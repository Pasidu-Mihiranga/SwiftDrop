package lk.swiftdrop.parcel.application.port.out;

import lk.swiftdrop.parcel.application.model.MerchantQuoteRequest;
import lk.swiftdrop.parcel.application.model.Quote;

/** Prices a merchant order inline with pricing-service (design figure 13: QuoteClient). */
public interface QuotePort {

    /**
     * @throws lk.swiftdrop.parcel.application.exception.PricingUnavailableException when pricing-service
     *         times out, fails or its circuit is open
     * @throws lk.swiftdrop.parcel.application.exception.OrderNotPriceableException when pricing-service
     *         refuses the order (e.g. a district that is not serviced)
     */
    Quote quote(MerchantQuoteRequest request);
}
