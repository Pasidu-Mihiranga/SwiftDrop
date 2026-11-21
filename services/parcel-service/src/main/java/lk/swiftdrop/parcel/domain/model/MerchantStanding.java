package lk.swiftdrop.parcel.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * parcel-service's copy of one merchant's status, built from MerchantLifecycleChanged events.
 *
 * <p>Kafka may deliver a merchant's events late or twice. {@link #supersededBy} decides whether an
 * incoming change replaces this one, so an old APPROVED arriving after a SUSPENDED can never
 * re-open a suspended merchant.
 *
 * @param businessName sender name on the merchant's parcels
 * @param contactEmail sender email on the merchant's parcels; may be null
 * @param changedAt    when merchant-service made the change, not when it arrived here
 */
public record MerchantStanding(String merchantId, MerchantStatus status, String businessName, String contactEmail,
                               Instant changedAt) {

    public MerchantStanding {
        Objects.requireNonNull(merchantId, "merchantId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(businessName, "businessName");
        Objects.requireNonNull(changedAt, "changedAt");
    }

    public boolean isApproved() {
        return status == MerchantStatus.APPROVED;
    }

    /**
     * Whether {@code incoming} is a newer fact about the same merchant. A change at the same instant
     * replaces this one only when it moves away from APPROVED, so a tie never re-opens a merchant.
     */
    public boolean supersededBy(MerchantStanding incoming) {
        if (!merchantId.equals(incoming.merchantId())) {
            throw new IllegalArgumentException("A standing can only be replaced by one for the same merchant");
        }
        if (incoming.changedAt().isAfter(changedAt)) {
            return true;
        }
        return incoming.changedAt().equals(changedAt) && !incoming.isApproved() && isApproved();
    }
}
