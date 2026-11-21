package lk.swiftdrop.merchant.infrastructure.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lk.swiftdrop.merchant.application.port.out.MerchantRepository;
import lk.swiftdrop.merchant.domain.model.MerchantStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MicrometerMerchantMetricsTest {

    private final MerchantRepository merchants = mock(MerchantRepository.class);
    private final MovableClock clock = new MovableClock(Instant.parse("2026-11-22T08:00:00Z"));
    private MeterRegistry registry;
    private MicrometerMerchantMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new MicrometerMerchantMetrics(registry, merchants, clock);
    }

    private double changes(String from, String to) {
        return registry.get(MicrometerMerchantMetrics.STATUS_CHANGES).tag("from", from).tag("to", to).counter().count();
    }

    private double applications(MerchantStatus status) {
        return registry.get(MicrometerMerchantMetrics.APPLICATIONS).tag("status", status.name()).gauge().value();
    }

    private static Map<MerchantStatus, Long> counts(long pending, long approved, long rejected, long suspended) {
        Map<MerchantStatus, Long> counts = new EnumMap<>(MerchantStatus.class);
        counts.put(MerchantStatus.PENDING, pending);
        counts.put(MerchantStatus.APPROVED, approved);
        counts.put(MerchantStatus.REJECTED, rejected);
        counts.put(MerchantStatus.SUSPENDED, suspended);
        return counts;
    }

    @Test
    void theUsualTransitionsExistAtZeroBeforeAnythingHappens() {
        assertThat(changes("NONE", "PENDING")).isZero();
        assertThat(changes("PENDING", "APPROVED")).isZero();
        assertThat(changes("PENDING", "REJECTED")).isZero();
        assertThat(changes("APPROVED", "SUSPENDED")).isZero();
        assertThat(registry.get(MicrometerMerchantMetrics.ROTATIONS).counter().count()).isZero();
    }

    @Test
    void statusChangesAreCountedByPreviousAndNewStatus() {
        metrics.statusChanged(null, MerchantStatus.PENDING);
        metrics.statusChanged(null, MerchantStatus.PENDING);
        metrics.statusChanged(MerchantStatus.PENDING, MerchantStatus.APPROVED);
        metrics.statusChanged(MerchantStatus.APPROVED, MerchantStatus.SUSPENDED);

        assertThat(changes("NONE", "PENDING")).isEqualTo(2);
        assertThat(changes("PENDING", "APPROVED")).isEqualTo(1);
        assertThat(changes("APPROVED", "SUSPENDED")).isEqualTo(1);
        assertThat(changes("PENDING", "REJECTED")).isZero();
    }

    @Test
    void rotationsAreCounted() {
        metrics.credentialRotated();
        metrics.credentialRotated();

        assertThat(registry.get(MicrometerMerchantMetrics.ROTATIONS).counter().count()).isEqualTo(2);
    }

    @Test
    void theStatusGaugesShowTheStoredCounts() {
        when(merchants.countByStatus()).thenReturn(counts(3, 5, 1, 0));

        assertThat(applications(MerchantStatus.PENDING)).isEqualTo(3);
        assertThat(applications(MerchantStatus.APPROVED)).isEqualTo(5);
        assertThat(applications(MerchantStatus.REJECTED)).isEqualTo(1);
        assertThat(applications(MerchantStatus.SUSPENDED)).isZero();
    }

    @Test
    void theDatabaseIsReadAtMostOncePerRefreshInterval() {
        when(merchants.countByStatus()).thenReturn(counts(1, 0, 0, 0), counts(2, 0, 0, 0));

        assertThat(applications(MerchantStatus.PENDING)).isEqualTo(1);
        clock.advance(MicrometerMerchantMetrics.REFRESH.minusSeconds(1));
        assertThat(applications(MerchantStatus.PENDING)).as("still the cached read").isEqualTo(1);
        verify(merchants, times(1)).countByStatus();

        clock.advance(java.time.Duration.ofSeconds(1));
        assertThat(applications(MerchantStatus.PENDING)).isEqualTo(2);
        verify(merchants, times(2)).countByStatus();
    }

    @Test
    void aFailedReadShowsNoValueInsteadOfAMadeUpOne() {
        when(merchants.countByStatus()).thenThrow(new IllegalStateException("database down"))
                .thenReturn(counts(4, 0, 0, 0));

        assertThat(applications(MerchantStatus.PENDING)).isNaN();
        // Not retried on every scrape while the database is down...
        assertThat(applications(MerchantStatus.APPROVED)).isNaN();
        verify(merchants, times(1)).countByStatus();

        // ...but read again after the interval.
        clock.advance(MicrometerMerchantMetrics.REFRESH);
        assertThat(applications(MerchantStatus.PENDING)).isEqualTo(4);
    }

    /** A clock the test moves forward. */
    private static final class MovableClock extends Clock {

        private Instant now;

        private MovableClock(Instant now) {
            this.now = now;
        }

        void advance(java.time.Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
