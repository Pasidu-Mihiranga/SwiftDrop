package lk.swiftdrop.gateway.config;

import lk.swiftdrop.gateway.ratelimit.CallerKeyResolver;
import lk.swiftdrop.gateway.ratelimit.TieredRedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Beans used by the {@code RequestRateLimiter} default filter in application.yml. */
@Configuration(proxyBeanMethods = false)
public class RateLimitConfiguration {

    @Bean
    public CallerKeyResolver callerKeyResolver(RateLimitProperties properties) {
        return new CallerKeyResolver(properties);
    }

    @Bean
    @Primary
    public TieredRedisRateLimiter tieredRedisRateLimiter(RateLimitProperties properties) {
        return new TieredRedisRateLimiter(properties);
    }
}
