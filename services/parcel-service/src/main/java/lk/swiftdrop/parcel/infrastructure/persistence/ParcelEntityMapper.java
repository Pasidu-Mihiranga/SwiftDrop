package lk.swiftdrop.parcel.infrastructure.persistence;

import lk.swiftdrop.events.common.Types.Money;
import lk.swiftdrop.parcel.domain.model.BookingDetails;
import lk.swiftdrop.parcel.domain.model.Parcel;
import lk.swiftdrop.parcel.domain.model.Recipient;
import lk.swiftdrop.parcel.domain.model.StatusChange;
import lk.swiftdrop.parcel.domain.model.TrackingNumber;

import java.util.List;

/** Translates between the parcel aggregate and its rows. */
final class ParcelEntityMapper {

    private ParcelEntityMapper() {
    }

    static ParcelEntity toNewEntity(Parcel parcel) {
        BookingDetails details = parcel.details();
        ParcelEntity entity = new ParcelEntity();
        entity.setId(parcel.id());
        entity.setTrackingNumber(parcel.trackingNumber().value());
        entity.setSource(parcel.source());
        entity.setCustomerId(parcel.customerId());
        entity.setMerchantId(parcel.merchantId());
        entity.setMerchantOrderRef(parcel.merchantOrderRef());
        entity.setPickup(details.pickup());
        entity.setDropoff(details.dropoff());
        entity.setPickupZone(details.pickupZone());
        entity.setDropoffZone(details.dropoffZone());
        entity.setRecipientName(details.recipient().name());
        entity.setRecipientPhone(details.recipient().phone());
        entity.setRecipientEmail(details.recipient().email());
        entity.setWeightKg(details.weightKg());
        entity.setSizeClass(details.sizeClass());
        entity.setServiceLevel(details.serviceLevel());
        entity.setFee(details.fee().amount());
        entity.setCurrency(details.fee().currency());
        entity.setQuoteId(details.quoteId());
        entity.setNotes(details.notes());
        entity.setCreatedAt(parcel.createdAt());
        applyChanges(entity, parcel);
        return entity;
    }

    /** Copies what can change after booking and appends the history entries the rows do not have yet. */
    static void applyChanges(ParcelEntity entity, Parcel parcel) {
        entity.setStatus(parcel.status());
        entity.setCourierId(parcel.courierId());
        entity.setUpdatedAt(parcel.updatedAt());
        parcel.history().stream()
                .skip(entity.getHistory().size())
                .map(change -> new ParcelStatusHistoryEntity(entity, change.fromStatus(), change.toStatus(),
                        change.actorId(), change.actorRole(), change.note(), change.occurredAt()))
                .forEach(entity.getHistory()::add);
    }

    static Parcel toDomain(ParcelEntity entity, boolean withHistory) {
        BookingDetails details = new BookingDetails(entity.getPickup(), entity.getDropoff(), entity.getPickupZone(),
                entity.getDropoffZone(),
                new Recipient(entity.getRecipientName(), entity.getRecipientPhone(), entity.getRecipientEmail()),
                entity.getWeightKg(), entity.getSizeClass(), entity.getServiceLevel(),
                new Money(entity.getFee(), entity.getCurrency()), entity.getQuoteId(), entity.getNotes());
        List<StatusChange> history = withHistory
                ? entity.getHistory().stream().map(ParcelEntityMapper::toDomain).toList()
                : List.of();
        return new Parcel(entity.getId(), new TrackingNumber(entity.getTrackingNumber()), entity.getSource(),
                entity.getCustomerId(), entity.getMerchantId(), entity.getMerchantOrderRef(), details,
                entity.getStatus(), entity.getCourierId(), history, entity.getCreatedAt(), entity.getUpdatedAt(),
                entity.getVersion());
    }

    private static StatusChange toDomain(ParcelStatusHistoryEntity row) {
        return new StatusChange(row.getFromStatus(), row.getToStatus(), row.getActorId(), row.getActorRole(),
                row.getNote(), row.getOccurredAt());
    }
}
