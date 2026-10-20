package lk.swiftdrop.events.parcel;

import lk.swiftdrop.events.EventPayload;
import lk.swiftdrop.events.Topics;
import lk.swiftdrop.events.common.Types.Location;
import lk.swiftdrop.events.common.Types.Money;
import lk.swiftdrop.events.common.Types.ParcelSource;
import lk.swiftdrop.events.common.Types.ServiceLevel;
import lk.swiftdrop.events.common.Types.SizeClass;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Schema: contracts/events/parcel-created.v1.schema.json */
public record ParcelCreated(UUID parcelId, String trackingNumber, ParcelSource source,
                            String customerId, String merchantId, String merchantOrderRef,
                            Location pickup, Location dropoff, SizeClass sizeClass, BigDecimal weightKg,
                            ServiceLevel serviceLevel, Money fee, Contacts contacts, Instant createdAt)
        implements EventPayload {

    public static final String TYPE = "ParcelCreated";
    public static final int VERSION = 1;

    /** Minimum contact data for notifications. Masked in logs; stripped from merchant webhooks. */
    public record Contacts(String senderName, String senderEmail, String senderPhone,
                           String recipientName, String recipientPhone) { }

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
        return Topics.PARCEL_CREATED;
    }

    @Override
    public String partitionKey() {
        return parcelId.toString();
    }
}
