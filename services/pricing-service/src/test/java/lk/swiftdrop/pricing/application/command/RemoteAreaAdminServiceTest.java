package lk.swiftdrop.pricing.application.command;

import lk.swiftdrop.common.web.error.ConflictException;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.pricing.application.port.out.DistrictAssignmentRepository;
import lk.swiftdrop.pricing.application.port.out.RemoteAreaSurchargeRepository;
import lk.swiftdrop.pricing.domain.model.DistrictAssignment;
import lk.swiftdrop.pricing.domain.model.RemoteAreaSurcharge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RemoteAreaAdminServiceTest {

    private static final LocalDate JUNE = LocalDate.of(2026, 6, 1);
    private static final BigDecimal FEE = new BigDecimal("150.00");

    private RemoteAreaSurchargeRepository remoteAreas;
    private DistrictAssignmentRepository districts;
    private RemoteAreaAdminService service;

    @BeforeEach
    void setUp() {
        remoteAreas = mock(RemoteAreaSurchargeRepository.class);
        districts = mock(DistrictAssignmentRepository.class);
        service = new RemoteAreaAdminService(remoteAreas, districts);
        DistrictAssignment mannar = mock(DistrictAssignment.class);
        when(mannar.district()).thenReturn("Mannar");
        when(districts.findByDistrict("mannar")).thenReturn(Optional.of(mannar));
    }

    @Test
    void publishesUnderTheDistrictsOwnSpelling() {
        RemoteAreaSurcharge saved = new RemoteAreaSurcharge("Mannar", FEE, JUNE, null);
        when(remoteAreas.publish("Mannar", FEE, JUNE)).thenReturn(saved);

        assertThat(service.publish(" mannar ", FEE, JUNE)).isSameAs(saved);
    }

    @Test
    void refusesADistrictThatDoesNotExistRatherThanStoringATypo() {
        when(districts.findByDistrict(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publish("Manar", FEE, JUNE))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(remoteAreas, never()).publish(any(), any(), any());
    }

    @Test
    void refusesAPeriodThatDoesNotFollowTheNewestOne() {
        when(remoteAreas.hasPeriodStartingOnOrAfter("Mannar", JUNE)).thenReturn(true);

        assertThatThrownBy(() -> service.publish("mannar", FEE, JUNE))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("later date");
        verify(remoteAreas, never()).publish(any(), any(), any());
    }
}
