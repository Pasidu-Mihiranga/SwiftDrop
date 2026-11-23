package lk.swiftdrop.common.pii;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Gives every service that depends on this library a {@link PiiCipher} built from
 * {@code swiftdrop.pii.encryption-key} ({@code PII_ENCRYPTION_KEY}) and the {@link EncryptedStringConverter}
 * as a Spring bean, so Hibernate gets it with the key injected. A missing or malformed key stops
 * start-up: personal data is never stored in clear.
 */
@AutoConfiguration
public class PiiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PiiCipher piiCipher(@Value("${swiftdrop.pii.encryption-key:}") String base64Key) {
        return new PiiCipher(base64Key);
    }

    @Bean
    @ConditionalOnMissingBean
    public EncryptedStringConverter encryptedStringConverter(PiiCipher cipher) {
        return new EncryptedStringConverter(cipher);
    }
}
