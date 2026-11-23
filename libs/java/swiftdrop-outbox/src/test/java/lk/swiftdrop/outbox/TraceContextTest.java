package lk.swiftdrop.outbox;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TraceContextTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN_ID = "00f067aa0ba902b7";

    @Test
    void formatsTheCurrentSpanAsAW3CTraceparent() {
        SpanContext context = SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
        try (Scope ignored = Span.wrap(context).makeCurrent()) {
            assertThat(TraceContext.currentTraceParent()).isEqualTo("00-" + TRACE_ID + "-" + SPAN_ID + "-01");
        }
    }

    @Test
    void isNullWhenNothingIsTraced() {
        assertThat(TraceContext.currentTraceParent()).isNull();
        assertThat(TraceContext.traceParent(null)).isNull();
        assertThat(TraceContext.traceParent(SpanContext.getInvalid())).isNull();
    }

    @Test
    void keepsAnUnsampledFlag() {
        SpanContext context = SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getDefault(), TraceState.getDefault());
        assertThat(TraceContext.traceParent(context)).endsWith("-00");
    }
}
