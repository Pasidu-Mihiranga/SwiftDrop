package lk.swiftdrop.common.logging;

import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Observability defaults every SwiftDrop service shares. Metrics are scraped from
 * {@code /actuator/prometheus}, so the OTLP metrics push that comes with the OpenTelemetry starter
 * is off; traces still go over OTLP. Added with the lowest precedence, so application
 * configuration and environment variables can still turn it on.
 */
public final class ObservabilityDefaults implements EnvironmentPostProcessor {

    static final String SOURCE_NAME = "swiftdropObservabilityDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        environment.getPropertySources()
                .addLast(new MapPropertySource(SOURCE_NAME, Map.of("management.otlp.metrics.export.enabled", "false")));
    }
}
