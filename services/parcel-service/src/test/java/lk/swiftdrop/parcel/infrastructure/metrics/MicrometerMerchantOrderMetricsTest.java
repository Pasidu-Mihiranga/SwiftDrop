package lk.swiftdrop.parcel.infrastructure.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lk.swiftdrop.parcel.application.port.out.MerchantOrderMetrics.Outcome;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerMerchantOrderMetricsTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerMerchantOrderMetrics metrics = new MicrometerMerchantOrderMetrics(registry);

    private double count(String outcome) {
        return registry.get(MicrometerMerchantOrderMetrics.ORDERS).tag("outcome", outcome).counter().count();
    }

    @Test
    void everyOutcomeExistsAtZeroBeforeAnyOrder() {
        for (Outcome outcome : Outcome.values()) {
            assertThat(count(MicrometerMerchantOrderMetrics.tagValue(outcome))).as(outcome.name()).isZero();
        }
        assertThat(registry.get(MicrometerMerchantOrderMetrics.ORDERS).counters()).hasSize(Outcome.values().length);
    }

    @Test
    void eachRequestIsCountedUnderItsOutcome() {
        metrics.orderRequest(Outcome.CREATED);
        metrics.orderRequest(Outcome.CREATED);
        metrics.orderRequest(Outcome.REPLAYED);
        metrics.orderRequest(Outcome.IDEMPOTENCY_CONFLICT);

        assertThat(count("created")).isEqualTo(2);
        assertThat(count("replayed")).isEqualTo(1);
        assertThat(count("idempotency_conflict")).isEqualTo(1);
        assertThat(count("not_approved")).isZero();
    }
}
