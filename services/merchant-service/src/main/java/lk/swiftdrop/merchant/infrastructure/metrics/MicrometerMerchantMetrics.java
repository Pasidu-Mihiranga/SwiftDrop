package lk.swiftdrop.merchant.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lk.swiftdrop.merchant.application.port.out.MerchantMetrics;
import lk.swiftdrop.merchant.application.port.out.MerchantRepository;
import lk.swiftdrop.merchant.domain.model.MerchantStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Publishes the merchant onboarding meters through Micrometer, so they appear on
 * /actuator/prometheus next to the HTTP and JVM meters with no extra scrape configuration
 * (the same approach as pricing-service's MicrometerPricingMetrics).
 *
 * <ul>
 *   <li>{@code merchant.status.changes} (Prometheus {@code merchant_status_changes_total}), tagged
 *       {@code from} and {@code to}: new applications ({@code from="NONE", to="PENDING"}), approvals,
 *       rejections and suspensions, counted after they are committed.</li>
 *   <li>{@code merchant.applications} ({@code merchant_applications}), a gauge tagged {@code status}:
 *       how many applications are in each status, read from the database.</li>
 *   <li>{@code merchant.credential.rotations} ({@code merchant_credential_rotations_total}).</li>
 * </ul>
 *
 * Tags are closed enumerations (four statuses), never a merchant id, so the number of series is
 * fixed however many merchants there are.
 */
@Component
public class MicrometerMerchantMetrics implements MerchantMetrics {

    static final String STATUS_CHANGES = "merchant.status.changes";
    static final String APPLICATIONS = "merchant.applications";
    static final String ROTATIONS = "merchant.credential.rotations";
    /** "from" for a new application, which has no previous status. */
    static final String NO_STATUS = "NONE";
    /** The status counts are read from the database at most this often, whatever the scrape rate. */
    static final Duration REFRESH = Duration.ofSeconds(30);

    private static final Logger log = LoggerFactory.getLogger(MicrometerMerchantMetrics.class);

    private final MeterRegistry registry;
    private final MerchantRepository merchants;
    private final Clock clock;
    private final Counter rotations;

    /** The last read, or null before the first; its counts are null when that read failed. */
    private Snapshot snapshot;

    private record Snapshot(Map<MerchantStatus, Long> counts, Instant readAt) {
    }

    public MicrometerMerchantMetrics(MeterRegistry registry, MerchantRepository merchants, Clock clock) {
        this.registry = registry;
        this.merchants = merchants;
        this.clock = clock;
        this.rotations = Counter.builder(ROTATIONS)
                .description("Merchant API client secrets replaced")
                .register(registry);
        for (MerchantStatus status : MerchantStatus.values()) {
            Gauge.builder(APPLICATIONS, this, metrics -> metrics.applicationsIn(status))
                    .description("Merchant applications in each status")
                    .tag("status", status.name())
                    .register(registry);
        }
        // Registered at zero up front: Prometheus' increase() only sees a change once a series exists,
        // so a counter that first appears at 1 would hide the first registration.
        statusChangeCounter(null, MerchantStatus.PENDING);
        statusChangeCounter(MerchantStatus.PENDING, MerchantStatus.APPROVED);
        statusChangeCounter(MerchantStatus.PENDING, MerchantStatus.REJECTED);
        statusChangeCounter(MerchantStatus.APPROVED, MerchantStatus.SUSPENDED);
    }

    @Override
    public void statusChanged(MerchantStatus from, MerchantStatus to) {
        statusChangeCounter(from, to).increment();
    }

    @Override
    public void credentialRotated() {
        rotations.increment();
    }

    private Counter statusChangeCounter(MerchantStatus from, MerchantStatus to) {
        return Counter.builder(STATUS_CHANGES)
                .description("Merchant status changes, by previous and new status")
                .tag("from", from == null ? NO_STATUS : from.name())
                .tag("to", to.name())
                .register(registry);
    }

    /**
     * The gauge's value: the stored count, or NaN when the database could not be read. NaN shows as
     * a gap in Grafana, which is honest; reporting 0 would claim there are no merchants.
     */
    double applicationsIn(MerchantStatus status) {
        Map<MerchantStatus, Long> counts = currentCounts();
        return counts == null ? Double.NaN : counts.getOrDefault(status, 0L);
    }

    private synchronized Map<MerchantStatus, Long> currentCounts() {
        Instant now = clock.instant();
        if (snapshot == null || !now.isBefore(snapshot.readAt().plus(REFRESH))) {
            try {
                snapshot = new Snapshot(merchants.countByStatus(), now);
            } catch (RuntimeException e) {
                // Kept for REFRESH too, so a database outage is not retried on every scrape.
                log.warn("Merchant counts could not be read for monitoring: {}", e.getClass().getSimpleName());
                snapshot = new Snapshot(null, now);
            }
        }
        return snapshot.counts();
    }
}
