package lk.swiftdrop.dispatch.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class DispatchKafkaConfiguration {
    @Bean
    public DefaultErrorHandler dispatchErrorHandler() {
        // Keep the offset on failure. Never silently discard a projection fact after retries.
        return new DefaultErrorHandler(new FixedBackOff(2000, FixedBackOff.UNLIMITED_ATTEMPTS));
    }
}
