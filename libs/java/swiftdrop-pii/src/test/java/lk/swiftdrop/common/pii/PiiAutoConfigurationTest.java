package lk.swiftdrop.common.pii;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class PiiAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PiiAutoConfiguration.class));

    @Test
    void buildsTheCipherAndConverterFromTheKey() {
        runner.withPropertyValues("swiftdrop.pii.encryption-key=" + PiiCipherTest.TEST_KEY).run(context -> {
            assertThat(context).hasSingleBean(PiiCipher.class).hasSingleBean(EncryptedStringConverter.class);
            EncryptedStringConverter converter = context.getBean(EncryptedStringConverter.class);
            assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn("+94771234567"))).isEqualTo("+94771234567");
        });
    }

    @Test
    void refusesToStartWithoutAKey() {
        runner.run(context -> assertThat(context).hasFailed());
    }
}
