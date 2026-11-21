package lk.swiftdrop.parcel.infrastructure.client;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import lk.swiftdrop.common.web.correlation.CorrelationId;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lk.swiftdrop.events.common.Types.ServiceLevel;
import lk.swiftdrop.events.common.Types.SizeClass;
import lk.swiftdrop.parcel.application.exception.OrderNotPriceableException;
import lk.swiftdrop.parcel.application.exception.PricingUnavailableException;
import lk.swiftdrop.parcel.application.model.MerchantQuoteRequest;
import lk.swiftdrop.parcel.application.model.Quote;
import lk.swiftdrop.parcel.application.port.out.PricingPort;
import lk.swiftdrop.parcel.application.port.out.QuotePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Calls pricing-service to reload a quote, behind a timeout and a Resilience4j circuit breaker.
 *
 * <p>A 404 is a normal answer (the quote is unknown or expired) and counts as a success for the
 * circuit. A timeout, a connection failure or any other status counts as a failure; after enough
 * of them the circuit opens and calls fail immediately instead of making every customer wait.
 * The caller's own token is relayed, so pricing-service sees the same user.
 *
 * <p>It also prices merchant orders inline ({@link QuotePort}), through the same client, timeouts
 * and circuit breaker. There a 400 or 422 is pricing-service refusing the order: a normal business
 * answer that counts as a success for the circuit and reaches the merchant as a 422. The merchant's
 * access token is not sent: quoting is public, so pricing-service needs no credentials, and a
 * merchant's token should not travel further than it must. Only the correlation id is passed on.
 */
@Component
public class PricingRestClient implements PricingPort, QuotePort {

    private static final Logger log = LoggerFactory.getLogger(PricingRestClient.class);
    private static final String QUOTE_PATH = "/api/v1/pricing/quotes/{quoteId}";
    private static final String QUOTES_PATH = "/api/v1/pricing/quotes";

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

    @Override
    public Quote quote(MerchantQuoteRequest request) {
        PricingOutcome outcome;
        try {
            outcome = circuitBreaker.executeSupplier(() -> price(request));
        } catch (CallNotPermittedException | RestClientException | UnexpectedPricingResponseException e) {
            log.warn("Merchant order could not be priced: {}", e.getClass().getSimpleName());
            throw new PricingUnavailableException(e);
        }
        return switch (outcome) {
            case PricingOutcome.Priced priced -> priced.quote();
            case PricingOutcome.Refused refused -> throw new OrderNotPriceableException(refused.code(), refused.detail());
        };
    }

    /** A refusal is returned, not thrown, so the circuit breaker counts it as a successful call. */
    private PricingOutcome price(MerchantQuoteRequest request) {
        return client.post()
                .uri(QUOTES_PATH)
                .headers(PricingRestClient::relayCorrelationId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(QuoteRequestBody.from(request))
                .exchange((http, response) -> {
                    HttpStatusCode status = response.getStatusCode();
                    if (status.isSameCodeAs(HttpStatus.UNPROCESSABLE_CONTENT) || status.isSameCodeAs(HttpStatus.BAD_REQUEST)) {
                        Problem problem = Optional.ofNullable(response.bodyTo(Problem.class)).orElse(new Problem(null, null));
                        return new PricingOutcome.Refused(problem.code(), problem.detail());
                    }
                    if (!status.is2xxSuccessful()) {
                        throw new UnexpectedPricingResponseException("status " + status.value());
                    }
                    QuoteResponse body = response.bodyTo(QuoteResponse.class);
                    return new PricingOutcome.Priced(Optional.ofNullable(body).flatMap(QuoteResponse::toQuote)
                            .orElseThrow(() -> new UnexpectedPricingResponseException("incomplete quote")));
                });
    }

    private sealed interface PricingOutcome {
        record Priced(Quote quote) implements PricingOutcome { }
        record Refused(String code, String detail) implements PricingOutcome { }
    }

    /** pricing-service's QuoteRequest (contracts/openapi/pricing-service.v1.yaml). */
    record QuoteRequestBody(String fromCity, String fromDistrict, String toCity, String toDistrict,
                                    BigDecimal weightKg, SizeClass sizeClass, ServiceLevel serviceLevel,
                                    BigDecimal codAmount) {
        static QuoteRequestBody from(MerchantQuoteRequest r) {
            return new QuoteRequestBody(r.fromCity(), r.fromDistrict(), r.toCity(), r.toDistrict(), r.weightKg(),
                    r.sizeClass(), r.serviceLevel(), r.codAmount());
        }
    }

    /** The parts of pricing-service's problem+json a merchant can act on. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Problem(String code, String detail) { }

    private static void relayCaller(HttpHeaders headers) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            headers.setBearerAuth(token.getToken().getTokenValue());
        }
        relayCorrelationId(headers);
    }

    /** Tracing only: no credentials. */
    private static void relayCorrelationId(HttpHeaders headers) {
        CorrelationId.current().ifPresent(id -> headers.set(CorrelationId.HEADER, id));
    }

    /** pricing-service answered, but not with something a booking can rely on. */
    private static final class UnexpectedPricingResponseException extends RuntimeException {

        private UnexpectedPricingResponseException(String reason) {
            super("Unexpected answer from pricing-service: " + reason);
        }
    }
}
