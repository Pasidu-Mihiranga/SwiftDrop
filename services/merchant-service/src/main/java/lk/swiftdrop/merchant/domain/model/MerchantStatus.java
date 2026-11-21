package lk.swiftdrop.merchant.domain.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Where a merchant application stands. The allowed moves are the merchant lifecycle in the M7
 * design (figure 11): PENDING to APPROVED or REJECTED, and APPROVED to SUSPENDED.
 * REJECTED and SUSPENDED are final; reinstating a suspended merchant is outside issue #20.
 */
public enum MerchantStatus {
    PENDING,
    APPROVED,
    REJECTED,
    SUSPENDED;

    /** True when a merchant in this status may move to {@code target}. */
    public boolean canMoveTo(MerchantStatus target) {
        return nextStatuses().contains(target);
    }

    private Set<MerchantStatus> nextStatuses() {
        return switch (this) {
            case PENDING -> EnumSet.of(APPROVED, REJECTED);
            case APPROVED -> EnumSet.of(SUSPENDED);
            case REJECTED, SUSPENDED -> EnumSet.noneOf(MerchantStatus.class);
        };
    }
}
