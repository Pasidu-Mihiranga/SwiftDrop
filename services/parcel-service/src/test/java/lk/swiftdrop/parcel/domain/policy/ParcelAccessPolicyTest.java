package lk.swiftdrop.parcel.domain.policy;

import lk.swiftdrop.parcel.ParcelFixtures;
import lk.swiftdrop.parcel.domain.model.Parcel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParcelAccessPolicyTest {

    private static final String MERCHANT_ID = "00000000-0000-4000-8000-000000000001";

    private final Parcel parcel = withCourierAndMerchant(ParcelFixtures.bookedBy("cust-1"));

    private static Parcel withCourierAndMerchant(Parcel booked) {
        return new Parcel(booked.id(), booked.trackingNumber(), booked.source(), booked.customerId(), MERCHANT_ID,
                booked.merchantOrderRef(), booked.details(), booked.status(), "courier-1", booked.history(),
                booked.createdAt(), booked.updatedAt(), booked.version());
    }

    @Test
    void theOwnerTheAssignedCourierTheMerchantAndStaffMayView() {
        assertThat(ParcelAccessPolicy.canView(parcel, ParcelFixtures.customer("cust-1"))).isTrue();
        assertThat(ParcelAccessPolicy.canView(parcel, ParcelFixtures.user("courier-1", "COURIER", null))).isTrue();
        assertThat(ParcelAccessPolicy.canView(parcel, ParcelFixtures.user("owner-1", "MERCHANT", MERCHANT_ID))).isTrue();
        assertThat(ParcelAccessPolicy.canView(parcel, ParcelFixtures.user("svc-1", "MERCHANT_API", MERCHANT_ID))).isTrue();
        assertThat(ParcelAccessPolicy.canView(parcel, ParcelFixtures.user("disp-1", "DISPATCHER", null))).isTrue();
        assertThat(ParcelAccessPolicy.canView(parcel, ParcelFixtures.user("admin-1", "ADMIN", null))).isTrue();
    }

    @Test
    void everyoneElseIsRefused() {
        assertThat(ParcelAccessPolicy.canView(parcel, ParcelFixtures.customer("cust-2"))).isFalse();
        assertThat(ParcelAccessPolicy.canView(parcel, ParcelFixtures.user("courier-2", "COURIER", null))).isFalse();
        assertThat(ParcelAccessPolicy.canView(parcel, ParcelFixtures.user("owner-2", "MERCHANT", "another-merchant")))
                .isFalse();
        assertThat(ParcelAccessPolicy.canView(parcel, ParcelFixtures.user("owner-3", "MERCHANT", null))).isFalse();
    }

    @Test
    void anIdThatMatchesInTheWrongRoleDoesNotGrantAccess() {
        // The customer id only counts for a caller acting as a customer, the courier id for a courier.
        assertThat(ParcelAccessPolicy.canView(parcel, ParcelFixtures.user("cust-1", "COURIER", null))).isFalse();
        assertThat(ParcelAccessPolicy.canView(parcel, ParcelFixtures.user("courier-1", "CUSTOMER", null))).isFalse();
    }

    @Test
    void aCustomerParcelWithoutAMerchantIsNotVisibleToMerchants() {
        Parcel customerParcel = ParcelFixtures.bookedBy("cust-1");

        assertThat(ParcelAccessPolicy.canView(customerParcel, ParcelFixtures.user("owner-1", "MERCHANT", MERCHANT_ID)))
                .isFalse();
    }

    @Test
    void onlyTheOwningCustomerMayCancel() {
        assertThat(ParcelAccessPolicy.canCancel(parcel, ParcelFixtures.customer("cust-1"))).isTrue();
        assertThat(ParcelAccessPolicy.canCancel(parcel, ParcelFixtures.customer("cust-2"))).isFalse();
        assertThat(ParcelAccessPolicy.canCancel(parcel, ParcelFixtures.user("courier-1", "COURIER", null))).isFalse();
        assertThat(ParcelAccessPolicy.canCancel(parcel, ParcelFixtures.user("admin-1", "ADMIN", null))).isFalse();
    }
}
