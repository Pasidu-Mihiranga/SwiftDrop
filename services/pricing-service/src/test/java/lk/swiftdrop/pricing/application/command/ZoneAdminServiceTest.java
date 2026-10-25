package lk.swiftdrop.pricing.application.command;

import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.common.web.error.ConflictException;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.pricing.application.port.out.DistrictAssignmentRepository;
import lk.swiftdrop.pricing.application.port.out.ZoneRepository;
import lk.swiftdrop.pricing.domain.model.DistrictAssignment;
import lk.swiftdrop.pricing.domain.model.Zone;
import lk.swiftdrop.pricing.domain.model.ZoneCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

class ZoneAdminServiceTest {

    private static final ZoneCode CENTRAL = ZoneCode.of("CP");

    private ZoneRepository zones;
    private DistrictAssignmentRepository districts;
    private ZoneAdminService service;

    @BeforeEach
    void setUp() {
        zones = mock(ZoneRepository.class);
        districts = mock(DistrictAssignmentRepository.class);
        service = new ZoneAdminService(zones, districts);
    }

    @Test
    void createsAZoneWhenTheCodeIsFree() {
        Zone saved = new Zone(UUID.randomUUID(), CENTRAL, "Central", true, List.of());
        when(zones.existsByCode(CENTRAL)).thenReturn(false);
        when(zones.save(any())).thenReturn(saved);

        assertThat(service.createZone(CENTRAL, "Central", true)).isEqualTo(saved);
    }

    @Test
    void refusesToCreateADuplicateZoneCode() {
        when(zones.existsByCode(CENTRAL)).thenReturn(true);

        assertThatThrownBy(() -> service.createZone(CENTRAL, "Central", true))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("CP");
        verify(zones, never()).save(any());
    }

    @Test
    void reportsAnUnknownZoneAsNotFound() {
        UUID missing = UUID.randomUUID();
        when(zones.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findZone(missing)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void renamesAZoneWithoutTouchingItsCodeOrCoverage() {
        UUID id = UUID.randomUUID();
        Zone existing = new Zone(id, CENTRAL, "Central", true, List.of("Kandy"));
        when(zones.findById(id)).thenReturn(Optional.of(existing));
        when(zones.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Zone updated = service.updateZone(id, "Central Province", true);

        assertThat(updated.code()).isEqualTo(CENTRAL);
        assertThat(updated.name()).isEqualTo("Central Province");
        assertThat(updated.districts()).containsExactly("Kandy");
    }

    @Test
    void refusesToDeactivateAZoneThatStillServesDistricts() {
        UUID id = UUID.randomUUID();
        when(zones.findById(id)).thenReturn(Optional.of(new Zone(id, CENTRAL, "Central", true, List.of("Kandy"))));

        assertThatThrownBy(() -> service.updateZone(id, "Central", false))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("districts assigned");
        verify(zones, never()).save(any());
    }

    @Test
    void deactivatesAZoneOnceItsDistrictsHaveMovedAway() {
        UUID id = UUID.randomUUID();
        when(zones.findById(id)).thenReturn(Optional.of(new Zone(id, CENTRAL, "Central", true, List.of())));
        when(zones.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.updateZone(id, "Central", false).active()).isFalse();
    }

    @Test
    void assignsADistrictToAnExistingZone() {
        DistrictAssignment assignment = new DistrictAssignment("Kandy", CENTRAL, "Central");
        when(districts.assign("Kandy", CENTRAL)).thenReturn(Optional.of(assignment));

        assertThat(service.assignDistrict("Kandy", CENTRAL)).isEqualTo(assignment);
    }

    @Test
    void refusesToAssignADistrictToAZoneThatDoesNotExist() {
        when(districts.assign("Kandy", CENTRAL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignDistrict("Kandy", CENTRAL))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("CP");
    }
}
