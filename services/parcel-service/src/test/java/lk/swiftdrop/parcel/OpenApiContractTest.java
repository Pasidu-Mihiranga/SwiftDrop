package lk.swiftdrop.parcel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Proves the contract check can fail: otherwise a passing integration test would prove nothing. */
class OpenApiContractTest {

    private static final String ORDER = """
            {"id":"7e726c28-4a72-4fea-9ec1-c56b08262ddc","trackingNumber":"SD261120M3RCH7X","status":"CREATED",
             "source":"MERCHANT","merchantOrderRef":"ORD-1001",
             "pickup":{"line1":"12 Galle Road","city":"Colombo 03","district":"Colombo"},
             "dropoff":{"line1":"45 Peradeniya Road","city":"Kandy","district":"Kandy"},
             "recipient":{"name":"Saman Perera","phone":"0712345678"},
             "weightKg":2,"sizeClass":"S","serviceLevel":"STANDARD","fee":{"amount":650.0,"currency":"LKR"},
             "createdAt":"2026-11-20T08:00:00Z"}""";

    @Test
    void aMerchantOrderShapedLikeTheContractPasses() {
        assertThatCode(() -> OpenApiContract.assertParcelSchema("MerchantOrder", ORDER)).doesNotThrowAnyException();
        assertThatCode(() -> OpenApiContract.assertParcelSchema("MerchantOrderPage",
                "{\"content\":[" + ORDER + "],\"page\":{\"page\":0,\"size\":20,\"totalElements\":1,\"totalPages\":1}}"))
                .doesNotThrowAnyException();
    }

    @Test
    void aCustomerParcelIsNotAMerchantOrder() {
        assertThatThrownBy(() -> OpenApiContract.assertParcelSchema("MerchantOrder",
                ORDER.replace("\"MERCHANT\"", "\"CUSTOMER\""))).isInstanceOf(AssertionError.class);
    }

    @Test
    void anOrderWithoutItsTrackingNumberOrFeeFails() {
        assertThatThrownBy(() -> OpenApiContract.assertParcelSchema("MerchantOrder",
                ORDER.replace("\"trackingNumber\":\"SD261120M3RCH7X\",", ""))).isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> OpenApiContract.assertParcelSchema("MerchantOrder",
                ORDER.replace(",\"fee\":{\"amount\":650.0,\"currency\":\"LKR\"}", ""))).isInstanceOf(AssertionError.class);
    }

    @Test
    void aPageWithoutItsPagingDetailsFails() {
        assertThatThrownBy(() -> OpenApiContract.assertParcelSchema("MerchantOrderPage",
                "{\"content\":[],\"page\":{\"page\":0}}")).isInstanceOf(AssertionError.class);
    }

    @Test
    void anErrorWithoutItsCodeIsNotAProblem() {
        assertThatThrownBy(() -> OpenApiContract.assertProblem(
                "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403}")).isInstanceOf(AssertionError.class);
    }
}
