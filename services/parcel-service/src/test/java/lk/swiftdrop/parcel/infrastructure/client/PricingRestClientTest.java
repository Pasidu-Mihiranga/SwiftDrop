package lk.swiftdrop.parcel.infrastructure.client;

import lk.swiftdrop.parcel.PricingStub;
import lk.swiftdrop.parcel.application.exception.PricingUnavailableException;
import lk.swiftdrop.parcel.application.model.Quote;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PricingRestClientTest {

    private static final Instant EXPIRES_AT = Instant.parse("2026-10-20T08:15:00Z");

    private final PricingStub pricing = new PricingStub();
    private final UUID quoteId = UUID.randomUUID();
    private PricingRestClient client;

    @BeforeEach
    void setUp() {
        client = new PricingRestClient(properties(pricing.baseUrl(), 100, 100));
    }

    @AfterEach
    void tearDown() {
        pricing.close();
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    /** The circuit needs {@code minimumCalls} calls in its window before it can open. */
    private static PricingProperties properties(String baseUrl, int slidingWindowSize, int minimumCalls) {
        return new PricingProperties(baseUrl, Duration.ofMillis(500), Duration.ofMillis(300), 50f,
                slidingWindowSize, minimumCalls, Duration.ofMinutes(1));
    }

    @Test
    void readsTheFieldsABookingNeedsAndIgnoresTheRest() {
        pricing.validQuote(quoteId, EXPIRES_AT);

        Optional<Quote> quote = client.findQuote(quoteId);

        assertThat(quote).hasValueSatisfying(found -> {
            assertThat(found.quoteId()).isEqualTo(quoteId);
            assertThat(found.fee().amount()).isEqualByComparingTo("650.00");
            assertThat(found.fee().currency()).isEqualTo("LKR");
            assertThat(found.fromZone()).isEqualTo("WP-COL");
            assertThat(found.toDistrict()).isEqualTo("Kandy");
            assertThat(found.weightKg()).isEqualByComparingTo("2");
            assertThat(found.expiresAt()).isEqualTo(EXPIRES_AT);
        });
    }

    @Test
    void relaysTheCallersTokenAndTheCorrelationId() {
        pricing.validQuote(quoteId, EXPIRES_AT);
        Jwt jwt = Jwt.withTokenValue("caller-token").header("alg", "none").subject("cust-1").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        MDC.put("correlationId", "corr-12345678");

        client.findQuote(quoteId);

        assertThat(pricing.calls()).singleElement().satisfies(call -> {
            assertThat(call.path()).isEqualTo("/api/v1/pricing/quotes/" + quoteId);
            assertThat(call.authorization()).isEqualTo("Bearer caller-token");
            assertThat(call.correlationId()).isEqualTo("corr-12345678");
        });
    }

    @Test
    void anUnknownOrExpiredQuoteIsAnEmptyAnswerNotAFailure() {
        assertThat(client.findQuote(quoteId)).isEmpty();
    }

    @Test
    void aServerErrorMeansPricingIsUnavailable() {
        pricing.answer(quoteId, 500, "{}");

        assertThatThrownBy(() -> client.findQuote(quoteId)).isInstanceOf(PricingUnavailableException.class);
    }

    @Test
    void aSlowAnswerTimesOut() {
        pricing.slowQuote(quoteId, Duration.ofSeconds(2));

        assertThatThrownBy(() -> client.findQuote(quoteId)).isInstanceOf(PricingUnavailableException.class);
    }

    @Test
    void anAnswerWithoutAFeeCannotBeBookedFrom() {
        pricing.answer(quoteId, 200, "{\"quoteId\":\"" + quoteId + "\"}");

        assertThatThrownBy(() -> client.findQuote(quoteId)).isInstanceOf(PricingUnavailableException.class);
    }

    @Test
    void anUnreachableServiceMeansPricingIsUnavailable() {
        PricingRestClient nobodyHome = new PricingRestClient(properties("http://127.0.0.1:1", 100, 100));

        assertThatThrownBy(() -> nobodyHome.findQuote(quoteId)).isInstanceOf(PricingUnavailableException.class);
    }

    @Test
    void afterRepeatedFailuresTheCircuitOpensAndStopsCalling() {
        PricingRestClient fragile = new PricingRestClient(properties(pricing.baseUrl(), 2, 2));
        pricing.answer(quoteId, 500, "{}");
        assertThatThrownBy(() -> fragile.findQuote(quoteId)).isInstanceOf(PricingUnavailableException.class);
        assertThatThrownBy(() -> fragile.findQuote(quoteId)).isInstanceOf(PricingUnavailableException.class);
        int callsBeforeOpen = pricing.calls().size();

        assertThatThrownBy(() -> fragile.findQuote(quoteId)).isInstanceOf(PricingUnavailableException.class);

        assertThat(callsBeforeOpen).isEqualTo(2);
        assertThat(pricing.calls()).hasSize(callsBeforeOpen);
    }

    @Test
    void notFoundAnswersDoNotOpenTheCircuit() {
        PricingRestClient fragile = new PricingRestClient(properties(pricing.baseUrl(), 2, 2));

        for (int i = 0; i < 5; i++) {
            assertThat(fragile.findQuote(UUID.randomUUID())).isEmpty();
        }

        assertThat(pricing.calls()).hasSize(5);
    }
}
