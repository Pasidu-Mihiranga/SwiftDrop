package lk.swiftdrop.user.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AddressDetailsTest {

    private static AddressDetails kandy(String postalCode, Double lat, Double lng) {
        return new AddressDetails("Home", "No. 12, Temple Road", null, "Kandy", "Kandy", postalCode, lat, lng);
    }

    @Test
    void trimsTheInputAndTurnsEmptyOptionalFieldsIntoNothing() {
        AddressDetails details = new AddressDetails(" Home ", " No. 12, Temple Road ", "  ", " Kandy ", " Kandy ", "", null, null);

        assertThat(details.label()).isEqualTo("Home");
        assertThat(details.line1()).isEqualTo("No. 12, Temple Road");
        assertThat(details.line2()).isNull();
        assertThat(details.postalCode()).isNull();
    }

    @Test
    void acceptsAFullAddressWithCoordinates() {
        AddressDetails details = kandy("20000", 7.2906, 80.6337);

        assertThat(details.postalCode()).isEqualTo("20000");
        assertThat(details.lat()).isEqualTo(7.2906);
    }

    @Test
    void rejectsMissingOrTooLongRequiredFields() {
        assertThatThrownBy(() -> new AddressDetails(" ", "Line", null, "Kandy", "Kandy", null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Label");
        assertThatThrownBy(() -> new AddressDetails("Home", null, null, "Kandy", "Kandy", null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("line 1");
        assertThatThrownBy(() -> new AddressDetails("Home", "Line", "x".repeat(121), "Kandy", "Kandy", null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("line 2");
        assertThatThrownBy(() -> new AddressDetails("Home", "Line", null, "", "Kandy", null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("City");
        assertThatThrownBy(() -> new AddressDetails("Home", "Line", null, "Kandy", "x".repeat(41), null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("District");
    }

    @Test
    void checksPostalCodeAndCoordinates() {
        assertThatThrownBy(() -> kandy("2000", null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> kandy("20000", 7.29, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> kandy("20000", 91.0, 80.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> kandy("20000", 7.0, 181.0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toStringNeverRevealsTheStreet() {
        assertThat(kandy(null, null, null).toString()).doesNotContain("Temple").contains("Kandy");
    }

    @Test
    void anAddressNeedsAnIdAnOwnerAndDetails() {
        AddressDetails details = kandy(null, null, null);
        assertThatThrownBy(() -> new Address(null, "u-1", details, false, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Address(UUID.randomUUID(), " ", details, false, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Address(UUID.randomUUID(), "u-1", null, false, null, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
