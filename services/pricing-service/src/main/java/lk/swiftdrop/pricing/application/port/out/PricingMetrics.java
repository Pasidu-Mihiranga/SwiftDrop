package lk.swiftdrop.pricing.application.port.out;

import lk.swiftdrop.pricing.domain.model.DistanceSource;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;

import java.math.BigDecimal;

/**
 * Business telemetry for pricing, as an outbound port.
 *
 * The HTTP and JVM meters Spring Boot publishes answer "is the service healthy". These answer
 * "is pricing behaving", which the platform dashboard cannot infer: how many quotes are being
 * issued and for which service level, what they cost, and how often a lane has no usable rate
 * card. A missing rate card is a configuration error that a 422 count alone would hide among
 * ordinary validation failures.
 *
 * It is a port rather than a direct Micrometer call so the application layer stays free of the
 * metrics library, exactly as it is free of the database and of Redis. The Micrometer
 * implementation lives in infrastructure.
 */
public interface PricingMetrics {

    /** A quote was issued and stored. */
    void quoteIssued(ServiceLevel serviceLevel, SizeClassCode sizeClass, BigDecimal fee);

    /**
     * Rate-card selection refused to price a lane.
     *
     * @param reason the problem code the caller will see, such as NO_RATE_CARD,
     *               AMBIGUOUS_RATE_CARD or DISTRICT_NOT_SERVICED
     */
    void rateCardSelectionFailed(String reason);

    /**
     * A lane was measured for a same-day quote.
     *
     * <p>Worth a meter of its own because a rising share of ESTIMATED means the routing engine
     * is failing and customers are being quoted approximate prices, which is invisible in an
     * error rate: nothing failed, the answers are just worse.
     */
    void laneDistanceResolved(DistanceSource source);
}
