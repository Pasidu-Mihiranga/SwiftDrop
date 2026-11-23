package lk.swiftdrop.gateway.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

/**
 * Answers {@code 503 SERVICE_UNAVAILABLE} problem+json when the service behind a route cannot be
 * reached (connection refused, unknown host, no instance), instead of Spring's generic 500. The body
 * never names the service or the address; the log does, with the correlation id.
 * Runs before Spring Boot's default error handler (order -1).
 */
@Component
@Order(-2)
public class ServiceUnavailableHandler implements WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ServiceUnavailableHandler.class);

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (!isUnreachable(ex) || response.isCommitted()) return Mono.error(ex);

        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        log.warn("Route {} unavailable: {}", route == null ? "?" : route.getId(), ex.getMessage());

        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        response.getHeaders().set("Retry-After", "5");
        byte[] body = ("{\"type\":\"about:blank\",\"title\":\"Service Unavailable\",\"status\":503,"
                + "\"code\":\"SERVICE_UNAVAILABLE\",\"detail\":\"This part of SwiftDrop is temporarily unavailable. Please try again shortly.\"}")
                .getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    static boolean isUnreachable(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof ConnectException || cause instanceof UnknownHostException
                    || (cause instanceof NotFoundException notFound && notFound.getStatusCode().value() == 503)) {
                return true;
            }
        }
        return false;
    }
}
