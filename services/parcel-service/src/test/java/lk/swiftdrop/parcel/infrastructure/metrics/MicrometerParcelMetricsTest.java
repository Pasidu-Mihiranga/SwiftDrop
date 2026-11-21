package lk.swiftdrop.parcel.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lk.swiftdrop.events.common.Types.FailureReason;
import lk.swiftdrop.events.common.Types.ParcelSource;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.parcel.application.port.out.ParcelMetrics.QuoteRejection;
import org.junit.jupiter.api.Test;

class MicrometerParcelMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerParcelMetrics metrics = new MicrometerParcelMetrics(registry);

    private double count(String name, String tag, String value) {
        return registry.get(name).tag(tag, value).counter().count();
    }

    @Test
    void everySeriesExistsAtZeroBeforeTheFirstEvent() {
        assertThat(registry.get(MicrometerParcelMetrics.BOOKINGS).counters()).hasSize(ParcelSource.values().length);
        assertThat(registry.get(MicrometerParcelMetrics.STATUS_CHANGES).counters()).hasSize(ParcelStatus.values().length);
        assertThat(registry.get(MicrometerParcelMetrics.DELIVERY_FAILURES).counters()).hasSize(FailureReason.values().length);
        assertThat(registry.get(MicrometerParcelMetrics.QUOTE_REJECTIONS).counters()).hasSize(QuoteRejection.values().length);
        assertThat(count(MicrometerParcelMetrics.BOOKINGS, "source", "customer")).isZero();
    }

    @Test
    void countsBookingsStatusChangesFailuresAndRejections() {
        metrics.parcelBooked(ParcelSource.MERCHANT);
        metrics.statusChanged(ParcelStatus.DELIVERED, null);
        metrics.statusChanged(ParcelStatus.FAILED, FailureReason.WRONG_ADDRESS);
        metrics.statusChanged(ParcelStatus.FAILED, null);
        metrics.quoteRejected(QuoteRejection.PRICING_UNAVAILABLE);

        assertThat(count(MicrometerParcelMetrics.BOOKINGS, "source", "merchant")).isEqualTo(1);
        assertThat(count(MicrometerParcelMetrics.STATUS_CHANGES, "status", "delivered")).isEqualTo(1);
        assertThat(count(MicrometerParcelMetrics.STATUS_CHANGES, "status", "failed")).isEqualTo(2);
        assertThat(count(MicrometerParcelMetrics.DELIVERY_FAILURES, "reason", "wrong_address")).isEqualTo(1);
        assertThat(count(MicrometerParcelMetrics.QUOTE_REJECTIONS, "reason", "pricing_unavailable")).isEqualTo(1);
    }

    @Test
    void mapsCircuitBreakerStatesToGaugeValues() {
        assertThat(PricingCircuitBreakerMetrics.code(CircuitBreaker.State.CLOSED)).isZero();
        assertThat(PricingCircuitBreakerMetrics.code(CircuitBreaker.State.HALF_OPEN)).isEqualTo(1);
        assertThat(PricingCircuitBreakerMetrics.code(CircuitBreaker.State.OPEN)).isEqualTo(2);
        assertThat(PricingCircuitBreakerMetrics.code(CircuitBreaker.State.FORCED_OPEN)).isEqualTo(3);
    }
}
