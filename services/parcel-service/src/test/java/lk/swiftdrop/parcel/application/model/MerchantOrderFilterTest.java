package lk.swiftdrop.parcel.application.model;

import lk.swiftdrop.events.common.Types.ParcelStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantOrderFilterTest {

    @Test
    void theOrderRefSearchIsTrimmed() {
        assertThat(new MerchantOrderFilter(null, null, null, "  WEB-1001 ").orderRef()).isEqualTo("WEB-1001");
    }

    @Test
    void aBlankOrderRefMeansNoSearch() {
        assertThat(new MerchantOrderFilter(null, null, null, " \t ")).isEqualTo(MerchantOrderFilter.none());
        assertThat(new MerchantOrderFilter(null, null, null, "").orderRef()).isNull();
    }

    @Test
    void theFilterWithoutASearchIsTheSameAsOneWithAnEmptySearch() {
        Instant from = Instant.parse("2026-11-01T00:00:00Z");
        assertThat(new MerchantOrderFilter(ParcelStatus.CREATED, from, null))
                .isEqualTo(new MerchantOrderFilter(ParcelStatus.CREATED, from, null, null));
    }
}
