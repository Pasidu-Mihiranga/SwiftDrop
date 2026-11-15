package lk.swiftdrop.pricing.application.command;

import lk.swiftdrop.pricing.application.model.QuoteSnapshot;
import lk.swiftdrop.pricing.application.port.out.PricingMetrics;
import lk.swiftdrop.pricing.application.port.out.QuoteStore;
import lk.swiftdrop.pricing.application.query.PreparedQuote;
import lk.swiftdrop.pricing.application.query.QuotePricingInput;
import lk.swiftdrop.pricing.application.query.LaneDistanceService;
import lk.swiftdrop.pricing.domain.model.DistanceRate;
import lk.swiftdrop.pricing.domain.model.LaneDistance;
import lk.swiftdrop.pricing.application.query.QuotePricingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Issues a quote only after its exact response snapshot has been stored. */
@Service
public class QuoteIssuanceService {

    private final QuotePricingService pricing;
    private final LaneDistanceService laneDistances;
    private final QuoteStore store;
    private final PricingMetrics metrics;
    private final Duration ttl;
    private final Clock clock;

    public QuoteIssuanceService(QuotePricingService pricing, LaneDistanceService laneDistances,
                                QuoteStore store, PricingMetrics metrics,
                                @Value("${swiftdrop.pricing.quote-ttl}") Duration ttl, Clock clock) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Quote TTL must be positive");
        }
        this.pricing = pricing;
        this.laneDistances = laneDistances;
        this.store = store;
        this.metrics = metrics;
        this.ttl = ttl;
        this.clock = clock;
    }

    public QuoteSnapshot issue(QuotePricingInput input) {
        Instant issuedAt = clock.instant();
        LocalDate quoteDate = LocalDate.ofInstant(issuedAt, clock.getZone());
        // Measured first, outside the pricing transaction: this may call a routing engine, and
        // a read transaction must not be held open across a network round trip.
        LaneDistance distance = laneDistances.measure(input.serviceLevel(), input.fromDistrict(),
                input.toDistrict(), input.origin(), input.destination(), quoteDate).orElse(null);
        DistanceRate distanceRate = distance == null ? null
                : laneDistances.rateInForce(quoteDate).orElse(null);
        if (distance != null) {
            metrics.laneDistanceResolved(distance.source());
        }
        PreparedQuote prepared = pricing.prepare(input, quoteDate, distance, distanceRate);
        QuoteSnapshot snapshot = new QuoteSnapshot(UUID.randomUUID(),
                new QuoteSnapshot.Money(prepared.breakdown().fee(), "LKR"), prepared.breakdown().lines(),
                prepared.fromZone().value(), prepared.toZone().value(),
                input.fromDistrict(), input.toDistrict(), input.weightKg(),
                prepared.sizeClass().code(), input.serviceLevel(), input.codAmount(),
                issuedAt, issuedAt.plus(ttl),
                prepared.weight().chargeableKg(), prepared.weight().volumetricKg(),
                prepared.weight().basis(), input.dimensions(), input.sizeClass(), input.declaredValue(),
                input.merchantId(), prepared.agreementId(),
                prepared.distance() == null ? null : prepared.distance().kilometres(),
                prepared.distance() == null ? null : prepared.distance().source());
        Duration remaining = Duration.between(clock.instant(), snapshot.expiresAt());
        if (remaining.isZero() || remaining.isNegative()) {
            throw new IllegalStateException("Quote expired before it could be stored");
        }
        store.save(snapshot, remaining);
        // Counted only now, so the meter reflects quotes a customer can actually use.
        metrics.quoteIssued(snapshot.serviceLevel(), snapshot.sizeClass(), snapshot.fee().amount());
        return snapshot;
    }
}
