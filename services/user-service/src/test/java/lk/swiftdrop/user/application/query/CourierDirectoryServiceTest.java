package lk.swiftdrop.user.application.query;

import lk.swiftdrop.user.application.model.DirectoryUser;
import lk.swiftdrop.user.application.port.out.CourierProfileRepository;
import lk.swiftdrop.user.application.port.out.IdentityDirectory;
import lk.swiftdrop.user.domain.model.Courier;
import lk.swiftdrop.user.domain.model.CourierProfile;
import lk.swiftdrop.user.domain.model.UserRole;
import lk.swiftdrop.user.domain.model.VehicleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CourierDirectoryServiceTest {

    private static final UUID KANDY = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID GALLE = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private CourierDirectoryService service;

    @BeforeEach
    void setUp() {
        IdentityDirectory directory = mock(IdentityDirectory.class);
        CourierProfileRepository profiles = mock(CourierProfileRepository.class);
        service = new CourierDirectoryService(directory, profiles);
        when(directory.usersWithRole(UserRole.COURIER)).thenReturn(List.of(
                new DirectoryUser("c-2", "kasun@x.lk", "Kasun", "Silva", true),
                new DirectoryUser("c-1", "chamath@x.lk", "Chamath", "Perera", true),
                new DirectoryUser("c-3", "new@x.lk", null, null, true),
                new DirectoryUser("c-4", "off@x.lk", "Amal", "Disabled", false)));
        when(profiles.findByCourierIds(anyCollection())).thenReturn(Map.of(
                "c-1", new CourierProfile("c-1", VehicleType.BIKE, KANDY, 25, true, null),
                "c-2", new CourierProfile("c-2", VehicleType.VAN, GALLE, 60, false, null),
                "c-4", new CourierProfile("c-4", VehicleType.BIKE, KANDY, 20, true, null)));
    }

    @Test
    void listsEveryKeycloakCourierSortedByNameEvenWithoutDetails() {
        List<Courier> couriers = service.couriers(null, null);

        assertThat(couriers).extracting(Courier::fullName)
                .containsExactly("Amal Disabled", "Chamath Perera", "Kasun Silva", "new@x.lk");
        assertThat(couriers.get(3).profile()).isEmpty();
    }

    @Test
    void filtersByHomeZone() {
        assertThat(service.couriers(KANDY, null)).extracting(Courier::id).containsExactly("c-4", "c-1");
    }

    @Test
    void filtersByWhetherDispatchCanAssignToThem() {
        assertThat(service.couriers(null, true)).extracting(Courier::id).containsExactly("c-1");
        assertThat(service.couriers(null, false)).extracting(Courier::id).containsExactly("c-4", "c-2", "c-3");
        assertThat(service.couriers(KANDY, true)).extracting(Courier::id).containsExactly("c-1");
    }

    @Test
    void displayNameFallsBackToTheEmailThenTheId() {
        assertThat(new DirectoryUser("id", "a@x.lk", " ", null, true).displayName()).isEqualTo("a@x.lk");
        assertThat(new DirectoryUser("id", null, null, null, true).displayName()).isEqualTo("id");
        assertThat(new DirectoryUser("id", "a@x.lk", "Nimal", null, true).displayName()).isEqualTo("Nimal");
    }
}
