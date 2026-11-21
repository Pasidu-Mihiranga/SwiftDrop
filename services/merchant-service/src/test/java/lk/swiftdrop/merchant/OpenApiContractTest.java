package lk.swiftdrop.merchant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Proves the contract check can fail: otherwise a passing integration test would prove nothing. */
class OpenApiContractTest {

    private static final String VALID = """
            {"id":"7e726c28-4a72-4fea-9ec1-c56b08262ddc","businessName":"Lanka Books","brNumber":"PV12345",
             "contactEmail":"lankabooks@example.com","contactPhone":"0771234567",
             "pickupAddress":{"line1":"12 Temple Road","city":"Kandy","district":"Kandy"},
             "status":"PENDING","createdAt":"2026-11-19T08:00:00Z"}
            """;

    @Test
    void aResponseShapedLikeTheContractPasses() {
        assertThatCode(() -> OpenApiContract.assertMerchantSchema("Merchant", VALID)).doesNotThrowAnyException();
    }

    @Test
    void aMissingRequiredFieldFails() {
        String noStatus = VALID.replace("\"status\":\"PENDING\",", "");

        assertThatThrownBy(() -> OpenApiContract.assertMerchantSchema("Merchant", noStatus))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void aStatusOutsideTheEnumFails() {
        assertThatThrownBy(() -> OpenApiContract.assertMerchantSchema("Merchant", VALID.replace("PENDING", "DELETED")))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void aPlainArrayIsNotAPage() {
        assertThatThrownBy(() -> OpenApiContract.assertMerchantSchema("MerchantPage", "[" + VALID + "]"))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void anErrorWithoutItsCodeIsNotAProblem() {
        assertThatThrownBy(() -> OpenApiContract.assertProblem(
                "{\"type\":\"about:blank\",\"title\":\"Not Found\",\"status\":404}"))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void newCredentialsNeedAllThreeFieldsAndNothingElse() {
        String credentials = """
                {"clientId":"merchant-7e726c28-4a72-4fea-9ec1-c56b08262ddc","clientSecret":"s3cret",
                 "tokenUrl":"http://localhost:8180/realms/swiftdrop/protocol/openid-connect/token"}
                """;

        assertThatCode(() -> OpenApiContract.assertMerchantSchema("ApiCredentialSecret", credentials))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> OpenApiContract.assertMerchantSchema("ApiCredentialSecret",
                credentials.replace(",\"clientSecret\":\"s3cret\"", ""))).isInstanceOf(AssertionError.class);
    }
}
