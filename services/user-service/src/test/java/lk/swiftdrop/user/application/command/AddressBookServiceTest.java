package lk.swiftdrop.user.application.command;

import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.user.application.model.IdentityClaims;
import lk.swiftdrop.user.application.port.out.AddressRepository;
import lk.swiftdrop.user.domain.model.Address;
import lk.swiftdrop.user.domain.model.AddressDetails;
import lk.swiftdrop.user.domain.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AddressBookServiceTest {

    private static final IdentityClaims NIMAL = new IdentityClaims("u-1", "nimal@x.lk", "Nimal", Set.of(UserRole.CUSTOMER));
    private static final AddressDetails HOME = new AddressDetails("Home", "No. 12, Temple Road", null, "Kandy", "Kandy", null, null, null);
    private static final AddressDetails OFFICE = new AddressDetails("Office", "45 Galle Road", null, "Colombo", "Colombo", null, null, null);

    private AddressRepository addresses;
    private UserProfileService profiles;
    private AddressBookService service;

    @BeforeEach
    void setUp() {
        addresses = mock(AddressRepository.class);
        profiles = mock(UserProfileService.class);
        service = new AddressBookService(addresses, profiles);
        when(addresses.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private static Address saved(AddressDetails details, boolean isDefault) {
        return new Address(UUID.randomUUID(), "u-1", details, isDefault, null, null);
    }

    @Test
    void theFirstAddressBecomesTheDefaultAndTheProfileExistsFirst() {
        when(addresses.findByOwner("u-1")).thenReturn(List.of());

        Address address = service.add(NIMAL, HOME, false);

        assertThat(address.isDefault()).isTrue();
        assertThat(address.ownerId()).isEqualTo("u-1");
        verify(profiles).getOrCreate(NIMAL);
        verify(addresses, never()).clearDefault(anyString());
    }

    @Test
    void aLaterAddressIsNotTheDefaultUnlessAsked() {
        when(addresses.findByOwner("u-1")).thenReturn(List.of(saved(HOME, true)));

        assertThat(service.add(NIMAL, OFFICE, false).isDefault()).isFalse();
        verify(addresses, never()).clearDefault(anyString());
    }

    @Test
    void askingForANewDefaultClearsTheOldOneFirst() {
        when(addresses.findByOwner("u-1")).thenReturn(List.of(saved(HOME, true)));

        Address office = service.add(NIMAL, OFFICE, true);

        assertThat(office.isDefault()).isTrue();
        var order = inOrder(addresses);
        order.verify(addresses).clearDefault("u-1");
        order.verify(addresses).save(any());
    }

    @Test
    void refusesAnEleventhAddress() {
        List<Address> ten = IntStream.range(0, Address.MAX_PER_PERSON).mapToObj(i -> saved(HOME, i == 0)).toList();
        when(addresses.findByOwner("u-1")).thenReturn(ten);

        assertThatThrownBy(() -> service.add(NIMAL, OFFICE, false))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("10");
        verify(addresses, never()).save(any());
    }

    @Test
    void updatingKeepsTheDefaultAndCanMoveIt() {
        Address home = saved(HOME, true);
        Address office = saved(OFFICE, false);
        when(addresses.findOwned(home.id(), "u-1")).thenReturn(Optional.of(home));
        when(addresses.findOwned(office.id(), "u-1")).thenReturn(Optional.of(office));

        assertThat(service.update("u-1", home.id(), OFFICE, false).isDefault()).isTrue();
        verify(addresses, never()).clearDefault(anyString());

        assertThat(service.update("u-1", office.id(), OFFICE, true).isDefault()).isTrue();
        verify(addresses).clearDefault("u-1");
    }

    @Test
    void someoneElsesAddressIsNotFound() {
        UUID theirs = UUID.randomUUID();
        when(addresses.findOwned(theirs, "u-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("u-1", theirs, HOME, false)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.delete("u-1", theirs)).isInstanceOf(ResourceNotFoundException.class);
        verify(addresses, never()).delete(any());
    }

    @Test
    void deletingTheDefaultPromotesTheOldestRemainingAddress() {
        Address home = saved(HOME, true);
        Address office = saved(OFFICE, false);
        when(addresses.findOwned(home.id(), "u-1")).thenReturn(Optional.of(home));
        when(addresses.findByOwner("u-1")).thenReturn(List.of(office));

        service.delete("u-1", home.id());

        verify(addresses).delete(home);
        ArgumentCaptor<Address> promoted = ArgumentCaptor.forClass(Address.class);
        verify(addresses).save(promoted.capture());
        assertThat(promoted.getValue().id()).isEqualTo(office.id());
        assertThat(promoted.getValue().isDefault()).isTrue();
    }

    @Test
    void deletingAnotherAddressOrTheLastOneChangesNothingElse() {
        Address office = saved(OFFICE, false);
        Address only = saved(HOME, true);
        when(addresses.findOwned(office.id(), "u-1")).thenReturn(Optional.of(office));
        when(addresses.findOwned(only.id(), "u-1")).thenReturn(Optional.of(only));
        when(addresses.findByOwner("u-1")).thenReturn(Collections.emptyList());

        service.delete("u-1", office.id());
        service.delete("u-1", only.id());

        verify(addresses, never()).save(any());
    }

    @Test
    void listsTheOwnersAddresses() {
        when(addresses.findByOwner("u-1")).thenReturn(List.of(saved(HOME, true)));

        assertThat(service.list("u-1")).hasSize(1);
    }
}
