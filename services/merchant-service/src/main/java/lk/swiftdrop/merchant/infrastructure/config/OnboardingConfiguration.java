package lk.swiftdrop.merchant.infrastructure.config;

import lk.swiftdrop.merchant.infrastructure.crypto.PiiCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the settings that come from the environment. */
@Configuration
public class OnboardingConfiguration {

    /** A missing or malformed {@code PII_ENCRYPTION_KEY} stops start-up: personal data is never stored in clear. */
    @Bean
    PiiCipher piiCipher(@Value("${swiftdrop.pii.encryption-key:}") String base64Key) {
        return new PiiCipher(base64Key);
    }
}
