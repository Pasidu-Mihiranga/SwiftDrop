package lk.swiftdrop.user.api.mapper;

import lk.swiftdrop.user.api.dto.CourierResponse;
import lk.swiftdrop.user.domain.model.Courier;
import lk.swiftdrop.user.domain.model.CourierProfile;

public final class CourierApiMapper {

    private CourierApiMapper() {
    }

    public static CourierResponse toResponse(Courier courier) {
        CourierProfile profile = courier.profile().orElse(null);
        return new CourierResponse(courier.id(), courier.fullName(), courier.email(), courier.enabled(),
                profile != null,
                profile == null ? null : profile.vehicleType().name(),
                profile == null ? null : profile.homeZoneId(),
                profile == null ? null : profile.maxDailyCapacity(),
                profile == null ? null : profile.active(),
                courier.assignable(),
                profile == null ? null : profile.updatedAt());
    }
}
