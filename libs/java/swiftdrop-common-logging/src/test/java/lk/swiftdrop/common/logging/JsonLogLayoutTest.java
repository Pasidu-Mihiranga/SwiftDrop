package lk.swiftdrop.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonLogLayoutTest {

    private final LoggerContext context = new LoggerContext();
    private final Logger logger = context.getLogger("lk.swiftdrop.parcel.Test");

    private LoggingEvent event(String message, Throwable error) {
        LoggingEvent event = new LoggingEvent("fqcn", logger, Level.INFO, message, error, null);
        event.setMDCPropertyMap(Map.of("correlationId", "corr-123", "traceId", "abc123"));
        return event;
    }

    @Test
    void writesOneJsonObjectPerLineWithStandardFields() {
        JsonLogLayout layout = new JsonLogLayout();
        layout.setService("parcel-service");

        String line = layout.doLayout(event("Parcel created", null));

        assertThat(line).startsWith("{").endsWith("}" + System.lineSeparator());
        assertThat(line)
                .contains("\"level\":\"INFO\"")
                .contains("\"service\":\"parcel-service\"")
                .contains("\"logger\":\"lk.swiftdrop.parcel.Test\"")
                .contains("\"message\":\"Parcel created\"")
                .contains("\"correlationId\":\"corr-123\"")
                .contains("\"traceId\":\"abc123\"")
                .contains("\"@timestamp\":\"");
    }

    @Test
    void masksPiiAndEscapesQuotesInMessagesAndStackTraces() {
        JsonLogLayout layout = new JsonLogLayout();
        RuntimeException error = new IllegalStateException("bad phone 0771234567");

        String line = layout.doLayout(event("user \"kamal@example.com\" failed", error));

        assertThat(line)
                .contains("user \\\"k***@example.com\\\" failed")
                .contains("\"exception\":\"java.lang.IllegalStateException: bad phone 07******67")
                .doesNotContain("0771234567")
                .doesNotContain("kamal@");
    }

    @Test
    void maskingConvertersMaskPatternOutput() {
        LoggingEvent event = event("phone 0771234567", new RuntimeException("mail kamal@b.com"));

        assertThat(new MaskingMessageConverter().convert(event)).isEqualTo("phone 07******67");

        MaskingThrowableConverter exConverter = new MaskingThrowableConverter();
        exConverter.setContext(context);
        exConverter.start();
        assertThat(exConverter.convert(event)).contains("k***@b.com").doesNotContain("kamal@");
    }

    @Test
    void escapesControlCharacters() {
        assertThat(JsonStrings.escape("a\tb\nc\\d\u0001")).isEqualTo("a\\tb\\nc\\\\d\\u0001");
    }
}
