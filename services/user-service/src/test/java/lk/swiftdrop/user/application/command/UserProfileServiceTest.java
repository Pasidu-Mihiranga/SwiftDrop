package lk.swiftdrop.user.application.command;

import lk.swiftdrop.user.application.model.IdentityClaims;
import lk.swiftdrop.user.application.port.out.UserProfileRepository;
import lk.swiftdrop.user.domain.model.PhoneNumber;
import lk.swiftdrop.user.domain.model.UserProfile;
import lk.swiftdrop.user.domain.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserProfileServiceTest {

    private static final IdentityClaims CUSTOMER =
            new IdentityClaims("sub-1", "nimal@example.lk", "Nimal Perera", Set.of(UserRole.CUSTOMER));

    private UserProfileRepository profiles;
    private UserProfileService service;

    @BeforeEach
    void setUp() {
        profiles = mock(UserProfileRepository.class);
        service = new UserProfileService(profiles);
        when(profiles.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void theFirstCallCreatesTheProfileFromTheToken() {
        UserProfile created = UserProfile.firstSeen("sub-1", "nimal@example.lk", "Nimal Perera", Set.of(UserRole.CUSTOMER));
        when(profiles.createIfAbsent(any())).thenReturn(true);
        when(profiles.findById("sub-1")).thenReturn(Optional.of(created));

        UserProfile profile = service.getOrCreate(CUSTOMER);

        ArgumentCaptor<UserProfile> inserted = ArgumentCaptor.forClass(UserProfile.class);
        verify(profiles).createIfAbsent(inserted.capture());
        assertThat(inserted.getValue().id()).isEqualTo("sub-1");
        assertThat(inserted.getValue().roles()).containsExactly(UserRole.CUSTOMER);
        assertThat(profile).isEqualTo(created);
        verify(profiles, never()).save(any());
    }

    @Test
    void laterCallsRefreshTheEmailAndRolesButKeepTheChosenName() {
        UserProfile stored = UserProfile.firstSeen("sub-1", "old@example.lk", "Chosen Name", Set.of(UserRole.CUSTOMER));
        when(profiles.createIfAbsent(any())).thenReturn(false);
        when(profiles.findById("sub-1")).thenReturn(Optional.of(stored));

        UserProfile profile = service.getOrCreate(
                new IdentityClaims("sub-1", "new@example.lk", "Token Name", Set.of(UserRole.CUSTOMER, UserRole.MERCHANT)));

        assertThat(profile.email()).isEqualTo("new@example.lk");
        assertThat(profile.roles()).containsExactlyInAnyOrder(UserRole.CUSTOMER, UserRole.MERCHANT);
        assertThat(profile.fullName()).isEqualTo("Chosen Name");
        verify(profiles).save(any());
    }

    @Test
    void nothingIsWrittenWhenTheTokenMatchesTheStoredProfile() {
        when(profiles.createIfAbsent(any())).thenReturn(false);
        when(profiles.findById("sub-1")).thenReturn(Optional.of(
                UserProfile.firstSeen("sub-1", "nimal@example.lk", "Nimal", Set.of(UserRole.CUSTOMER))));

        service.getOrCreate(CUSTOMER);

        verify(profiles, never()).save(any());
    }

    @Test
    void failsLoudlyIfTheProfileVanishesRightAfterCreation() {
        when(profiles.createIfAbsent(any())).thenReturn(true);
        when(profiles.findById("sub-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrCreate(CUSTOMER)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void updatesTheNameAndPhoneOfTheCaller() {
        when(profiles.createIfAbsent(any())).thenReturn(false);
        when(profiles.findById("sub-1")).thenReturn(Optional.of(
                UserProfile.firstSeen("sub-1", "nimal@example.lk", "Nimal", Set.of(UserRole.CUSTOMER))));

        UserProfile updated = service.updateContactDetails(CUSTOMER, "Nimal Perera", PhoneNumber.parse("0771234567"));

        assertThat(updated.fullName()).isEqualTo("Nimal Perera");
        assertThat(updated.phoneNumber()).contains(PhoneNumber.parse("+94771234567"));
        assertThat(updated.id()).isEqualTo("sub-1");
    }
}
