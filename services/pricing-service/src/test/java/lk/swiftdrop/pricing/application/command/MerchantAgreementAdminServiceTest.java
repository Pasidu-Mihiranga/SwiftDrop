package lk.swiftdrop.pricing.application.command;

import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.common.web.error.ConflictException;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.pricing.application.port.out.MerchantAgreementRepository;
import lk.swiftdrop.pricing.application.port.out.ZoneRepository;
import lk.swiftdrop.pricing.domain.model.LaneOverride;
import lk.swiftdrop.pricing.domain.model.MerchantAgreement;
import lk.swiftdrop.pricing.domain.model.ZoneCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MerchantAgreementAdminServiceTest {

    private static final LocalDate JUNE = LocalDate.of(2026, 6, 1);
    private static final UUID MERCHANT = UUID.randomUUID();
    private static final ZoneCode WP = ZoneCode.of("WP");
    private static final ZoneCode CP = ZoneCode.of("CP");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-15T09:00:00Z"), ZoneId.of("Asia/Colombo"));

    private MerchantAgreementRepository agreements;
    private ZoneRepository zones;
    private MerchantAgreementAdminService service;

    @BeforeEach
    void setUp() {
        agreements = mock(MerchantAgreementRepository.class);
        zones = mock(ZoneRepository.class);
        when(zones.existsByCode(any())).thenReturn(true);
        service = new MerchantAgreementAdminService(agreements, zones, CLOCK);
    }

    private static MerchantAgreement terms(LaneOverride... lanes) {
        return new MerchantAgreement(null, MERCHANT, new BigDecimal("0.1000"), BigDecimal.ZERO,
                false, false, false, List.of(lanes));
    }

    private static LaneOverride lane() {
        return new LaneOverride(WP, CP, new BigDecimal("0.2000"), null);
    }

    @Test
    void listsWhoHasTermsTodayInTheServicesOwnTimeZone() {
        service.listInForce();

        verify(agreements).findAllInForce(LocalDate.of(2026, 6, 15));
    }

    @Test
    void publishesValidTerms() {
        MerchantAgreement candidate = terms(lane());
        when(agreements.publish(candidate, JUNE)).thenReturn(candidate);

        assertThat(service.publish(candidate, JUNE)).isSameAs(candidate);
    }

    @Test
    void refusesALaneInAZoneThatDoesNotExist() {
        when(zones.existsByCode(CP)).thenReturn(false);

        assertThatThrownBy(() -> service.publish(terms(lane()), JUNE))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(agreements, never()).publish(any(), any());
    }

    @Test
    void refusesTheSameLaneTwiceRatherThanHittingTheUniqueConstraint() {
        assertThatThrownBy(() -> service.publish(terms(lane(), lane()), JUNE))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(agreements, never()).publish(any(), any());
    }

    @Test
    void refusesAPeriodThatDoesNotFollowTheNewestOne() {
        when(agreements.hasPeriodStartingOnOrAfter(MERCHANT, JUNE)).thenReturn(true);

        assertThatThrownBy(() -> service.publish(terms(), JUNE))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("later date");
        verify(agreements, never()).publish(any(), any());
    }
}
