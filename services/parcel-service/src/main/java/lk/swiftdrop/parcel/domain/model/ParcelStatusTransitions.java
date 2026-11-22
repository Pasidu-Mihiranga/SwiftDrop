package lk.swiftdrop.parcel.domain.model;

import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.events.common.Types.ParcelStatus;

/** The courier lifecycle rules, kept independent from Spring and persistence. */
public final class ParcelStatusTransitions {

    private ParcelStatusTransitions() {
    }

    public static void validate(ParcelStatus from, ParcelStatus to) {
        boolean allowed = switch (from) {
            case CREATED -> to == ParcelStatus.PICKED_UP || to == ParcelStatus.CANCELLED;
            case PICKED_UP -> to == ParcelStatus.IN_TRANSIT;
            case IN_TRANSIT -> to == ParcelStatus.OUT_FOR_DELIVERY;
            case OUT_FOR_DELIVERY -> to == ParcelStatus.DELIVERED || to == ParcelStatus.FAILED;
            case FAILED -> to == ParcelStatus.OUT_FOR_DELIVERY;
            case DELIVERED, CANCELLED -> false;
        };
        if (!allowed) {
            throw new BusinessRuleViolationException("INVALID_STATUS_TRANSITION",
                    "Parcel cannot move from " + from + " to " + to + ".");
        }
    }
}
