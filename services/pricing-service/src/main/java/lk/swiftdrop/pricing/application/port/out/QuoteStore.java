package lk.swiftdrop.pricing.application.port.out;

import lk.swiftdrop.pricing.application.model.QuoteSnapshot;

import java.time.Duration;

/** Persists issued quotes as snapshots for exactly their configured lifetime. */
public interface QuoteStore {

    void save(QuoteSnapshot quote, Duration ttl);
}
