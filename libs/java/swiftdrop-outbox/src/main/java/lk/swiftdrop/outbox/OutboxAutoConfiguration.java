package lk.swiftdrop.outbox;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

/**
 * Activates the outbox in any service that depends on this library:
 * registers the entities/repositories, the {@link OutboxWriter}, the scheduled {@link OutboxRelay}
 * and the {@link IdempotentConsumer}. The tables come from the bundled Flyway migrations.
 * Note: a service must not use {@code @EntityScan} without also listing {@code lk.swiftdrop.outbox}.
 */
@AutoConfiguration(beforeName = "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration")
@AutoConfigurationPackage(basePackageClasses = OutboxEvent.class)
@ConditionalOnClass(KafkaTemplate.class)
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Clock outboxClock() {
        return Clock.systemUTC();
    }

    @Bean
    public OutboxWriter outboxWriter(OutboxEventRepository repository, ObjectMapper objectMapper,
                                     @Value("${spring.application.name}") String producer) {
        return new OutboxWriter(repository, objectMapper, producer);
    }

    @Bean
    @ConditionalOnProperty(prefix = "swiftdrop.outbox", name = "relay-enabled", matchIfMissing = true)
    public OutboxRelay outboxRelay(OutboxEventRepository repository, KafkaTemplate<String, String> kafkaTemplate,
                                   OutboxProperties properties, Clock clock) {
        return new OutboxRelay(repository, kafkaTemplate, properties, clock);
    }

    @Bean
    public IdempotentConsumer idempotentConsumer(ProcessedEventRepository repository) {
        return new IdempotentConsumer(repository);
    }
}
