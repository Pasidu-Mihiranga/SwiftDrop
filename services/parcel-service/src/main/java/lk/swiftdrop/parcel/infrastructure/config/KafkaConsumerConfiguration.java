package lk.swiftdrop.parcel.infrastructure.config;

import lk.swiftdrop.parcel.infrastructure.messaging.LifecycleEventParser;
import lk.swiftdrop.parcel.infrastructure.messaging.MalformedEventException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

/**
 * Error handling for parcel-service's Kafka listeners (Spring Boot applies this handler to the
 * default listener container factory).
 *
 * <ul>
 *   <li>A processing failure (database down, a bug) is retried with exponential backoff until it
 *       succeeds. The failed attempt rolled back, so nothing was marked as consumed. Retries never
 *       give up: dropping a SUSPENDED event would leave a suspended merchant able to order, so the
 *       partition waits instead. There is no dead-letter topic for this consumer (topics are
 *       defined in swiftdrop-events and infra/kafka).</li>
 *   <li>A {@link MalformedEventException} can never succeed, so it is logged with its position and
 *       skipped at once instead of blocking every later event.</li>
 * </ul>
 */
@Configuration
public class KafkaConsumerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfiguration.class);

    @Bean
    LifecycleEventParser lifecycleEventParser(ObjectMapper json) {
        return new LifecycleEventParser(json);
    }

    @Bean
    CommonErrorHandler kafkaErrorHandler(
            @Value("${swiftdrop.kafka.retry.initial-interval:1s}") Duration initialInterval,
            @Value("${swiftdrop.kafka.retry.max-interval:30s}") Duration maxInterval) {
        ExponentialBackOff backOff = new ExponentialBackOff(initialInterval.toMillis(), 2.0);
        backOff.setMaxInterval(maxInterval.toMillis());
        DefaultErrorHandler handler = new DefaultErrorHandler((record, failure) -> log.error(
                "Skipped malformed message on {} partition {} offset {}: {}", record.topic(), record.partition(),
                record.offset(), rootMessage(failure)), backOff);
        handler.addNotRetryableExceptions(MalformedEventException.class);
        // Only the record's position and the failure's type are logged: neither the record's value nor the
        // failure's message, which for a database error can quote the row (lifecycle events carry contact emails).
        handler.setRetryListeners((record, failure, attempt) -> log.warn(
                "Processing {} partition {} offset {} failed (attempt {}), retrying: {}", record.topic(),
                record.partition(), record.offset(), attempt, rootCause(failure).getClass().getSimpleName()));
        return handler;
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static String rootMessage(Throwable failure) {
        return rootCause(failure).getMessage();
    }
}
