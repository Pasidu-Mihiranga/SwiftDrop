package lk.swiftdrop.pricing.application.query;

import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.pricing.application.model.Paged;
import lk.swiftdrop.pricing.application.model.RateCardFilter;
import lk.swiftdrop.pricing.application.port.out.RateCardRepository;
import lk.swiftdrop.pricing.domain.model.RateCard;
import lk.swiftdrop.pricing.domain.model.ZoneCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateCardQueryServiceTest {

    private final RateCardRepository rateCards = mock(RateCardRepository.class);
    private final RateCardQueryService service = new RateCardQueryService(rateCards, org.mockito.Mockito.mock(lk.swiftdrop.pricing.application.port.out.WeightTierRepository.class));

    @Test
    void passesTheFilterAndPageStraightThrough() {
        RateCardFilter filter = new RateCardFilter(ZoneCode.of("WP"), null, LocalDate.of(2026, 6, 1));
        when(rateCards.findPage(filter, 1, 20)).thenReturn(new Paged<>(List.of(), 1, 20, 81));

        Paged<RateCard> page = service.list(filter, 1, 20);

        assertThat(page.totalElements()).isEqualTo(81);
        assertThat(page.totalPages()).isEqualTo(5);
    }

    @Test
    void reportsAnUnknownCardAsNotFound() {
        UUID missing = UUID.randomUUID();
        when(rateCards.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.find(missing))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(missing.toString());
    }

    @Test
    void returnsAKnownCard() {
        RateCard card = new RateCard(UUID.randomUUID(), ZoneCode.of("WP"), ZoneCode.of("CP"),
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO,
                LocalDate.of(2026, 1, 1), null);
        when(rateCards.findById(card.id())).thenReturn(Optional.of(card));

        assertThat(service.find(card.id())).isEqualTo(card);
    }
}
