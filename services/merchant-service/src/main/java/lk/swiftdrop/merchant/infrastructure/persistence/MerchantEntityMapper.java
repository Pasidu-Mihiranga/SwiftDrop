package lk.swiftdrop.merchant.infrastructure.persistence;

import lk.swiftdrop.merchant.domain.model.BusinessDetails;
import lk.swiftdrop.merchant.domain.model.Merchant;

/** Translates between the merchant aggregate and its row. */
final class MerchantEntityMapper {

    private MerchantEntityMapper() {
    }

    static MerchantEntity toNewEntity(Merchant merchant) {
        BusinessDetails business = merchant.business();
        MerchantEntity entity = new MerchantEntity();
        entity.setId(merchant.id());
        entity.setOwnerUserId(merchant.ownerUserId());
        entity.setBusinessName(business.businessName());
        entity.setBrNumber(business.brNumber());
        entity.setContactEmail(business.contactEmail());
        entity.setContactPhone(business.contactPhone());
        entity.setWebsite(business.website());
        entity.setPickupAddress(business.pickupAddress());
        entity.setCreatedAt(merchant.createdAt());
        applyDecision(entity, merchant);
        return entity;
    }

    /** Copies the only columns that change after an application is made. */
    static void applyDecision(MerchantEntity entity, Merchant merchant) {
        entity.setStatus(merchant.status());
        entity.setReviewedBy(merchant.reviewedBy());
        entity.setReviewedAt(merchant.reviewedAt());
        entity.setRejectionReason(merchant.rejectionReason());
        entity.setUpdatedAt(merchant.updatedAt());
    }

    static Merchant toDomain(MerchantEntity entity) {
        BusinessDetails business = new BusinessDetails(entity.getBusinessName(), entity.getBrNumber(),
                entity.getContactEmail(), entity.getContactPhone(), entity.getWebsite(), entity.getPickupAddress());
        return new Merchant(entity.getId(), entity.getOwnerUserId(), business, entity.getStatus(),
                entity.getReviewedBy(), entity.getReviewedAt(), entity.getRejectionReason(), entity.getCreatedAt(),
                entity.getUpdatedAt(), entity.getVersion());
    }
}
