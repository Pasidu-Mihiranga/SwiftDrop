package lk.swiftdrop.merchant.domain.model;

import lk.swiftdrop.merchant.MerchantFixtures;
import lk.swiftdrop.merchant.domain.exception.InvalidMerchantTransitionException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static lk.swiftdrop.merchant.MerchantFixtures.ADMIN;
import static lk.swiftdrop.merchant.MerchantFixtures.NOW;
import static lk.swiftdrop.merchant.MerchantFixtures.OWNER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MerchantTest {

    private static final Instant LATER = NOW.plusSeconds(3600);

    @Test
    void aNewApplicationIsPendingAndUnreviewed() {
        Merchant merchant = MerchantFixtures.pending();

        assertThat(merchant.id()).isNotNull();
        assertThat(merchant.ownerUserId()).isEqualTo(OWNER);
        assertThat(merchant.status()).isEqualTo(MerchantStatus.PENDING);
        assertThat(merchant.isApproved()).isFalse();
        assertThat(merchant.reviewedBy()).isNull();
        assertThat(merchant.reviewedAt()).isNull();
        assertThat(merchant.rejectionReason()).isNull();
        assertThat(merchant.createdAt()).isEqualTo(NOW);
        assertThat(merchant.version()).isNull();
    }

    @Test
    void approvingRecordsTheAdminAndLeavesTheOriginalUntouched() {
        Merchant pending = MerchantFixtures.pending();

        Merchant approved = pending.approve(ADMIN, LATER);

        assertThat(pending.status()).isEqualTo(MerchantStatus.PENDING);
        assertThat(approved.status()).isEqualTo(MerchantStatus.APPROVED);
        assertThat(approved.isApproved()).isTrue();
        assertThat(approved.reviewedBy()).isEqualTo(ADMIN);
        assertThat(approved.reviewedAt()).isEqualTo(LATER);
        assertThat(approved.updatedAt()).isEqualTo(LATER);
        assertThat(approved.createdAt()).isEqualTo(NOW);
        assertThat(approved.id()).isEqualTo(pending.id());
    }

    @Test
    void rejectingKeepsTheReasonWithoutSurroundingSpace() {
        Merchant rejected = MerchantFixtures.pending().reject(ADMIN, "  BR number could not be verified  ", LATER);

        assertThat(rejected.status()).isEqualTo(MerchantStatus.REJECTED);
        assertThat(rejected.rejectionReason()).isEqualTo("BR number could not be verified");
        assertThat(rejected.reviewedBy()).isEqualTo(ADMIN);
    }

    @Test
    void aRejectionNeedsAReason() {
        Merchant pending = MerchantFixtures.pending();

        assertThatThrownBy(() -> pending.reject(ADMIN, "   ", LATER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pending.reject(ADMIN, null, LATER)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aRejectionReasonHasALengthLimit() {
        Merchant pending = MerchantFixtures.pending();
        String tooLong = "x".repeat(Merchant.MAX_REASON_LENGTH + 1);

        assertThatThrownBy(() -> pending.reject(ADMIN, tooLong, LATER)).isInstanceOf(IllegalArgumentException.class);
        assertThat(pending.reject(ADMIN, "x".repeat(Merchant.MAX_REASON_LENGTH), LATER).status())
                .isEqualTo(MerchantStatus.REJECTED);
    }

    @Test
    void onlyAnApprovedMerchantCanBeSuspended() {
        Merchant suspended = MerchantFixtures.pending().approve(ADMIN, LATER).suspend("admin-2", LATER.plusSeconds(60));

        assertThat(suspended.status()).isEqualTo(MerchantStatus.SUSPENDED);
        assertThat(suspended.reviewedBy()).isEqualTo("admin-2");
        assertThat(suspended.rejectionReason()).isNull();

        assertThatThrownBy(() -> MerchantFixtures.pending().suspend(ADMIN, LATER))
                .isInstanceOf(InvalidMerchantTransitionException.class);
    }

    @Test
    void aDecidedApplicationCannotBeDecidedAgain() {
        Merchant approved = MerchantFixtures.pending().approve(ADMIN, LATER);
        Merchant rejected = MerchantFixtures.pending().reject(ADMIN, "Incomplete", LATER);

        assertThatThrownBy(() -> approved.approve(ADMIN, LATER)).isInstanceOf(InvalidMerchantTransitionException.class);
        assertThatThrownBy(() -> approved.reject(ADMIN, "Too late", LATER))
                .isInstanceOf(InvalidMerchantTransitionException.class);
        assertThatThrownBy(() -> rejected.approve(ADMIN, LATER)).isInstanceOf(InvalidMerchantTransitionException.class);
        assertThatThrownBy(() -> rejected.suspend(ADMIN, LATER)).isInstanceOf(InvalidMerchantTransitionException.class);
    }

    @Test
    void aSuspendedMerchantStaysSuspended() {
        Merchant suspended = MerchantFixtures.pending().approve(ADMIN, LATER).suspend(ADMIN, LATER);

        assertThatThrownBy(() -> suspended.approve(ADMIN, LATER)).isInstanceOf(InvalidMerchantTransitionException.class);
        assertThatThrownBy(() -> suspended.suspend(ADMIN, LATER)).isInstanceOf(InvalidMerchantTransitionException.class);
    }

    @Test
    void theTransitionErrorNamesBothStatusesAndCarriesAStableCode() {
        Merchant rejected = MerchantFixtures.pending().reject(ADMIN, "Incomplete", LATER);

        assertThatThrownBy(() -> rejected.approve(ADMIN, LATER))
                .isInstanceOfSatisfying(InvalidMerchantTransitionException.class, e -> {
                    assertThat(e.code()).isEqualTo("MERCHANT_INVALID_TRANSITION");
                    assertThat(e.getMessage()).contains("REJECTED").contains("APPROVED");
                });
    }
}
