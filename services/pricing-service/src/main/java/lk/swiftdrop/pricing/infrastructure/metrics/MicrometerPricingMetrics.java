package lk.swiftdrop.pricing.infrastructure.metrics;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import lk.swiftdrop.pricing.application.port.out.PricingMetrics;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Publishes the pricing meters through Micrometer, so they appear on /actuator/prometheus
 * alongside the HTTP and JVM meters and need no extra scrape configuration.
 *
 * Tag cardinality is deliberately bounded: service level has two values and size class four,
 * both closed enumerations, and the failure reason is a problem code rather than a message. No
 * district, zone pair or quote id is ever used as a tag, because 81 lanes times a time series
 * each is how a Prometheus instance falls over.
 */
@Component
public class MicrometerPricingMetrics implements PricingMetrics {

    private static final String QUOTES_ISSUED = "pricing.quotes.issued";
    private static final String QUOTE_FEE = "pricing.quote.fee";
    private static final String SELECTION_FAILURES = "pricing.rate_card.selection.failures";

    private final MeterRegistry registry;

    public MicrometerPricingMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void quoteIssued(ServiceLevel serviceLevel, SizeClassCode sizeClass, BigDecimal fee) {
        registry.counter(QUOTES_ISSUED,
                "service_level", serviceLevel.name(),
                "size_class", sizeClass.name()).increment();

        // A summary rather than a counter: the average and the quantiles of what customers are
        // quoted is the business signal, and a rate of summed rupees would not show a pricing
        // mistake that makes every quote too cheap.
        DistributionSummary.builder(QUOTE_FEE)
                .description("Fee quoted to a customer, in LKR")
                .baseUnit("LKR")
                .tag("service_level", serviceLevel.name())
                .publishPercentiles(0.5, 0.95)
                .register(registry)
                .record(fee.doubleValue());
    }

    @Override
    public void rateCardSelectionFailed(String reason) {
        registry.counter(SELECTION_FAILURES, "reason", reason).increment();
    }
}
