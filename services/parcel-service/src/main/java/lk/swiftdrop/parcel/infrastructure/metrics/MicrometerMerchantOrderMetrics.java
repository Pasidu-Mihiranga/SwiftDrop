package lk.swiftdrop.parcel.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lk.swiftdrop.parcel.application.port.out.MerchantOrderMetrics;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Publishes {@code parcel.merchant.orders} (Prometheus {@code parcel_merchant_orders_total}) through
 * Micrometer, tagged {@code outcome} with one of the lower-case {@link Outcome} names. The tag is a
 * closed list and no merchant id is ever used, so the series count is fixed however many merchants
 * there are. Every outcome is registered at zero up front, so Prometheus' increase() also sees the
 * first event of each kind.
 */
@Component
public class MicrometerMerchantOrderMetrics implements MerchantOrderMetrics {

    static final String ORDERS = "parcel.merchant.orders";

    private final Map<Outcome, Counter> counters = new EnumMap<>(Outcome.class);

    public MicrometerMerchantOrderMetrics(MeterRegistry registry) {
        for (Outcome outcome : Outcome.values()) {
            counters.put(outcome, Counter.builder(ORDERS)
                    .description("Merchant order API requests, by how they ended")
                    .tag("outcome", tagValue(outcome))
                    .register(registry));
        }
    }

    @Override
    public void orderRequest(Outcome outcome) {
        counters.get(outcome).increment();
    }

    static String tagValue(Outcome outcome) {
        return outcome.name().toLowerCase(Locale.ROOT);
    }
}
