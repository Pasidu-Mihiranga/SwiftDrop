package lk.swiftdrop.common.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.LayoutBase;

import java.time.Instant;
import java.util.Map;

/**
 * Renders each log event as one JSON object per line, which Grafana Alloy ships to Loki.
 * Field names are shared with the Node and Python services so one LogQL query works everywhere:
 * {@code @timestamp, level, service, logger, thread, message, traceId, spanId, correlationId, exception}.
 * All free text passes through {@link PiiMasker}.
 */
public class JsonLogLayout extends LayoutBase<ILoggingEvent> {

    private String service = "swiftdrop-service";

    public void setService(String service) {
        this.service = service;
    }

    @Override
    public String doLayout(ILoggingEvent event) {
        StringBuilder json = new StringBuilder(256);
        json.append('{');
        field(json, "@timestamp", Instant.ofEpochMilli(event.getTimeStamp()).toString());
        field(json, "level", event.getLevel().toString());
        field(json, "service", service);
        field(json, "logger", event.getLoggerName());
        field(json, "thread", event.getThreadName());
        field(json, "message", PiiMasker.mask(event.getFormattedMessage()));

        Map<String, String> mdc = event.getMDCPropertyMap();
        if (mdc != null) {
            mdc.forEach((key, value) -> field(json, key, PiiMasker.mask(value)));
        }

        IThrowableProxy throwable = event.getThrowableProxy();
        if (throwable != null) {
            field(json, "exception", PiiMasker.mask(ThrowableProxyUtil.asString(throwable)));
        }

        json.setLength(json.length() - 1); // drop trailing comma
        json.append('}').append(CoreConstants.LINE_SEPARATOR);
        return json.toString();
    }

    private static void field(StringBuilder json, String name, String value) {
        if (value == null) {
            return;
        }
        json.append('"').append(JsonStrings.escape(name)).append("\":\"")
            .append(JsonStrings.escape(value)).append("\",");
    }
}
