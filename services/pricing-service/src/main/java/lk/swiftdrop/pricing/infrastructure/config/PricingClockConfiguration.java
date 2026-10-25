package lk.swiftdrop.pricing.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class PricingClockConfiguration {

    @Bean
    Clock pricingClock(@Value("${swiftdrop.pricing.quote-zone-id}") String zoneId) {
        return Clock.system(ZoneId.of(zoneId));
    }
}
