package lk.swiftdrop.user.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserProfileTest {

    @Test
    void aFirstSeenProfileTakesTheNameFromTheTokenAndHasNoPhone() {
        UserProfile profile = UserProfile.firstSeen("sub-1", " Nimal@Example.LK ", "Nimal Perera", Set.of(UserRole.CUSTOMER));

        assertThat(profile.email()).isEqualTo("nimal@example.lk");
        assertThat(profile.fullName()).isEqualTo("Nimal Perera");
        assertThat(profile.phoneNumber()).isEmpty();
        assertThat(profile.status()).isEqualTo(ProfileStatus.ACTIVE);
        assertThat(profile.roles()).containsExactly(UserRole.CUSTOMER);
    }

    @Test
    void withoutANameClaimTheEmailLocalPartIsUsed() {
        assertThat(UserProfile.firstSeen("sub-1", "kamal@example.lk", null, Set.of()).fullName()).isEqualTo("kamal");
        assertThat(UserProfile.firstSeen("sub-1", "kamal@example.lk", "  ", Set.of()).fullName()).isEqualTo("kamal");
    }

    @Test
    void syncingKeepsTheNameAndPhoneTheUserChose() {
        UserProfile profile = UserProfile.firstSeen("sub-1", "a@example.lk", "Old", Set.of(UserRole.CUSTOMER))
                .withContactDetails("Chosen Name", PhoneNumber.parse("0771234567"));

        UserProfile synced = profile.syncedWith("b@example.lk", Set.of(UserRole.COURIER));

        assertThat(synced.email()).isEqualTo("b@example.lk");
        assertThat(synced.roles()).containsExactly(UserRole.COURIER);
        assertThat(synced.fullName()).isEqualTo("Chosen Name");
        assertThat(synced.phoneNumber()).contains(PhoneNumber.parse("0771234567"));
    }

    @Test
    void detectsWhenTheTokenDisagreesWithTheStoredCopy() {
        UserProfile profile = UserProfile.firstSeen("sub-1", "a@example.lk", "A", Set.of(UserRole.CUSTOMER));

        assertThat(profile.differsFrom("A@EXAMPLE.LK", Set.of(UserRole.CUSTOMER))).isFalse();
        assertThat(profile.differsFrom("b@example.lk", Set.of(UserRole.CUSTOMER))).isTrue();
        assertThat(profile.differsFrom("a@example.lk", Set.of(UserRole.CUSTOMER, UserRole.ADMIN))).isTrue();
        assertThat(profile.differsFrom("a@example.lk", Set.of())).isTrue();
    }

    @Test
    void aNullPhoneRemovesTheStoredNumber() {
        UserProfile profile = UserProfile.firstSeen("sub-1", "a@example.lk", "A", Set.of())
                .withContactDetails("A", PhoneNumber.parse("0771234567"))
                .withContactDetails("A", null);

        assertThat(profile.phoneNumber()).isEmpty();
    }

    @Test
    void enforcesItsInvariants() {
        assertThatThrownBy(() -> UserProfile.firstSeen(" ", "a@example.lk", "A", Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserProfile.firstSeen("sub-1", "not-an-email", "A", Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserProfile.firstSeen("sub-1", null, "A", Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserProfile.firstSeen("sub-1", "a@example.lk", "x".repeat(101), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserProfile.firstSeen("sub-1", "a@example.lk", "A", Set.of()).withContactDetails(" ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void personRolesComeOnlyFromKnownRealmRoles() {
        assertThat(UserRole.fromTokenRoles(List.of("CUSTOMER", "offline_access", "MERCHANT_API", "default-roles-swiftdrop", "ADMIN")))
                .containsExactlyInAnyOrder(UserRole.CUSTOMER, UserRole.ADMIN);
    }
}
