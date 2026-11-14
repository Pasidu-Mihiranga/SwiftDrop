package lk.swiftdrop.pricing.infrastructure.config;

import lk.swiftdrop.pricing.domain.policy.FeeCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publishes the fee calculator as a bean.
 *
 * <p>The calculator and its rule chain are plain objects, because the domain layer may not see
 * Spring. Wiring them up is an infrastructure concern, so it happens here: that keeps the chain
 * replaceable from configuration or a test without the domain knowing a container exists.
 */
@Configuration
public class PricingPolicyConfiguration {

    @Bean
    FeeCalculator feeCalculator() {
        return new FeeCalculator();
    }
}
