package lk.swiftdrop.parcel.infrastructure.metrics;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lk.swiftdrop.parcel.infrastructure.client.PricingRestClient;
import org.springframework.stereotype.Component;

/**
 * {@code parcel.pricing.circuit.state} [{@code parcel_pricing_circuit_state}]: the state of the
 * circuit breaker around pricing-service calls, as 0 closed, 1 half open, 2 open (3 for any
 * other state, such as disabled or forced open).
 */
@Component
public class PricingCircuitBreakerMetrics {

    static final String STATE = "parcel.pricing.circuit.state";

    public PricingCircuitBreakerMetrics(MeterRegistry registry, PricingRestClient pricing) {
        Gauge.builder(STATE, pricing, client -> code(client.circuitState()))
                .description("Pricing circuit breaker: 0 closed, 1 half open, 2 open, 3 other")
                .register(registry);
    }

    static double code(CircuitBreaker.State state) {
        return switch (state) {
            case CLOSED -> 0;
            case HALF_OPEN -> 1;
            case OPEN -> 2;
            default -> 3;
        };
    }
}
