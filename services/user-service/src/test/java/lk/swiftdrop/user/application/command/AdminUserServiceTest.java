package lk.swiftdrop.user.application.command;

import lk.swiftdrop.common.web.error.BadRequestException;
import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.common.web.error.ConflictException;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.user.application.model.DirectoryUser;
import lk.swiftdrop.user.application.model.NewStaffAccount;
import lk.swiftdrop.user.application.port.out.CourierProfileRepository;
import lk.swiftdrop.user.application.port.out.IdentityAdmin;
import lk.swiftdrop.user.application.port.out.UserProfileRepository;
import lk.swiftdrop.user.domain.model.CourierProfile;
import lk.swiftdrop.user.domain.model.PhoneNumber;
import lk.swiftdrop.user.domain.model.ProfileStatus;
import lk.swiftdrop.user.domain.model.UserProfile;
import lk.swiftdrop.user.domain.model.UserRole;
import lk.swiftdrop.user.domain.model.VehicleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserServiceTest {

    private static final UUID KANDY = UUID.fromString("11111111-1111-4111-8111-111111111111");

    private IdentityAdmin admin;
    private UserProfileRepository profiles;
    private CourierProfileRepository courierProfiles;
    private AdminUserService service;

    @BeforeEach
    void setUp() {
        admin = mock(IdentityAdmin.class);
        profiles = mock(UserProfileRepository.class);
        courierProfiles = mock(CourierProfileRepository.class);
        service = new AdminUserService(admin, profiles, courierProfiles);
        when(admin.createUser(anyString(), anyString())).thenReturn("new-1");
        when(profiles.save(any())).thenAnswer(call -> call.getArgument(0));
        when(courierProfiles.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private static NewStaffAccount courier(PhoneNumber phone, NewStaffAccount.CourierDetails details) {
        return new NewStaffAccount(" Kasun@X.lk ", "Kasun Silva", phone, UserRole.COURIER, details);
    }

    @Test
    void createsACourierWithRoleProfilePhoneAndDetailsThenInvitesThem() {
        when(profiles.findById("new-1")).thenReturn(Optional.of(
                UserProfile.firstSeen("new-1", "kasun@x.lk", "Kasun Silva", Set.of(UserRole.COURIER))));

        AdminUserService.CreatedAccount created = service.create(courier(PhoneNumber.parse("0771234567"),
                new NewStaffAccount.CourierDetails(VehicleType.BIKE, KANDY, 25)));

        verify(admin).createUser("kasun@x.lk", "Kasun Silva");
        verify(admin).grantRoles("new-1", Set.of(UserRole.COURIER));
        ArgumentCaptor<UserProfile> withPhone = ArgumentCaptor.forClass(UserProfile.class);
        verify(profiles).save(withPhone.capture());
        assertThat(withPhone.getValue().phoneNumber()).contains(PhoneNumber.parse("+94771234567"));
        ArgumentCaptor<CourierProfile> details = ArgumentCaptor.forClass(CourierProfile.class);
        verify(courierProfiles).save(details.capture());
        assertThat(details.getValue().maxDailyCapacity()).isEqualTo(25);
        verify(admin).sendSetPasswordEmail("new-1");
        assertThat(created.invitationSent()).isTrue();
        assertThat(created.user().roles()).containsExactlyInAnyOrder(UserRole.COURIER, UserRole.CUSTOMER);
        assertThat(created.user().email()).isEqualTo("kasun@x.lk");
    }

    @Test
    void aFailedInvitationStillLeavesTheAccount() {
        doThrow(new RuntimeException("smtp down")).when(admin).sendSetPasswordEmail("new-1");

        AdminUserService.CreatedAccount created = service.create(
                new NewStaffAccount("d@x.lk", "Dilan", null, UserRole.DISPATCHER, null));

        assertThat(created.invitationSent()).isFalse();
        verify(admin, never()).deleteUser(anyString());
    }

    @Test
    void aDatabaseFailureRemovesTheKeycloakAccountAgain() {
        when(profiles.createIfAbsent(any())).thenThrow(new ConflictException("EMAIL_ALREADY_LINKED", "linked"));

        assertThatThrownBy(() -> service.create(courier(null, null))).isInstanceOf(ConflictException.class);
        verify(admin).deleteUser("new-1");
        verify(admin, never()).sendSetPasswordEmail(anyString());
    }

    @Test
    void aFailedCleanupDoesNotHideTheOriginalError() {
        doThrow(new IllegalStateException("db down")).when(admin).grantRoles(anyString(), any());
        doThrow(new RuntimeException("keycloak down too")).when(admin).deleteUser("new-1");

        assertThatThrownBy(() -> service.create(courier(null, null))).hasMessage("db down");
    }

    @Test
    void refusesCustomerAccountsAndCourierDetailsForOtherRoles() {
        assertThatThrownBy(() -> service.create(new NewStaffAccount("c@x.lk", "C", null, UserRole.CUSTOMER, null)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("COURIER");
        assertThatThrownBy(() -> service.create(new NewStaffAccount("d@x.lk", "D", null, UserRole.DISPATCHER,
                new NewStaffAccount.CourierDetails(VehicleType.VAN, KANDY, 10))))
                .isInstanceOf(BadRequestException.class);
        verify(admin, never()).createUser(anyString(), anyString());
    }

    @Test
    void replacesStaffRolesAndDisablesTheAccount() {
        when(admin.findUser("u-2")).thenReturn(Optional.of(new DirectoryUser("u-2", "k@x.lk", "Kasun", "Silva", true)));
        when(admin.rolesOf("u-2")).thenReturn(Set.of(UserRole.COURIER));
        when(profiles.findById("u-2")).thenReturn(Optional.of(
                UserProfile.firstSeen("u-2", "k@x.lk", "Kasun Silva", Set.of(UserRole.COURIER, UserRole.CUSTOMER))));

        var updated = service.update("admin-1", "u-2", false, Set.of(UserRole.DISPATCHER));

        verify(admin).grantRoles("u-2", Set.of(UserRole.DISPATCHER));
        verify(admin).revokeRoles("u-2", Set.of(UserRole.COURIER));
        verify(admin).setEnabled("u-2", false);
        verify(admin).endSessions("u-2");
        ArgumentCaptor<UserProfile> mirrored = ArgumentCaptor.forClass(UserProfile.class);
        verify(profiles).save(mirrored.capture());
        assertThat(mirrored.getValue().status()).isEqualTo(ProfileStatus.DISABLED);
        assertThat(mirrored.getValue().roles()).containsExactlyInAnyOrder(UserRole.DISPATCHER, UserRole.CUSTOMER);
        assertThat(updated.enabled()).isFalse();
        assertThat(updated.roles()).containsExactlyInAnyOrder(UserRole.DISPATCHER, UserRole.CUSTOMER);
    }

    @Test
    void leavesUnmentionedPartsAloneAndSkipsAMissingProfile() {
        when(admin.findUser("u-2")).thenReturn(Optional.of(new DirectoryUser("u-2", "k@x.lk", "Kasun", null, true)));
        when(admin.rolesOf("u-2")).thenReturn(Set.of(UserRole.MERCHANT));
        when(profiles.findById("u-2")).thenReturn(Optional.empty());

        var updated = service.update("admin-1", "u-2", true, null);

        verify(admin, never()).setEnabled(anyString(), anyBoolean());
        verify(admin, never()).grantRoles(anyString(), any());
        verify(profiles, never()).save(any());
        assertThat(updated.roles()).containsExactlyInAnyOrder(UserRole.MERCHANT, UserRole.CUSTOMER);
    }

    @Test
    void anAdminCannotLockThemselvesOut() {
        when(admin.findUser("admin-1")).thenReturn(Optional.of(new DirectoryUser("admin-1", "a@x.lk", "Amara", null, true)));

        assertThatThrownBy(() -> service.update("admin-1", "admin-1", false, null))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("disable your own");
        assertThatThrownBy(() -> service.update("admin-1", "admin-1", null, Set.of(UserRole.DISPATCHER)))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("ADMIN role");
        verify(admin, never()).setEnabled(anyString(), anyBoolean());
        verify(admin, never()).revokeRoles(anyString(), any());
    }

    @Test
    void onlyStaffRolesCanBeChangedAndUnknownUsersAreNotFound() {
        when(admin.findUser("u-2")).thenReturn(Optional.of(new DirectoryUser("u-2", "k@x.lk", "K", null, true)));
        when(admin.findUser("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("admin-1", "u-2", null, Set.of(UserRole.MERCHANT)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.update("admin-1", "nobody", true, null)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.resendInvitation("nobody")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resendsTheInvitation() {
        when(admin.findUser("u-2")).thenReturn(Optional.of(new DirectoryUser("u-2", "k@x.lk", "K", null, true)));

        service.resendInvitation("u-2");

        verify(admin).sendSetPasswordEmail("u-2");
    }
}
