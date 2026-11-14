package lk.swiftdrop.parcel.infrastructure.client;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import lk.swiftdrop.common.web.correlation.CorrelationId;
import lk.swiftdrop.parcel.application.exception.PricingUnavailableException;
import lk.swiftdrop.parcel.application.model.Quote;
import lk.swiftdrop.parcel.application.port.out.PricingPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;
import java.util.UUID;

/**
 * Calls pricing-service to reload a quote, behind a timeout and a Resilience4j circuit breaker.
 *
 * <p>A 404 is a normal answer (the quote is unknown or expired) and counts as a success for the
 * circuit. A timeout, a connection failure or any other status counts as a failure; after enough
 * of them the circuit opens and calls fail immediately instead of making every customer wait.
 * The caller's own token is relayed, so pricing-service sees the same user.
 */
@Component
public class PricingRestClient implements PricingPort {

    private static final Logger log = LoggerFactory.getLogger(PricingRestClient.class);
    private static final String QUOTE_PATH = "/api/v1/pricing/quotes/{quoteId}";

    private final RestClient client;
    private final CircuitBreaker circuitBreaker;

    public PricingRestClient(PricingProperties properties) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(properties.connectTimeout());
        requests.setReadTimeout(properties.readTimeout());
        this.client = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(requests).build();
        this.circuitBreaker = CircuitBreaker.of("pricing-service", CircuitBreakerConfig.custom()
                .failureRateThreshold(properties.failureRateThreshold())
                .slidingWindowSize(properties.slidingWindowSize())
                .minimumNumberOfCalls(properties.minimumCalls())
                .waitDurationInOpenState(properties.openDuration())
                .build());
    }

    @Override
    public Optional<Quote> findQuote(UUID quoteId) {
        try {
            return circuitBreaker.executeSupplier(() -> fetch(quoteId));
        } catch (CallNotPermittedException | RestClientException | UnexpectedPricingResponseException e) {
            log.warn("Quote {} could not be validated: {}", quoteId, e.getClass().getSimpleName());
            throw new PricingUnavailableException(e);
        }
    }

    private Optional<Quote> fetch(UUID quoteId) {
        return client.get()
                .uri(QUOTE_PATH, quoteId)
                .headers(PricingRestClient::relayCaller)
                .exchange((request, response) -> {
                    HttpStatusCode status = response.getStatusCode();
                    if (status.isSameCodeAs(HttpStatus.NOT_FOUND)) {
                        return Optional.<Quote>empty();
                    }
                    if (!status.is2xxSuccessful()) {
                        throw new UnexpectedPricingResponseException("status " + status.value());
                    }
                    QuoteResponse body = response.bodyTo(QuoteResponse.class);
                    return Optional.of(Optional.ofNullable(body).flatMap(QuoteResponse::toQuote)
                            .orElseThrow(() -> new UnexpectedPricingResponseException("incomplete quote")));
                });
    }

    private static void relayCaller(HttpHeaders headers) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            headers.setBearerAuth(token.getToken().getTokenValue());
        }
        CorrelationId.current().ifPresent(id -> headers.set(CorrelationId.HEADER, id));
    }

    /** pricing-service answered, but not with something a booking can rely on. */
    private static final class UnexpectedPricingResponseException extends RuntimeException {

        private UnexpectedPricingResponseException(String reason) {
            super("Unexpected answer from pricing-service: " + reason);
        }
    }
}
