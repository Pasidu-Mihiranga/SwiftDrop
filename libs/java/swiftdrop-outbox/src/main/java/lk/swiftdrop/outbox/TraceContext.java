package lk.swiftdrop.outbox;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;

/**
 * The W3C trace context of the current request, so an event published later by the outbox relay
 * still belongs to the trace that caused it (gateway, service, Kafka, consumer in one trace).
 */
public final class TraceContext {

    /** W3C Trace Context header, read by Spring Kafka listeners and by the Node and Python consumers. */
    public static final String TRACEPARENT_HEADER = "traceparent";

    private TraceContext() {
    }

    /** {@code 00-<trace id>-<span id>-<flags>}, or null when nothing is being traced. */
    public static String currentTraceParent() {
        return traceParent(Span.current().getSpanContext());
    }

    static String traceParent(SpanContext context) {
        if (context == null || !context.isValid()) {
            return null;
        }
        return "00-" + context.getTraceId() + "-" + context.getSpanId() + "-" + context.getTraceFlags().asHex();
    }
}
