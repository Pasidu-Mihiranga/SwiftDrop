package lk.swiftdrop.parcel.infrastructure.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code swiftdrop.pricing.*}
 *
 * @param baseUrl              pricing-service inside the cluster ({@code PRICING_SERVICE_URL})
 * @param connectTimeout       how long to wait for a connection
 * @param readTimeout          how long to wait for the answer; the customer is waiting, so keep it short
 * @param failureRateThreshold percentage of failed calls that opens the circuit
 * @param slidingWindowSize    number of recent calls the failure rate is measured over
 * @param minimumCalls         calls needed before the failure rate counts
 * @param openDuration         how long the circuit stays open before a trial call is allowed
 */
@ConfigurationProperties(prefix = "swiftdrop.pricing")
public record PricingProperties(String baseUrl, Duration connectTimeout, Duration readTimeout,
                                Float failureRateThreshold, Integer slidingWindowSize, Integer minimumCalls,
                                Duration openDuration) {

    public PricingProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:8083" : baseUrl;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(1) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(2) : readTimeout;
        failureRateThreshold = failureRateThreshold == null ? 50f : failureRateThreshold;
        slidingWindowSize = slidingWindowSize == null ? 10 : slidingWindowSize;
        minimumCalls = minimumCalls == null ? 5 : minimumCalls;
        openDuration = openDuration == null ? Duration.ofSeconds(10) : openDuration;
    }
}
