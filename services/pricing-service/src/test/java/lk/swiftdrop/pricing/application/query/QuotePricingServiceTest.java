package lk.swiftdrop.pricing.application.query;

import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.pricing.application.port.out.DistrictAssignmentRepository;
import lk.swiftdrop.pricing.application.port.out.SizeClassRepository;
import lk.swiftdrop.pricing.domain.model.RateCard;
import lk.swiftdrop.pricing.domain.model.ServiceLevel;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;
import lk.swiftdrop.pricing.domain.model.SizeClassRate;
import lk.swiftdrop.pricing.domain.model.ZoneCode;
import lk.swiftdrop.pricing.domain.policy.FeeCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuotePricingServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 25);
    private static final ZoneCode WP = ZoneCode.of("WP");
    private static final ZoneCode CP = ZoneCode.of("CP");
    private static final QuotePricingInput INPUT = new QuotePricingInput("Colombo", "Kandy",
            new BigDecimal("2.1"), SizeClassCode.M, ServiceLevel.EXPRESS, new BigDecimal("1000"));

    private DistrictAssignmentRepository districts;
    private SizeClassRepository sizeClasses;
    private RateCardSelectionService rateCards;
    private QuotePricingService service;

    @BeforeEach
    void setUp() {
        districts = mock(DistrictAssignmentRepository.class);
        sizeClasses = mock(SizeClassRepository.class);
        rateCards = mock(RateCardSelectionService.class);
        service = new QuotePricingService(districts, sizeClasses, rateCards, new FeeCalculator());
        when(districts.findActiveZoneCodeByDistrict("Colombo")).thenReturn(Optional.of(WP));
        when(districts.findActiveZoneCodeByDistrict("Kandy")).thenReturn(Optional.of(CP));
    }

    @Test
    void resolvesCoverageAndConfiguredRatesWithoutCallerSuppliedPrices() {
        when(sizeClasses.findByCode(SizeClassCode.M)).thenReturn(Optional.of(size("5")));
        when(rateCards.select(WP, CP, TODAY)).thenReturn(card());

        PreparedQuote quote = service.prepare(INPUT, TODAY);

        assertThat(quote.fromZone()).isEqualTo(WP);
        assertThat(quote.toZone()).isEqualTo(CP);
        assertThat(quote.breakdown().fee()).isEqualByComparingTo("1344.38");
        verify(rateCards).select(WP, CP, TODAY);
    }

    @Test
    void rejectsUnservedDestinationBeforeLookingUpRates() {
        when(districts.findActiveZoneCodeByDistrict("Kandy")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.prepare(INPUT, TODAY))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class,
                        error -> assertThat(error.code()).isEqualTo("DISTRICT_NOT_SERVICED"));
        verify(rateCards, never()).select(WP, CP, TODAY);
    }

    @Test
    void rejectsWeightAboveTheConfiguredSizeLimitBeforeLookingUpRates() {
        when(sizeClasses.findByCode(SizeClassCode.M)).thenReturn(Optional.of(size("2")));

        assertThatThrownBy(() -> service.prepare(INPUT, TODAY))
                .isInstanceOfSatisfying(BusinessRuleViolationException.class,
                        error -> assertThat(error.code()).isEqualTo("SIZE_CLASS_WEIGHT_EXCEEDED"));
        verify(rateCards, never()).select(WP, CP, TODAY);
    }

    @Test
    void failsClosedWhenAConfiguredSizeClassIsMissing() {
        when(sizeClasses.findByCode(SizeClassCode.M)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.prepare(INPUT, TODAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Size class M is not configured");
    }

    private static SizeClassRate size(String maxWeight) {
        return new SizeClassRate(SizeClassCode.M, new BigDecimal(maxWeight), null, new BigDecimal("1.25"));
    }

    private static RateCard card() {
        return new RateCard(UUID.randomUUID(), WP, CP, new BigDecimal("475.00"),
                new BigDecimal("78.00"), new BigDecimal("1.500"), new BigDecimal("0.0150"), TODAY, null);
    }
}
