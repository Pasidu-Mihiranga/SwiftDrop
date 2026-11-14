package lk.swiftdrop.user.infrastructure.config;

import lk.swiftdrop.user.infrastructure.crypto.PiiCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Builds the PII cipher from {@code PII_ENCRYPTION_KEY}; a missing or malformed key stops start-up. */
@Configuration
public class PiiConfiguration {

    @Bean
    PiiCipher piiCipher(@Value("${swiftdrop.pii.encryption-key:}") String base64Key) {
        return new PiiCipher(base64Key);
    }
}
