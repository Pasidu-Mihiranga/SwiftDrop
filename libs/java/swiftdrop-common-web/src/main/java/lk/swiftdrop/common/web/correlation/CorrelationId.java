package lk.swiftdrop.common.web.correlation;

import org.slf4j.MDC;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The correlation id ties together every log line, Kafka event and webhook caused by one user action.
 * The gateway creates it; services read it from the {@value #HEADER} header and copy it into events.
 */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    /** Only safe characters are accepted so a client cannot inject text into our logs. */
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9-]{8,64}");

    private CorrelationId() {
    }

    /** Returns the incoming id if well-formed, otherwise a new random one. */
    public static String resolve(String incoming) {
        return incoming != null && VALID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
    }

    /** Correlation id of the current request/consumer thread, if any. */
    public static Optional<String> current() {
        return Optional.ofNullable(MDC.get(MDC_KEY));
    }
}
