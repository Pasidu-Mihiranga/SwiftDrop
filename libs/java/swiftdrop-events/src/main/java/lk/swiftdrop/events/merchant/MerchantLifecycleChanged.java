package lk.swiftdrop.events.merchant;

import lk.swiftdrop.events.EventPayload;
import lk.swiftdrop.events.Topics;
import lk.swiftdrop.events.common.Types.Actor;

import java.time.Instant;
import java.util.UUID;

/** Schema: contracts/events/merchant-lifecycle.v1.schema.json */
public record MerchantLifecycleChanged(UUID merchantId, String businessName, String ownerUserId, String contactEmail,
                                       MerchantStatus previousStatus, MerchantStatus status, String reason,
                                       Actor changedBy, Instant changedAt)
        implements EventPayload {

    public static final String TYPE = "MerchantLifecycleChanged";
    public static final int VERSION = 1;

    public enum MerchantStatus { PENDING, APPROVED, REJECTED, SUSPENDED }

    @Override
    public String eventType() {
        return TYPE;
    }

    @Override
    public int eventVersion() {
        return VERSION;
    }

    @Override
    public String topic() {
        return Topics.MERCHANT_LIFECYCLE;
    }

    @Override
    public String partitionKey() {
        return merchantId.toString();
    }
}
