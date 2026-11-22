package lk.swiftdrop.user.application.query;

import lk.swiftdrop.user.application.model.DirectoryUser;
import lk.swiftdrop.user.application.model.ManagedUser;
import lk.swiftdrop.user.application.model.UserPage;
import lk.swiftdrop.user.application.port.out.IdentityAdmin;
import lk.swiftdrop.user.application.port.out.IdentityDirectory;
import lk.swiftdrop.user.domain.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminUserQueryServiceTest {

    private AdminUserQueryService service;

    @BeforeEach
    void setUp() {
        IdentityAdmin admin = mock(IdentityAdmin.class);
        IdentityDirectory directory = mock(IdentityDirectory.class);
        service = new AdminUserQueryService(admin, directory);
        DirectoryUser amara = new DirectoryUser("a", "admin@swiftdrop.lk", "Amara", "Admin", true);
        DirectoryUser chamath = new DirectoryUser("c", "courier1@swiftdrop.lk", "Chamath", "Courier", true);
        DirectoryUser kasun = new DirectoryUser("k", "courier2@swiftdrop.lk", "Kasun", "Courier", false);
        DirectoryUser nimali = new DirectoryUser("n", "customer@swiftdrop.lk", "Nimali", "Customer", true);
        when(admin.allUsers()).thenReturn(List.of(nimali, kasun, amara, chamath));
        when(directory.usersWithRole(any())).thenReturn(List.of());
        when(directory.usersWithRole(UserRole.ADMIN)).thenReturn(List.of(amara));
        when(directory.usersWithRole(UserRole.COURIER)).thenReturn(List.of(chamath, kasun));
    }

    @Test
    void listsEveryoneSortedByNameWithTheirRoles() {
        UserPage page = service.search(null, null, null, 0, 20);

        assertThat(page.total()).isEqualTo(4);
        assertThat(page.items()).extracting(ManagedUser::fullName)
                .containsExactly("Amara Admin", "Chamath Courier", "Kasun Courier", "Nimali Customer");
        assertThat(page.items().get(0).roles()).containsExactlyInAnyOrder(UserRole.ADMIN, UserRole.CUSTOMER);
        assertThat(page.items().get(3).roles()).containsExactly(UserRole.CUSTOMER);
    }

    @Test
    void searchesByNameOrEmailAndFiltersByRoleAndState() {
        assertThat(service.search("  COURIER2 ", null, null, 0, 20).items()).extracting(ManagedUser::id).containsExactly("k");
        assertThat(service.search("chamath", null, null, 0, 20).items()).extracting(ManagedUser::id).containsExactly("c");
        assertThat(service.search(null, UserRole.COURIER, true, 0, 20).items()).extracting(ManagedUser::id).containsExactly("c");
        assertThat(service.search(null, null, false, 0, 20).items()).extracting(ManagedUser::id).containsExactly("k");
    }

    @Test
    void pagesAndKeepsPageSizesSensible() {
        UserPage second = service.search(null, null, null, 1, 3);
        assertThat(second.items()).extracting(ManagedUser::id).containsExactly("n");
        assertThat(second.total()).isEqualTo(4);

        assertThat(service.search(null, null, null, 9, 3).items()).isEmpty();
        assertThat(service.search(null, null, null, -1, 0).size()).isEqualTo(1);
        assertThat(service.search(null, null, null, 0, 1000).size()).isEqualTo(AdminUserQueryService.MAX_PAGE_SIZE);
    }
}
