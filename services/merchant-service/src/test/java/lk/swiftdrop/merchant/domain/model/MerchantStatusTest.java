package lk.swiftdrop.merchant.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantStatusTest {

    @ParameterizedTest(name = "{0} -> {1} allowed: {2}")
    @CsvSource({
            "PENDING,   APPROVED,  true",
            "PENDING,   REJECTED,  true",
            "PENDING,   SUSPENDED, false",
            "PENDING,   PENDING,   false",
            "APPROVED,  SUSPENDED, true",
            "APPROVED,  REJECTED,  false",
            "APPROVED,  PENDING,   false",
            "APPROVED,  APPROVED,  false",
            "REJECTED,  APPROVED,  false",
            "REJECTED,  PENDING,   false",
            "SUSPENDED, APPROVED,  false",
            "SUSPENDED, PENDING,   false",
    })
    void followsTheMerchantLifecycle(MerchantStatus from, MerchantStatus to, boolean allowed) {
        assertThat(from.canMoveTo(to)).isEqualTo(allowed);
    }

    @Test
    void rejectedAndSuspendedAreFinal() {
        for (MerchantStatus target : MerchantStatus.values()) {
            assertThat(MerchantStatus.REJECTED.canMoveTo(target)).isFalse();
            assertThat(MerchantStatus.SUSPENDED.canMoveTo(target)).isFalse();
        }
    }
}
