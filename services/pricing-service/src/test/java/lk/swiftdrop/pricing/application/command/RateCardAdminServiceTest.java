package lk.swiftdrop.pricing.application.command;

import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.common.web.error.ConflictException;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.pricing.application.port.out.WeightTierRepository;
import lk.swiftdrop.pricing.application.port.out.RateCardRepository;
import lk.swiftdrop.pricing.application.port.out.ZoneRepository;
import lk.swiftdrop.pricing.domain.model.RateCard;
import lk.swiftdrop.pricing.domain.model.ZoneCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateCardAdminServiceTest {

    private static final ZoneCode WESTERN = ZoneCode.of("WP");
    private static final ZoneCode CENTRAL = ZoneCode.of("CP");
    private static final LocalDate JANUARY = LocalDate.of(2026, 1, 1);
    private static final LocalDate JULY = LocalDate.of(2026, 7, 1);

    private RateCardRepository rateCards;
    private ZoneRepository zones;
    private RateCardAdminService service;

    @BeforeEach
    void setUp() {
        rateCards = mock(RateCardRepository.class);
        zones = mock(ZoneRepository.class);
        service = new RateCardAdminService(rateCards, zones, mock(WeightTierRepository.class));
        when(zones.existsByCode(any())).thenReturn(true);
        when(rateCards.findByPair(any(), any())).thenReturn(List.of());
        when(rateCards.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static RateCard card(LocalDate from, LocalDate to) {
        return new RateCard(UUID.randomUUID(), WESTERN, CENTRAL, new BigDecimal("475.00"),
                new BigDecimal("78.00"), new BigDecimal("1.500"), new BigDecimal("0.0150"), from, to);
    }

    private RateCard create(LocalDate from, LocalDate to) {
        return service.create(WESTERN, CENTRAL, new BigDecimal("475.00"), new BigDecimal("78.00"),
                new BigDecimal("1.500"), new BigDecimal("0.0150"), from, to);
    }

    @Test
    void createsACardForALaneWithNoExistingRates() {
        RateCard created = create(JANUARY, null);

        assertThat(created.fromZone()).isEqualTo(WESTERN);
        assertThat(created.effectiveFrom()).isEqualTo(JANUARY);
        verify(rateCards).save(any());
    }

    @Test
    void allowsACardForALaneWithinASingleZone() {
        assertThat(service.create(WESTERN, WESTERN, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ZERO, JANUARY, null)).isNotNull();
    }

    @Test
    void refusesACardWhoseOriginOrDestinationZoneIsUnknown() {
        when(zones.existsByCode(CENTRAL)).thenReturn(false);

        assertThatThrownBy(() -> create(JANUARY, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("CP");
        verify(rateCards, never()).save(any());
    }

    @Test
    void refusesACardThatOverlapsOneAlreadyInForce() {
        when(rateCards.findByPair(WESTERN, CENTRAL)).thenReturn(List.of(card(JANUARY, null)));

        assertThatThrownBy(() -> create(JULY, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already in force");
        verify(rateCards, never()).save(any());
    }

    @Test
    void allowsACardThatStartsExactlyWhenThePreviousOneEndsBecausePeriodsAreHalfOpen() {
        when(rateCards.findByPair(WESTERN, CENTRAL)).thenReturn(List.of(card(JANUARY, JULY)));

        assertThat(create(JULY, null)).isNotNull();
        verify(rateCards).save(any());
    }

    @Test
    void refusesACardThatStartsOneDayBeforeThePreviousOneEnds() {
        when(rateCards.findByPair(WESTERN, CENTRAL)).thenReturn(List.of(card(JANUARY, JULY)));

        assertThatThrownBy(() -> create(JULY.minusDays(1), null)).isInstanceOf(ConflictException.class);
    }

    @Test
    void refusesACardThatFullyEnclosesAnExistingPeriod() {
        when(rateCards.findByPair(WESTERN, CENTRAL)).thenReturn(List.of(card(JANUARY, JULY)));

        assertThatThrownBy(() -> create(JANUARY.minusMonths(1), JULY.plusMonths(1)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void allowsAClosedHistoricCardAlongsideACurrentOne() {
        when(rateCards.findByPair(WESTERN, CENTRAL)).thenReturn(List.of(card(JANUARY, JULY)));

        assertThat(create(JANUARY.minusYears(1), JANUARY)).isNotNull();
    }

    @Test
    void reportsInvalidFeesAsABrokenRuleRatherThanAServerError() {
        assertThatThrownBy(() -> service.create(WESTERN, CENTRAL, new BigDecimal("-1"), BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ZERO, JANUARY, null))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("base fee");
    }

    @Test
    void closesACardByEndingItsPeriod() {
        RateCard open = card(JANUARY, null);
        when(rateCards.findById(open.id())).thenReturn(Optional.of(open));

        RateCard closed = service.close(open.id(), JULY);

        assertThat(closed.effectiveTo()).isEqualTo(JULY);
        assertThat(closed.baseFee()).isEqualByComparingTo(open.baseFee());
    }

    @Test
    void refusesToCloseACardBeforeItStarted() {
        RateCard open = card(JULY, null);
        when(rateCards.findById(open.id())).thenReturn(Optional.of(open));

        assertThatThrownBy(() -> service.close(open.id(), JANUARY))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("must end after");
        assertThatThrownBy(() -> service.close(open.id(), JULY))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> service.close(open.id(), null))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(rateCards, never()).save(any());
    }

    @Test
    void reportsAnUnknownCardAsNotFound() {
        UUID missing = UUID.randomUUID();
        when(rateCards.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.close(missing, JULY)).isInstanceOf(ResourceNotFoundException.class);
    }
}
