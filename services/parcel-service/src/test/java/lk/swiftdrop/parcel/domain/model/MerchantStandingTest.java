package lk.swiftdrop.parcel.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MerchantStandingTest {

    private static final Instant T1 = Instant.parse("2026-11-20T08:00:00Z");
    private static final Instant T2 = T1.plusSeconds(60);

    private static MerchantStanding standing(MerchantStatus status, Instant changedAt) {
        return new MerchantStanding("merchant-1", status, "Lanka Books", "lankabooks@example.com", changedAt);
    }

    @Test
    void onlyAnApprovedMerchantMayOrder() {
        assertThat(standing(MerchantStatus.APPROVED, T1).isApproved()).isTrue();
        for (MerchantStatus other : new MerchantStatus[]{MerchantStatus.PENDING, MerchantStatus.REJECTED,
                MerchantStatus.SUSPENDED}) {
            assertThat(standing(other, T1).isApproved()).as(other.name()).isFalse();
        }
    }

    @Test
    void aNewerChangeReplacesAnOlderOne() {
        assertThat(standing(MerchantStatus.PENDING, T1).supersededBy(standing(MerchantStatus.APPROVED, T2))).isTrue();
        assertThat(standing(MerchantStatus.APPROVED, T1).supersededBy(standing(MerchantStatus.SUSPENDED, T2))).isTrue();
    }

    @Test
    void aLateOlderApprovalCannotReopenASuspendedMerchant() {
        MerchantStanding suspended = standing(MerchantStatus.SUSPENDED, T2);

        assertThat(suspended.supersededBy(standing(MerchantStatus.APPROVED, T1))).isFalse();
    }

    @Test
    void theSameChangeDeliveredTwiceChangesNothing() {
        assertThat(standing(MerchantStatus.APPROVED, T1).supersededBy(standing(MerchantStatus.APPROVED, T1))).isFalse();
    }

    @Test
    void onATieTheMoveAwayFromApprovedWins() {
        assertThat(standing(MerchantStatus.APPROVED, T1).supersededBy(standing(MerchantStatus.SUSPENDED, T1))).isTrue();
        assertThat(standing(MerchantStatus.SUSPENDED, T1).supersededBy(standing(MerchantStatus.APPROVED, T1))).isFalse();
    }

    @Test
    void anotherMerchantsChangeIsAMistake() {
        MerchantStanding other = new MerchantStanding("merchant-2", MerchantStatus.APPROVED, "Other", null, T2);

        assertThatThrownBy(() -> standing(MerchantStatus.PENDING, T1).supersededBy(other))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
