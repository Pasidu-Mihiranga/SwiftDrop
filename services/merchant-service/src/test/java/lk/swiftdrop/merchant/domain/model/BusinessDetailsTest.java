package lk.swiftdrop.merchant.domain.model;

import lk.swiftdrop.merchant.MerchantFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class BusinessDetailsTest {

    private static final PickupAddress ADDRESS = new PickupAddress("12 Temple Road", null, "Kandy", "Kandy", null);

    @Test
    void theSameRegistrationNumberWrittenDifferentlyNormalisesToOneValue() {
        assertThat(BusinessDetails.normaliseBrNumber("pv 12345")).isEqualTo("PV12345");
        assertThat(BusinessDetails.normaliseBrNumber(" PV12345 ")).isEqualTo("PV12345");
        assertThat(BusinessDetails.normaliseBrNumber("Pv\t123 45")).isEqualTo("PV12345");
    }

    @Test
    void inputIsTrimmedAndABlankWebsiteBecomesAbsent() {
        BusinessDetails details = new BusinessDetails("  Lanka Books ", "pv 12345", " a@b.example ", " +94771234567 ",
                "  ", ADDRESS);

        assertThat(details.businessName()).isEqualTo("Lanka Books");
        assertThat(details.brNumber()).isEqualTo("PV12345");
        assertThat(details.contactEmail()).isEqualTo("a@b.example");
        assertThat(details.contactPhone()).isEqualTo("+94771234567");
        assertThat(details.website()).isNull();
    }

    @Test
    void requiredFieldsMustBePresent() {
        assertThatNullPointerException()
                .isThrownBy(() -> new BusinessDetails(null, "PV1", "a@b.example", "+94771234567", null, ADDRESS));
        assertThatNullPointerException()
                .isThrownBy(() -> new BusinessDetails("Shop", "PV1", "a@b.example", "+94771234567", null, null));
    }

    @Test
    void personalDataNeverAppearsInTheTextForm() {
        BusinessDetails details = MerchantFixtures.lankaBooks();

        assertThat(details.toString())
                .doesNotContain(details.contactPhone())
                .doesNotContain("Temple Road")
                .contains("Lanka Books");
        assertThat(details.pickupAddress().toString()).doesNotContain("Temple Road");
    }
}
