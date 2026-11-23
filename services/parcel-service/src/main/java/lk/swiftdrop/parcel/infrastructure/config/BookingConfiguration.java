package lk.swiftdrop.parcel.infrastructure.config;

import lk.swiftdrop.parcel.domain.model.TrackingNumberFactory;
import lk.swiftdrop.parcel.infrastructure.client.PricingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.time.Clock;

/** Wires the plain-Java domain objects and the settings that come from the environment. */
@Configuration
@EnableConfigurationProperties(PricingProperties.class)
public class BookingConfiguration {

    @Bean
    TrackingNumberFactory trackingNumberFactory(Clock clock) {
        return new TrackingNumberFactory(clock, new SecureRandom());
    }
}
