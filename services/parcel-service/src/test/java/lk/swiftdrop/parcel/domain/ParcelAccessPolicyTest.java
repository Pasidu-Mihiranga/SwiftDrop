package lk.swiftdrop.parcel.domain;

import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.parcel.domain.policy.ParcelAccessPolicy;
import lk.swiftdrop.parcel.infrastructure.ParcelEntity;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParcelAccessPolicyTest {
    @Test void foreignCourierCannotUpdateParcel() {
        var parcel=new ParcelEntity(UUID.randomUUID(),"SD261020ABC123","customer-1",null); parcel.assignCourier("courier-1");
        var caller=new AuthenticatedUser("courier-2",Optional.empty(),Optional.empty(),Set.of("COURIER"),Optional.empty());
        assertThatThrownBy(() -> new ParcelAccessPolicy().assertCanUpdate(parcel,caller)).isInstanceOf(RuntimeException.class).hasMessageContaining("not found");
    }
    @Test void assignedCourierCanUpdateParcel() {
        var parcel=new ParcelEntity(UUID.randomUUID(),"SD261020ABC124","customer-1",null); parcel.assignCourier("courier-1");
        var caller=new AuthenticatedUser("courier-1",Optional.empty(),Optional.empty(),Set.of("COURIER"),Optional.empty());
        new ParcelAccessPolicy().assertCanUpdate(parcel,caller);
    }
}
