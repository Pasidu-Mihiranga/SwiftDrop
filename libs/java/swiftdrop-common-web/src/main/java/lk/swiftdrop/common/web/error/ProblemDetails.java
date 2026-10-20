package lk.swiftdrop.common.web.error;

import lk.swiftdrop.common.web.correlation.CorrelationId;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.Instant;

/**
 * Builds RFC 9457 problem responses with SwiftDrop's extra members:
 * {@code code} (stable error code), {@code traceId} (to find the request in Grafana) and {@code timestamp}.
 */
public final class ProblemDetails {

    public static final String TYPE_BASE = "https://api.swiftdrop.lk/problems/";

    private ProblemDetails() {
    }

    public static ProblemDetail of(HttpStatusCode status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        enrich(problem, code);
        return problem;
    }

    /** Adds type, code, traceId and timestamp to a problem created elsewhere (e.g. by Spring MVC). */
    public static ProblemDetail enrich(ProblemDetail problem, String code) {
        problem.setType(URI.create(TYPE_BASE + code.toLowerCase().replace('_', '-')));
        problem.setProperty("code", code);
        problem.setProperty("traceId", currentTraceId());
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }

    private static String currentTraceId() {
        String traceId = MDC.get("traceId");
        return traceId != null ? traceId : MDC.get(CorrelationId.MDC_KEY);
    }
}
