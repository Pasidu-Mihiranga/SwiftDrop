package lk.swiftdrop.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code swiftdrop.security.*} settings.
 *
 * @param publicPaths extra Ant-style paths reachable without a token (in addition to health/docs)
 */
@ConfigurationProperties(prefix = "swiftdrop.security")
public record SwiftDropSecurityProperties(List<String> publicPaths) {

    static final List<String> ALWAYS_PUBLIC = List.of(
            "/actuator/health/**", "/actuator/info", "/actuator/prometheus",
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error");

    public SwiftDropSecurityProperties {
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
    }

    String[] allPublicPaths() {
        List<String> all = new ArrayList<>(ALWAYS_PUBLIC);
        all.addAll(publicPaths);
        return all.toArray(String[]::new);
    }
}
