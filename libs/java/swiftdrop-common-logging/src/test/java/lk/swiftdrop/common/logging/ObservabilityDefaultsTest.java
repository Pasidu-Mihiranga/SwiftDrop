package lk.swiftdrop.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class ObservabilityDefaultsTest {

    private static final String KEY = "management.otlp.metrics.export.enabled";

    @Test
    void turnsOffOtlpMetricsPushByDefault() {
        StandardEnvironment environment = new StandardEnvironment();
        new ObservabilityDefaults().postProcessEnvironment(environment, null);
        assertThat(environment.getProperty(KEY)).isEqualTo("false");
    }

    @Test
    void configurationStillWins() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("app", Map.of(KEY, "true")));
        new ObservabilityDefaults().postProcessEnvironment(environment, null);
        assertThat(environment.getProperty(KEY)).isEqualTo("true");
    }
}
