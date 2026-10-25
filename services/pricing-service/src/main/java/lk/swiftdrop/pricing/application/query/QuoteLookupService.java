package lk.swiftdrop.pricing.application.query;

import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.pricing.application.model.QuoteSnapshot;
import lk.swiftdrop.pricing.application.port.out.QuoteStore;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

/** Returns only the unexpired snapshot originally issued to the customer. */
@Service
public class QuoteLookupService {

    private final QuoteStore store;
    private final Clock clock;

    public QuoteLookupService(QuoteStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public QuoteSnapshot get(UUID quoteId) {
        return store.findById(quoteId)
                .filter(quote -> quote.quoteId().equals(quoteId))
                .filter(quote -> clock.instant().isBefore(quote.expiresAt()))
                .orElseThrow(() -> ResourceNotFoundException.of("Quote", quoteId));
    }
}
