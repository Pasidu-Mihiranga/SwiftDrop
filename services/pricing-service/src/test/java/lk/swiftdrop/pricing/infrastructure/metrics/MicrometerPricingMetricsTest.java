package lk.swiftdrop.pricing.infrastructure.metrics;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** The meters the pricing Grafana panel queries, asserted by name and tag. */
class MicrometerPricingMetricsTest {

    private MeterRegistry registry;
    private MicrometerPricingMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new MicrometerPricingMetrics(registry);
    }

    @Test
    void countsAnIssuedQuoteAgainstItsServiceLevelAndSizeClass() {
        metrics.quoteIssued(ServiceLevel.STANDARD, SizeClassCode.M, new BigDecimal("725.65"));

        assertThat(registry.counter("pricing.quotes.issued",
                "service_level", "STANDARD", "size_class", "M").count()).isEqualTo(1.0d);
    }

    @Test
    void keepsSeparateSeriesPerServiceLevel() {
        metrics.quoteIssued(ServiceLevel.STANDARD, SizeClassCode.M, new BigDecimal("725.65"));
        metrics.quoteIssued(ServiceLevel.STANDARD, SizeClassCode.M, new BigDecimal("800.00"));
        metrics.quoteIssued(ServiceLevel.EXPRESS, SizeClassCode.L, new BigDecimal("1376.25"));

        assertThat(registry.counter("pricing.quotes.issued",
                "service_level", "STANDARD", "size_class", "M").count()).isEqualTo(2.0d);
        assertThat(registry.counter("pricing.quotes.issued",
                "service_level", "EXPRESS", "size_class", "L").count()).isEqualTo(1.0d);
    }

    @Test
    void recordsTheQuotedFeeAsADistributionInRupees() {
        metrics.quoteIssued(ServiceLevel.STANDARD, SizeClassCode.M, new BigDecimal("600.00"));
        metrics.quoteIssued(ServiceLevel.STANDARD, SizeClassCode.M, new BigDecimal("800.00"));

        DistributionSummary fees = registry.find("pricing.quote.fee")
                .tag("service_level", "STANDARD").summary();
        assertThat(fees).isNotNull();
        assertThat(fees.count()).isEqualTo(2L);
        assertThat(fees.totalAmount()).isEqualTo(1400.0d);
        assertThat(fees.mean()).isEqualTo(700.0d);
        assertThat(fees.getId().getBaseUnit()).isEqualTo("LKR");
    }

    @Test
    void countsEachSelectionFailureReasonSeparately() {
        metrics.rateCardSelectionFailed("NO_RATE_CARD");
        metrics.rateCardSelectionFailed("NO_RATE_CARD");
        metrics.rateCardSelectionFailed("AMBIGUOUS_RATE_CARD");

        assertThat(registry.counter("pricing.rate_card.selection.failures",
                "reason", "NO_RATE_CARD").count()).isEqualTo(2.0d);
        assertThat(registry.counter("pricing.rate_card.selection.failures",
                "reason", "AMBIGUOUS_RATE_CARD").count()).isEqualTo(1.0d);
    }

    @Test
    void doesNotTagAnythingWithUnboundedCardinality() {
        metrics.quoteIssued(ServiceLevel.EXPRESS, SizeClassCode.XL, new BigDecimal("2500.00"));
        metrics.rateCardSelectionFailed("DISTRICT_NOT_SERVICED");

        // Districts, zone pairs and quote ids must never become tags: 81 lanes times a series
        // each is how a Prometheus instance is brought down.
        //
        // "source" is ROUTED or ESTIMATED: two values, and never a coordinate pair, which is
        // the obvious wrong thing to tag a distance meter with.
        //
        // "phi" is Micrometer's own tag on the percentile gauges that publishPercentiles
        // creates, and it is bounded by the two quantiles configured in the adapter.
        assertThat(registry.getMeters())
                .allSatisfy(meter -> assertThat(meter.getId().getTags())
                        .extracting(tag -> tag.getKey())
                        .allMatch(key -> key.equals("service_level")
                                || key.equals("size_class")
                                || key.equals("reason")
                                || key.equals("source")
                                || key.equals("phi")));
    }
}
