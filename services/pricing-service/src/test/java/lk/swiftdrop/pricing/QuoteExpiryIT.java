package lk.swiftdrop.pricing;

import lk.swiftdrop.pricing.application.command.QuoteIssuanceService;
import lk.swiftdrop.pricing.application.model.QuoteSnapshot;
import lk.swiftdrop.pricing.application.query.QuoteLookupService;
import lk.swiftdrop.pricing.application.query.QuotePricingInput;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Quote lifetime, driven by a controlled clock rather than by waiting, so the assertions are exact
 * instead of merely likely. Redis enforces the same window independently through the key's TTL.
 */
@Testcontainers
@SpringBootTest(properties = "swiftdrop.pricing.quote-ttl=PT15M")
class QuoteExpiryIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.15-alpine");
    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7.4.11-alpine").withExposedPorts(6379);

    private static final Instant ISSUED_AT = Instant.parse("2026-06-01T09:00:00Z");

    @MockitoBean
    private Clock clock;

    @Autowired
    private QuoteIssuanceService issuance;
    @Autowired
    private QuoteLookupService lookup;
    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void freezeTheClock() {
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Colombo"));
        when(clock.instant()).thenReturn(ISSUED_AT);
    }

    private QuoteSnapshot issueColomboToKandy() {
        return issuance.issue(new QuotePricingInput("Colombo", "Kandy", new BigDecimal("2"),
                SizeClassCode.M, ServiceLevel.STANDARD, BigDecimal.ZERO));
    }

    @Test
    void aQuoteExpiresExactlyOneTtlAfterItWasIssued() {
        QuoteSnapshot issued = issueColomboToKandy();

        assertThat(issued.issuedAt()).isEqualTo(ISSUED_AT);
        assertThat(issued.expiresAt()).isEqualTo(ISSUED_AT.plus(Duration.ofMinutes(15)));
    }

    @Test
    void theQuoteIsReturnedUnchangedForItsWholeLifetime() {
        QuoteSnapshot issued = issueColomboToKandy();

        when(clock.instant()).thenReturn(issued.expiresAt().minusMillis(1));

        assertThat(lookup.get(issued.quoteId())).isEqualTo(issued);
    }

    @Test
    void lookupStopsWorkingTheInstantTheQuoteExpires() {
        QuoteSnapshot issued = issueColomboToKandy();

        when(clock.instant()).thenReturn(issued.expiresAt());

        assertThatThrownBy(() -> lookup.get(issued.quoteId()))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting("code").isEqualTo("QUOTE_NOT_FOUND");
    }

    @Test
    void anUnknownQuoteAnswersTheSameWayAsAnExpiredOneSoExpiryIsNotDetectable() {
        QuoteSnapshot issued = issueColomboToKandy();
        when(clock.instant()).thenReturn(issued.expiresAt());

        Throwable expired = org.assertj.core.api.Assertions.catchThrowable(() -> lookup.get(issued.quoteId()));
        Throwable unknown = org.assertj.core.api.Assertions.catchThrowable(() -> lookup.get(UUID.randomUUID()));

        assertThat(expired).isInstanceOf(ResourceNotFoundException.class);
        assertThat(unknown).isInstanceOf(ResourceNotFoundException.class);
        assertThat(expired).extracting("code").isEqualTo(((ResourceNotFoundException) unknown).code());
    }

    @Test
    void redisIsToldToDropTheKeyAfterTheSameWindow() {
        QuoteSnapshot issued = issueColomboToKandy();

        Long ttlSeconds = redis.getExpire("pricing:quote:" + issued.quoteId(), TimeUnit.SECONDS);

        assertThat(ttlSeconds).isNotNull().isBetween(1L, Duration.ofMinutes(15).toSeconds());
    }
}
