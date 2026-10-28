package lk.swiftdrop.pricing.application.query;

import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.pricing.application.port.out.PricingMetrics;
import lk.swiftdrop.pricing.application.port.out.RateCardRepository;
import lk.swiftdrop.pricing.application.port.out.ZoneRepository;
import lk.swiftdrop.pricing.domain.model.RateCard;
import lk.swiftdrop.pricing.domain.model.Zone;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RateCardSelectionServiceTest {

    private static final ZoneCode WESTERN = ZoneCode.of("WP");
    private static final ZoneCode CENTRAL = ZoneCode.of("CP");
    private static final LocalDate QUOTE_DATE = LocalDate.of(2026, 10, 25);

    private ZoneRepository zones;
    private RateCardRepository rateCards;
    private PricingMetrics metrics;
    private RateCardSelectionService service;

    @BeforeEach
    void setUp() {
        zones = mock(ZoneRepository.class);
        rateCards = mock(RateCardRepository.class);
        metrics = mock(PricingMetrics.class);
        service = new RateCardSelectionService(zones, rateCards, metrics);
        when(zones.findByCode(WESTERN)).thenReturn(Optional.of(zone(WESTERN, true)));
        when(zones.findByCode(CENTRAL)).thenReturn(Optional.of(zone(CENTRAL, true)));
    }

    @Test
    void selectsTheLatestApplicableCardRegardlessOfRepositoryOrder() {
        RateCard older = card(WESTERN, CENTRAL, LocalDate.of(2026, 1, 1));
        RateCard newer = card(WESTERN, CENTRAL, LocalDate.of(2026, 7, 1));
        when(rateCards.findInForce(WESTERN, CENTRAL, QUOTE_DATE)).thenReturn(List.of(newer, older));

        assertThat(service.select(WESTERN, CENTRAL, QUOTE_DATE)).isEqualTo(newer);
        verify(rateCards).findInForce(WESTERN, CENTRAL, QUOTE_DATE);
    }

    @Test
    void doesNotReverseTheZonePair() {
        RateCard reverse = card(CENTRAL, WESTERN, LocalDate.of(2026, 1, 1));
        when(rateCards.findInForce(CENTRAL, WESTERN, QUOTE_DATE)).thenReturn(List.of(reverse));

        assertThat(service.select(CENTRAL, WESTERN, QUOTE_DATE)).isEqualTo(reverse);
        verify(rateCards).findInForce(CENTRAL, WESTERN, QUOTE_DATE);
    }

    @Test
    void rejectsAZoneThatIsInactive() {
        when(zones.findByCode(CENTRAL)).thenReturn(Optional.of(zone(CENTRAL, false)));

        assertThatThrownBy(() -> service.select(WESTERN, CENTRAL, QUOTE_DATE))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class,
                        error -> assertThat(error.code()).isEqualTo("DISTRICT_NOT_SERVICED"));
        verify(rateCards, never()).findInForce(WESTERN, CENTRAL, QUOTE_DATE);
    }

    @Test
    void rejectsAZoneThatDoesNotExist() {
        when(zones.findByCode(WESTERN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.select(WESTERN, CENTRAL, QUOTE_DATE))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class,
                        error -> assertThat(error.code()).isEqualTo("DISTRICT_NOT_SERVICED"));
        verify(rateCards, never()).findInForce(WESTERN, CENTRAL, QUOTE_DATE);
    }

    @Test
    void rejectsAZonePairWithoutAnApplicableCard() {
        when(rateCards.findInForce(WESTERN, CENTRAL, QUOTE_DATE)).thenReturn(List.of());

        assertThatThrownBy(() -> service.select(WESTERN, CENTRAL, QUOTE_DATE))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class,
                        error -> assertThat(error.code()).isEqualTo("NO_RATE_CARD"));
    }

    @Test
    void rejectsCardsTiedForTheLatestStartDate() {
        LocalDate latest = LocalDate.of(2026, 7, 1);
        RateCard older = card(WESTERN, CENTRAL, LocalDate.of(2026, 1, 1));
        when(rateCards.findInForce(WESTERN, CENTRAL, QUOTE_DATE))
                .thenReturn(List.of(card(WESTERN, CENTRAL, latest), older, card(WESTERN, CENTRAL, latest)));

        assertThatThrownBy(() -> service.select(WESTERN, CENTRAL, QUOTE_DATE))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class,
                        error -> assertThat(error.code()).isEqualTo("AMBIGUOUS_RATE_CARD"));
    }

    private static Zone zone(ZoneCode code, boolean active) {
        return new Zone(UUID.randomUUID(), code, code.value(), active, List.of());
    }

    private static RateCard card(ZoneCode from, ZoneCode to, LocalDate start) {
        return new RateCard(UUID.randomUUID(), from, to, new BigDecimal("475.00"),
                new BigDecimal("78.00"), new BigDecimal("1.500"), new BigDecimal("0.0150"), start, null);
    }

    @Test
    void countsAMissingRateCardAsAConfigurationFailure() {
        when(rateCards.findInForce(WESTERN, CENTRAL, QUOTE_DATE)).thenReturn(List.of());

        assertThatThrownBy(() -> service.select(WESTERN, CENTRAL, QUOTE_DATE))
                .isInstanceOf(BusinessRuleViolationException.class);

        verify(metrics).rateCardSelectionFailed("NO_RATE_CARD");
    }

    @Test
    void countsAnInactiveZoneSeparatelyFromAMissingCard() {
        when(zones.findByCode(WESTERN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.select(WESTERN, CENTRAL, QUOTE_DATE))
                .isInstanceOf(BusinessRuleViolationException.class);

        verify(metrics).rateCardSelectionFailed("DISTRICT_NOT_SERVICED");
        verify(metrics, never()).rateCardSelectionFailed("NO_RATE_CARD");
    }

    @Test
    void recordsNoFailureWhenACardIsFound() {
        when(rateCards.findInForce(WESTERN, CENTRAL, QUOTE_DATE))
                .thenReturn(List.of(card(WESTERN, CENTRAL, LocalDate.of(2026, 1, 1))));

        service.select(WESTERN, CENTRAL, QUOTE_DATE);

        verifyNoInteractions(metrics);
    }
}
