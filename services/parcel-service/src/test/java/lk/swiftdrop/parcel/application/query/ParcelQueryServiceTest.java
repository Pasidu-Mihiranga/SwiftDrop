package lk.swiftdrop.parcel.application.query;

import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.parcel.ParcelFixtures;
import lk.swiftdrop.parcel.application.model.PagedResult;
import lk.swiftdrop.parcel.application.port.out.ParcelRepository;
import lk.swiftdrop.parcel.domain.model.Parcel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParcelQueryServiceTest {

    @Mock
    private ParcelRepository parcels;
    @InjectMocks
    private ParcelQueryService service;

    @Test
    void listsOnlyTheCallersOwnParcels() {
        Parcel mine = ParcelFixtures.bookedBy("cust-1");
        when(parcels.findByCustomer("cust-1", ParcelStatus.CREATED, 0, 20))
                .thenReturn(new PagedResult<>(List.of(mine), 0, 20, 1, 1));

        PagedResult<Parcel> page = service.mine(ParcelFixtures.customer("cust-1"), ParcelStatus.CREATED, 0, 20);

        assertThat(page.content()).containsExactly(mine);
    }

    @Test
    void theOwnerOpensTheParcel() {
        Parcel parcel = ParcelFixtures.bookedBy("cust-1");
        when(parcels.findById(parcel.id())).thenReturn(Optional.of(parcel));

        assertThat(service.get(ParcelFixtures.customer("cust-1"), parcel.id())).isEqualTo(parcel);
    }

    @Test
    void aRefusedCallerAndAMissingIdGetTheSameError() {
        Parcel parcel = ParcelFixtures.bookedBy("cust-1");
        UUID missing = UUID.randomUUID();
        when(parcels.findById(parcel.id())).thenReturn(Optional.of(parcel));
        when(parcels.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(ParcelFixtures.customer("cust-2"), parcel.id()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "PARCEL_NOT_FOUND");
        assertThatThrownBy(() -> service.get(ParcelFixtures.customer("cust-2"), missing))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "PARCEL_NOT_FOUND");
    }
}
