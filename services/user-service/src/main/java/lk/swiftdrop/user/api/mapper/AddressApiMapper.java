package lk.swiftdrop.user.api.mapper;

import lk.swiftdrop.common.web.error.BadRequestException;
import lk.swiftdrop.user.api.dto.AddressRequest;
import lk.swiftdrop.user.api.dto.AddressResponse;
import lk.swiftdrop.user.domain.model.Address;
import lk.swiftdrop.user.domain.model.AddressDetails;

public final class AddressApiMapper {

    private AddressApiMapper() {
    }

    /** Bean Validation covers each field; the domain adds the rules that span fields (lat and lng together). */
    public static AddressDetails toDetails(AddressRequest request) {
        try {
            return new AddressDetails(request.label(), request.line1(), request.line2(), request.city(),
                    request.district(), request.postalCode(), request.lat(), request.lng());
        } catch (IllegalArgumentException invalid) {
            throw new BadRequestException("INVALID_ADDRESS", invalid.getMessage());
        }
    }

    public static AddressResponse toResponse(Address address) {
        AddressDetails details = address.details();
        return new AddressResponse(address.id(), details.label(), details.line1(), details.line2(), details.city(),
                details.district(), details.postalCode(), details.lat(), details.lng(), address.isDefault(),
                address.createdAt(), address.updatedAt());
    }
}
