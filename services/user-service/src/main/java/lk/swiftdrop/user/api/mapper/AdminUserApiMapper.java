package lk.swiftdrop.user.api.mapper;

import lk.swiftdrop.common.web.error.BadRequestException;
import lk.swiftdrop.user.api.dto.AdminUserPageResponse;
import lk.swiftdrop.user.api.dto.AdminUserResponse;
import lk.swiftdrop.user.api.dto.CreateStaffRequest;
import lk.swiftdrop.user.application.model.ManagedUser;
import lk.swiftdrop.user.application.model.NewStaffAccount;
import lk.swiftdrop.user.application.model.UserPage;
import lk.swiftdrop.user.domain.model.PhoneNumber;
import lk.swiftdrop.user.domain.model.UserRole;

public final class AdminUserApiMapper {

    private AdminUserApiMapper() {
    }

    public static NewStaffAccount toAccount(CreateStaffRequest request) {
        if (request.courierDetailsGiven() && !request.courierDetailsComplete()) {
            throw new BadRequestException("COURIER_DETAILS_INCOMPLETE",
                    "Give the vehicle, home zone and daily capacity together, or none of them");
        }
        NewStaffAccount.CourierDetails courier = request.courierDetailsComplete()
                ? new NewStaffAccount.CourierDetails(request.vehicleType(), request.homeZoneId(), request.maxDailyCapacity())
                : null;
        PhoneNumber phone = request.phone() == null || request.phone().isBlank() ? null : PhoneNumber.parse(request.phone());
        return new NewStaffAccount(request.email(), request.fullName().strip(), phone, request.role(), courier);
    }

    public static AdminUserResponse toResponse(ManagedUser user) {
        return new AdminUserResponse(user.id(), user.email(), user.fullName(), user.enabled(),
                user.roles().stream().map(UserRole::name).sorted().toList());
    }

    public static AdminUserPageResponse toResponse(UserPage page) {
        return new AdminUserPageResponse(page.items().stream().map(AdminUserApiMapper::toResponse).toList(),
                page.total(), page.page(), page.size());
    }
}
