package lk.swiftdrop.reporting.infrastructure.messaging;

import lk.swiftdrop.events.Topics;
import lk.swiftdrop.reporting.infrastructure.metrics.ReportingMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.nio.charset.StandardCharsets;

/**
 * What happens when a record cannot be applied. Transient failures (database briefly down) are
 * retried with exponential backoff, about 35 seconds in total; records that can never succeed are
 * skipped at once. Either way the skipped record is logged by position and event id (never its
 * contents, which hold personal data) and counted, so it shows on the dashboard. The read model
 * can be rebuilt by replaying the topics, so skipping does not lose source data.
 */
@Configuration
class KafkaConsumerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfiguration.class);

    @Bean
    DefaultErrorHandler kafkaErrorHandler(ReportingMetrics metrics) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(6);
        backOff.setInitialInterval(1_000);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10_000);

        DefaultErrorHandler handler = new DefaultErrorHandler((record, ex) -> skip(record, ex, metrics), backOff);
        handler.addNotRetryableExceptions(MalformedEventException.class);
        return handler;
    }

    private static void skip(ConsumerRecord<?, ?> record, Exception ex, ReportingMetrics metrics) {
        boolean malformed = hasCause(ex, MalformedEventException.class);
        metrics.rejected(malformed ? "malformed" : "retries_exhausted");
        log.error("Skipping record {}-{}@{} (eventId={}): {}", record.topic(), record.partition(), record.offset(),
                header(record, Topics.HEADER_EVENT_ID), rootMessage(ex));
    }

    private static boolean hasCause(Throwable ex, Class<? extends Throwable> type) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    private static String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? "unknown" : new String(header.value(), StandardCharsets.UTF_8);
    }
}
