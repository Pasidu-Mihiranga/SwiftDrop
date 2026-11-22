package lk.swiftdrop.merchant.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lk.swiftdrop.merchant.domain.model.BusinessDetails;

/**
 * A business applies to become a merchant. The applicant is the caller; no owner id is accepted
 * in the body, so nobody can apply on someone else's behalf.
 *
 * @param contactPhone a Sri Lankan mobile or landline number
 * @param website      optional, http or https
 */
public record RegisterMerchantRequest(
        @NotBlank @Size(max = 150) String businessName,
        @NotBlank @Size(max = 30)
        @Pattern(regexp = "^[A-Za-z0-9 /-]+$", message = "may contain only letters, digits, spaces, '/' and '-'")
        String brNumber,
        // The domain must contain a dot: lifecycle events carry this address and their schema demands it.
        @NotBlank @Size(max = 254) @Email(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") String contactEmail,
        @NotBlank @Pattern(regexp = "^(\\+94|0)[1-9][0-9]{8}$", message = "must be a Sri Lankan phone number")
        String contactPhone,
        @Size(max = 255) @Pattern(regexp = "^https?://\\S+$", message = "must be an http or https address")
        String website,
        @NotNull @Valid PickupAddressDto pickupAddress) {

    public BusinessDetails toDomain() {
        return new BusinessDetails(businessName, brNumber, contactEmail, contactPhone, website,
                pickupAddress.toDomain());
    }
}
