package lk.swiftdrop.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdGlobalFilterTest {

    private final CorrelationIdGlobalFilter filter = new CorrelationIdGlobalFilter();

    private String forwardedHeader(MockServerHttpRequest request, AtomicReference<ServerWebExchange> responseHolder) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<String> forwarded = new AtomicReference<>();
        filter.filter(exchange, ex -> {
            forwarded.set(ex.getRequest().getHeaders().getFirst(CorrelationIdGlobalFilter.HEADER));
            return Mono.empty();
        }).block();
        responseHolder.set(exchange);
        return forwarded.get();
    }

    @Test
    void keepsWellFormedIncomingId() {
        AtomicReference<ServerWebExchange> exchange = new AtomicReference<>();
        String forwarded = forwardedHeader(MockServerHttpRequest.get("/api/v1/parcels")
                .header(CorrelationIdGlobalFilter.HEADER, "abc-12345678").build(), exchange);

        assertThat(forwarded).isEqualTo("abc-12345678");
        assertThat(exchange.get().getResponse().getHeaders().getFirst(CorrelationIdGlobalFilter.HEADER)).isEqualTo("abc-12345678");
    }

    @Test
    void generatesIdWhenMissingOrMalicious() {
        AtomicReference<ServerWebExchange> exchange = new AtomicReference<>();
        String generated = forwardedHeader(MockServerHttpRequest.get("/x").build(), exchange);
        String replaced = forwardedHeader(MockServerHttpRequest.get("/x")
                .header(CorrelationIdGlobalFilter.HEADER, "evil\nlog-injection").build(), exchange);

        assertThat(generated).matches("[0-9a-f-]{36}");
        assertThat(replaced).matches("[0-9a-f-]{36}");
    }

    @Test
    void runsBeforeEveryOtherFilter() {
        assertThat(filter.getOrder()).isEqualTo(Integer.MIN_VALUE);
    }
}
