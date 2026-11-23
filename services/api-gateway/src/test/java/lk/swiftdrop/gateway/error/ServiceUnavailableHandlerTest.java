package lk.swiftdrop.gateway.error;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import java.net.ConnectException;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceUnavailableHandlerTest {

    private final ServiceUnavailableHandler handler = new ServiceUnavailableHandler();

    @Test
    void recognisesUnreachableServicesAnywhereInTheCauseChain() {
        assertThat(ServiceUnavailableHandler.isUnreachable(new RuntimeException(new ConnectException("refused")))).isTrue();
        assertThat(ServiceUnavailableHandler.isUnreachable(new UnknownHostException("pricing-service"))).isTrue();
        assertThat(ServiceUnavailableHandler.isUnreachable(NotFoundException.create(false, "no instance"))).isTrue(); // 503
        assertThat(ServiceUnavailableHandler.isUnreachable(NotFoundException.create(true, "no route"))).isFalse(); // 404
        assertThat(ServiceUnavailableHandler.isUnreachable(new IllegalStateException("bug"))).isFalse();
    }

    @Test
    void otherErrorsAreLeftToTheDefaultHandler() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/x"));
        IllegalStateException bug = new IllegalStateException("bug");
        StepVerifier.create(handler.handle(exchange, bug)).expectErrorMatches(e -> e == bug).verify();
    }

    @Test
    void writes503WithoutTheAddress() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/x"));
        handler.handle(exchange, new ConnectException("Connection refused: pricing-service/10.0.0.7:8083")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("\"code\":\"SERVICE_UNAVAILABLE\"").doesNotContain("pricing", "10.0.0.7");
    }

    @Test
    void aCommittedResponseIsNotRewritten() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/x"));
        exchange.getResponse().setComplete().block();
        ConnectException refused = new ConnectException("refused");
        StepVerifier.create(handler.handle(exchange, refused)).expectErrorMatches(e -> e == refused).verify();
    }
}
