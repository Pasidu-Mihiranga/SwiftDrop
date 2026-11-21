package lk.swiftdrop.merchant.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lk.swiftdrop.merchant.domain.model.BusinessDetails;
import lk.swiftdrop.merchant.domain.model.Merchant;
import lk.swiftdrop.merchant.domain.model.MerchantStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * The Merchant schema in contracts/openapi/merchant-service.v1.yaml. Shown only to the applicant
 * and to admins, who both need the contact details. Who reviewed it is not included: the applicant
 * sees the decision and its reason, not the admin's identity.
 *
 * @param rejectionReason present only when REJECTED
 * @param reviewedAt      when the latest decision was made; absent while PENDING
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MerchantResponse(UUID id, String businessName, String brNumber, String contactEmail,
                               String contactPhone, String website, PickupAddressDto pickupAddress,
                               MerchantStatus status, String rejectionReason, Instant reviewedAt,
                               Instant createdAt) {

    public static MerchantResponse from(Merchant merchant) {
        BusinessDetails business = merchant.business();
        return new MerchantResponse(merchant.id(), business.businessName(), business.brNumber(),
                business.contactEmail(), business.contactPhone(), business.website(),
                PickupAddressDto.from(business.pickupAddress()), merchant.status(), merchant.rejectionReason(),
                merchant.reviewedAt(), merchant.createdAt());
    }
}
