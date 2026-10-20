package lk.swiftdrop.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * {@code swiftdrop.gateway.public-paths}: routes reachable without a token. Everything else needs a
 * valid Keycloak JWT; the downstream service then applies its own role and ownership checks.
 */
@ConfigurationProperties(prefix = "swiftdrop.gateway")
public record GatewaySecurityProperties(List<String> publicPaths) {

    public GatewaySecurityProperties {
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
    }
}
