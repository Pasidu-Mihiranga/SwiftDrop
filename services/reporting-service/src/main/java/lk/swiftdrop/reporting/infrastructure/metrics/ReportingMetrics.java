package lk.swiftdrop.reporting.infrastructure.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Projection meters on /actuator/prometheus. Tags are closed sets (three event types, two
 * outcomes, two rejection reasons); parcel, zone and courier ids never become tags.
 */
@Component
public class ReportingMetrics {

    private static final String PROCESSED = "reporting.events.processed";
    private static final String REJECTED = "reporting.events.rejected";
    private static final String LAG = "reporting.projection.lag";

    private final MeterRegistry registry;
    private final Clock clock;

    public ReportingMetrics(MeterRegistry registry, Clock clock) {
        this.registry = registry;
        this.clock = clock;
    }

    /** One event handled; {@code applied} is false for a duplicate delivery. */
    public void processed(String eventType, boolean applied, Instant occurredAt) {
        registry.counter(PROCESSED, "type", eventType, "outcome", applied ? "applied" : "duplicate").increment();
        if (applied && occurredAt != null) {
            // How far the reports trail reality: from the business fact to the moment it is reflected here.
            Timer.builder(LAG).description("Time from an event occurring to it being reflected in the reports")
                    .register(registry)
                    .record(Duration.between(occurredAt, clock.instant()).abs());
        }
    }

    public void rejected(String reason) {
        registry.counter(REJECTED, "reason", reason).increment();
    }
}
