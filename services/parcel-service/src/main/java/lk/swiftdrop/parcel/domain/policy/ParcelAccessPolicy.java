package lk.swiftdrop.parcel.domain.policy;

import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.common.security.Roles;
import lk.swiftdrop.parcel.domain.model.Parcel;

/**
 * The only place that decides who may see, cancel or move a parcel. Callers turn a refusal into the same
 * 404 as a missing id, so a response never reveals that someone else's parcel exists.
 */
public final class ParcelAccessPolicy {

    private ParcelAccessPolicy() {
    }

    /** The owning customer, the assigned courier, the merchant on the parcel, and staff. */
    public static boolean canView(Parcel parcel, AuthenticatedUser caller) {
        return isOwningCustomer(parcel, caller)
                || isAssignedCourier(parcel, caller)
                || isOwningMerchant(parcel, caller)
                || caller.isStaff();
    }

    /** The owning customer, the owning merchant, or staff (couriers cannot download customer receipts). */
    public static boolean canViewReceipt(Parcel parcel, AuthenticatedUser caller) {
        return isOwningCustomer(parcel, caller)
                || isOwningMerchant(parcel, caller)
                || caller.isStaff();
    }

    /** Only the customer who booked it. */
    public static boolean canCancel(Parcel parcel, AuthenticatedUser caller) {
        return isOwningCustomer(parcel, caller);
    }

    /**
     * Only the courier the parcel is assigned to, or staff (M4). Another courier is refused like a
     * missing id, so couriers cannot probe for each other's parcels.
     */
    public static boolean canUpdateStatus(Parcel parcel, AuthenticatedUser caller) {
        return isAssignedCourier(parcel, caller) || caller.isStaff();
    }

    /** A failed delivery is re-attempted by dispatch, not by the courier who failed it. */
    public static boolean canReattempt(AuthenticatedUser caller) {
        return caller.isStaff();
    }

    private static boolean isOwningCustomer(Parcel parcel, AuthenticatedUser caller) {
        return caller.hasRole(Roles.CUSTOMER) && caller.id().equals(parcel.customerId());
    }

    private static boolean isAssignedCourier(Parcel parcel, AuthenticatedUser caller) {
        return caller.hasRole(Roles.COURIER) && caller.id().equals(parcel.courierId());
    }

    private static boolean isOwningMerchant(Parcel parcel, AuthenticatedUser caller) {
        return caller.hasAnyRole(Roles.MERCHANT, Roles.MERCHANT_API)
                && parcel.merchantId() != null
                && caller.merchantId().filter(parcel.merchantId()::equals).isPresent();
    }
}
