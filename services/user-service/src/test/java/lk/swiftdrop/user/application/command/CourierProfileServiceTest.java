package lk.swiftdrop.user.application.command;

import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.user.application.model.DirectoryUser;
import lk.swiftdrop.user.application.port.out.CourierProfileRepository;
import lk.swiftdrop.user.application.port.out.IdentityDirectory;
import lk.swiftdrop.user.application.port.out.UserProfileRepository;
import lk.swiftdrop.user.domain.model.Courier;
import lk.swiftdrop.user.domain.model.UserProfile;
import lk.swiftdrop.user.domain.model.UserRole;
import lk.swiftdrop.user.domain.model.VehicleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourierProfileServiceTest {

    private static final UUID KANDY = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private IdentityDirectory directory;
    private UserProfileRepository userProfiles;
    private CourierProfileRepository courierProfiles;
    private CourierProfileService service;

    @BeforeEach
    void setUp() {
        directory = mock(IdentityDirectory.class);
        userProfiles = mock(UserProfileRepository.class);
        courierProfiles = mock(CourierProfileRepository.class);
        service = new CourierProfileService(directory, userProfiles, courierProfiles);
        when(directory.usersWithRole(UserRole.COURIER)).thenReturn(List.of(
                new DirectoryUser("c-1", "chamath@x.lk", "Chamath", "Perera", true),
                new DirectoryUser("c-9", null, "No", "Email", true)));
        when(courierProfiles.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void savesTheDetailsAndCreatesTheUserProfileOfACourierWhoNeverSignedIn() {
        Courier courier = service.updateProfile("c-1", VehicleType.THREE_WHEELER, KANDY, 30, true);

        ArgumentCaptor<UserProfile> created = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfiles).createIfAbsent(created.capture());
        assertThat(created.getValue().id()).isEqualTo("c-1");
        assertThat(created.getValue().fullName()).isEqualTo("Chamath Perera");
        assertThat(created.getValue().roles()).containsExactly(UserRole.COURIER);
        assertThat(courier.fullName()).isEqualTo("Chamath Perera");
        assertThat(courier.assignable()).isTrue();
        assertThat(courier.profile()).hasValueSatisfying(profile -> {
            assertThat(profile.vehicleType()).isEqualTo(VehicleType.THREE_WHEELER);
            assertThat(profile.homeZoneId()).isEqualTo(KANDY);
            assertThat(profile.maxDailyCapacity()).isEqualTo(30);
        });
    }

    @Test
    void anIdThatIsNotACourierIsNotFound() {
        assertThatThrownBy(() -> service.updateProfile("customer-1", VehicleType.BIKE, KANDY, 10, true))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("customer-1");
        verify(courierProfiles, never()).save(any());
        verify(userProfiles, never()).createIfAbsent(any());
    }

    @Test
    void aCourierWithoutAnEmailCannotGetDetails() {
        assertThatThrownBy(() -> service.updateProfile("c-9", VehicleType.BIKE, KANDY, 10, true))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(courierProfiles, never()).save(any());
    }
}
