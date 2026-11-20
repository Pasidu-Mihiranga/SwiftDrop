package lk.swiftdrop.merchant.infrastructure.config;

import lk.swiftdrop.merchant.application.port.out.IdentityProvisioner;
import lk.swiftdrop.merchant.infrastructure.keycloak.KeycloakAdminAdapter;
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
 * Wires the Keycloak Admin API adapter. A missing client secret does not stop start-up (applying
 * and reviewing still work); approval answers 503 until it is configured, and a warning says so.
 */
@Configuration
public class KeycloakAdminConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminConfiguration.class);

    @Bean
    IdentityProvisioner identityProvisioner(@Value("${swiftdrop.keycloak.admin.base-url}") String baseUrl,
                                            @Value("${swiftdrop.keycloak.admin.realm}") String realm,
                                            @Value("${swiftdrop.keycloak.admin.client-id}") String clientId,
                                            @Value("${swiftdrop.keycloak.admin.client-secret:}") String clientSecret,
                                            @Value("${swiftdrop.keycloak.admin.connect-timeout:2s}") Duration connectTimeout,
                                            @Value("${swiftdrop.keycloak.admin.read-timeout:5s}") Duration readTimeout) {
        if (clientSecret.isBlank()) {
            log.warn("KC_MERCHANT_SERVICE_CLIENT_SECRET is not set: approving a merchant will answer 503");
        }
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(connectTimeout);
        timeouts.setReadTimeout(readTimeout);
        RestClient keycloak = RestClient.builder().baseUrl(baseUrl).requestFactory(timeouts).build();
        return new KeycloakAdminAdapter(keycloak, realm, clientId, clientSecret, Clock.systemUTC());
    }
}
