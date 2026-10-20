package lk.swiftdrop.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code swiftdrop.outbox.*}
 *
 * @param relayEnabled  run the relay in this instance (disable in tests that only inspect the table)
 * @param batchSize     max events published per poll
 * @param sendTimeoutMs how long to wait for a Kafka acknowledgement
 * @param retentionDays how long published rows are kept
 */
@ConfigurationProperties(prefix = "swiftdrop.outbox")
public record OutboxProperties(Boolean relayEnabled, Integer batchSize, Long sendTimeoutMs, Integer retentionDays) {

    public OutboxProperties {
        relayEnabled = relayEnabled == null || relayEnabled;
        batchSize = batchSize == null ? 100 : batchSize;
        sendTimeoutMs = sendTimeoutMs == null ? 5_000L : sendTimeoutMs;
        retentionDays = retentionDays == null ? 7 : retentionDays;
    }
}
