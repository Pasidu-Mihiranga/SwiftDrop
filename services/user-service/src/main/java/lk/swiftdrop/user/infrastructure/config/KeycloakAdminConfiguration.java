package lk.swiftdrop.user.infrastructure.config;

import lk.swiftdrop.user.application.port.out.IdentityAdmin;
import lk.swiftdrop.user.application.port.out.IdentityDirectory;
import lk.swiftdrop.user.infrastructure.keycloak.KeycloakAdminAdapter;
import lk.swiftdrop.user.infrastructure.keycloak.KeycloakAdminClient;
import lk.swiftdrop.user.infrastructure.keycloak.KeycloakIdentityDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;

/**
 * Wires the Keycloak Admin API: one shared client (token cache, retries) and one adapter per port.
 * A missing client secret does not stop start-up (profiles still work); the courier and admin user
 * endpoints answer 503 until it is configured, and a warning says so here.
 */
@Configuration
public class KeycloakAdminConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminConfiguration.class);

    @Bean
    KeycloakAdminClient keycloakAdminClient(@Value("${swiftdrop.keycloak.admin.base-url}") String baseUrl,
                                            @Value("${swiftdrop.keycloak.admin.realm}") String realm,
                                            @Value("${swiftdrop.keycloak.admin.client-id}") String clientId,
                                            @Value("${swiftdrop.keycloak.admin.client-secret:}") String clientSecret) {
        if (clientSecret.isBlank()) {
            log.warn("KC_USER_SERVICE_CLIENT_SECRET is not set: courier and admin user endpoints will answer 503");
        }
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(2));
        timeouts.setReadTimeout(Duration.ofSeconds(5));
        RestClient keycloak = RestClient.builder().baseUrl(baseUrl).requestFactory(timeouts).build();
        return new KeycloakAdminClient(keycloak, realm, clientId, clientSecret, Clock.systemUTC());
    }

    @Bean
    IdentityDirectory identityDirectory(KeycloakAdminClient keycloak) {
        return new KeycloakIdentityDirectory(keycloak);
    }

    @Bean
    IdentityAdmin identityAdmin(KeycloakAdminClient keycloak) {
        return new KeycloakAdminAdapter(keycloak);
    }
}
