package lk.swiftdrop.pricing.application.command;

import lk.swiftdrop.pricing.domain.model.SizeClassRate;
import lk.swiftdrop.pricing.domain.model.Dimensions;
import lk.swiftdrop.pricing.application.model.QuoteSnapshot;
import lk.swiftdrop.pricing.application.port.out.PricingMetrics;
import lk.swiftdrop.pricing.application.port.out.QuoteStore;
import lk.swiftdrop.pricing.application.query.LaneDistanceService;
import lk.swiftdrop.pricing.application.query.PreparedQuote;
import lk.swiftdrop.pricing.application.query.QuotePricingInput;
import lk.swiftdrop.pricing.application.query.QuotePricingService;
import lk.swiftdrop.pricing.domain.model.FeeBreakdown;
import lk.swiftdrop.pricing.domain.model.PricingRule;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;
import lk.swiftdrop.pricing.domain.model.ZoneCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.pricing.domain.model.PricingSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuoteIssuanceServiceTest {

    private final LaneDistanceService laneDistances = mock(LaneDistanceService.class);

    @BeforeEach
    void nothingToMeasure() {
        // None of these quotes is a same-day job, so no lane is ever measured.
        when(laneDistances.measure(any(), any(), any(), any(), any(), any())).thenReturn(Optional.empty());
    }

    private static final SizeClassRate MEDIUM = new SizeClassRate(SizeClassCode.M,
            new BigDecimal("5.00"), Dimensions.of("40", "30", "20"), new BigDecimal("1.150"));

    private static final Instant NOW = Instant.parse("2026-10-24T20:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Asia/Colombo"));
    private static final Duration TTL = Duration.ofMinutes(15);
    private static final QuotePricingInput INPUT = new QuotePricingInput("Colombo", "Kandy",
            new BigDecimal("2"), SizeClassCode.M, ServiceLevel.STANDARD, BigDecimal.ZERO);

    private final QuotePricingService pricing = mock(QuotePricingService.class);
    private final QuoteStore store = mock(QuoteStore.class);
    private final PricingMetrics metrics = mock(PricingMetrics.class);

    @Test
    void storesTheExactIssuedSnapshotWithConfiguredExpiryAndLocalQuoteDate() {
        FeeBreakdown breakdown = new FeeBreakdown(
                List.of(new FeeBreakdown.Line(PricingRule.BASE, new BigDecimal("475.00"))),
                new BigDecimal("475.00"));
        when(pricing.prepare(eq(INPUT), eq(LocalDate.of(2026, 10, 25)), any(), any()))
                .thenReturn(new PreparedQuote(INPUT, ZoneCode.of("WP"), ZoneCode.of("CP"), breakdown, MEDIUM));
        QuoteIssuanceService service = new QuoteIssuanceService(pricing, laneDistances, store, metrics, TTL, CLOCK);

        QuoteSnapshot returned = service.issue(INPUT);

        ArgumentCaptor<QuoteSnapshot> saved = ArgumentCaptor.forClass(QuoteSnapshot.class);
        verify(store).save(saved.capture(), org.mockito.ArgumentMatchers.eq(TTL));
        assertThat(saved.getValue()).isEqualTo(returned);
        assertThat(returned.quoteId()).isNotNull();
        assertThat(returned.fee()).isEqualTo(new QuoteSnapshot.Money(new BigDecimal("475.00"), "LKR"));
        assertThat(returned.fromZone()).isEqualTo("WP");
        assertThat(returned.toZone()).isEqualTo("CP");
        assertThat(returned.issuedAt()).isEqualTo(NOW);
        assertThat(returned.expiresAt()).isEqualTo(NOW.plus(TTL));
    }

    @Test
    void rejectsNonPositiveQuoteLifetime() {
        assertThatThrownBy(() -> new QuoteIssuanceService(pricing, laneDistances, store, metrics, Duration.ZERO, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QuoteIssuanceService(pricing, laneDistances, store, metrics, Duration.ofSeconds(-1), CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void redisLifetimeDoesNotExtendBeyondTheReportedExpiry() {
        Clock movingClock = mock(Clock.class);
        when(movingClock.instant()).thenReturn(NOW, NOW.plusSeconds(2));
        when(movingClock.getZone()).thenReturn(ZoneId.of("Asia/Colombo"));
        FeeBreakdown breakdown = new FeeBreakdown(List.of(), new BigDecimal("0.00"));
        when(pricing.prepare(eq(INPUT), eq(LocalDate.of(2026, 10, 25)), any(), any()))
                .thenReturn(new PreparedQuote(INPUT, ZoneCode.of("WP"), ZoneCode.of("CP"), breakdown, MEDIUM));

        QuoteSnapshot returned = new QuoteIssuanceService(pricing, laneDistances, store, metrics, TTL, movingClock).issue(INPUT);

        verify(store).save(returned, TTL.minusSeconds(2));
        assertThat(returned.expiresAt()).isEqualTo(NOW.plus(TTL));
    }

    @Test
    void countsTheQuoteOnlyAfterItHasBeenStored() {
        FeeBreakdown breakdown = new FeeBreakdown(
                List.of(new FeeBreakdown.Line(PricingRule.BASE, new BigDecimal("475.00"))),
                new BigDecimal("475.00"));
        when(pricing.prepare(eq(INPUT), eq(LocalDate.of(2026, 10, 25)), any(), any()))
                .thenReturn(new PreparedQuote(INPUT, ZoneCode.of("WP"), ZoneCode.of("CP"), breakdown, MEDIUM));

        new QuoteIssuanceService(pricing, laneDistances, store, metrics, TTL, CLOCK).issue(INPUT);

        verify(metrics).quoteIssued(ServiceLevel.STANDARD, SizeClassCode.M, new BigDecimal("475.00"));
    }

    @Test
    void doesNotCountAQuoteThatCouldNotBeStored() {
        FeeBreakdown breakdown = new FeeBreakdown(
                List.of(new FeeBreakdown.Line(PricingRule.BASE, new BigDecimal("475.00"))),
                new BigDecimal("475.00"));
        when(pricing.prepare(eq(INPUT), eq(LocalDate.of(2026, 10, 25)), any(), any()))
                .thenReturn(new PreparedQuote(INPUT, ZoneCode.of("WP"), ZoneCode.of("CP"), breakdown, MEDIUM));
        doThrow(new IllegalStateException("redis is down")).when(store).save(any(), any());

        assertThatThrownBy(() -> new QuoteIssuanceService(pricing, laneDistances, store, metrics, TTL, CLOCK).issue(INPUT))
                .isInstanceOf(IllegalStateException.class);

        // A quote nobody can use must not inflate the business meter.
        verify(metrics, never()).quoteIssued(any(), any(), any());
    }

    // NOW is 01:30 on 25 October in Colombo.
    private static final QuotePricingInput SAME_DAY = new QuotePricingInput("Colombo", "Colombo",
            new BigDecimal("2"), SizeClassCode.M, ServiceLevel.SAME_DAY, BigDecimal.ZERO);

    private QuoteIssuanceService withCutoff(LocalTime cutoff) {
        PricingSettings settings = new PricingSettings(UUID.randomUUID(), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, cutoff);
        return new QuoteIssuanceService(pricing, laneDistances, store, metrics, TTL, CLOCK,
                on -> on.equals(LocalDate.of(2026, 10, 25)) ? Optional.of(settings) : Optional.empty());
    }

    @Test
    void refusesSameDayFromTheLocalCutoffWithoutMeasuringTheLane() {
        assertThatThrownBy(() -> withCutoff(LocalTime.of(1, 30)).issue(SAME_DAY))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class, error -> {
                    assertThat(error.code()).isEqualTo("SAME_DAY_CUTOFF_PASSED");
                    assertThat(error.getMessage()).contains("01:30").contains("Asia/Colombo");
                });

        verify(laneDistances, never()).measure(any(), any(), any(), any(), any(), any());
        verify(store, never()).save(any(), any());
    }

    @Test
    void takesSameDayBeforeTheCutoffAndOtherServicesAfterIt() {
        FeeBreakdown breakdown = new FeeBreakdown(List.of(), new BigDecimal("0.00"));
        when(pricing.prepare(any(), any(), any(), any())).thenAnswer(call -> new PreparedQuote(
                call.getArgument(0), ZoneCode.of("WP"), ZoneCode.of("WP"), breakdown, MEDIUM));

        assertThat(withCutoff(LocalTime.of(1, 31)).issue(SAME_DAY).serviceLevel()).isEqualTo(ServiceLevel.SAME_DAY);
        assertThat(withCutoff(LocalTime.of(1, 0)).issue(INPUT).serviceLevel()).isEqualTo(ServiceLevel.STANDARD);
        assertThat(withCutoff(null).issue(SAME_DAY).serviceLevel()).isEqualTo(ServiceLevel.SAME_DAY);
    }
}
