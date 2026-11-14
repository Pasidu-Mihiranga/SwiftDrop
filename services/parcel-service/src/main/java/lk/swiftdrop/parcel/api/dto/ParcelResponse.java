package lk.swiftdrop.parcel.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lk.swiftdrop.events.common.Types.Money;
import lk.swiftdrop.events.common.Types.ParcelSource;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.events.common.Types.ServiceLevel;
import lk.swiftdrop.events.common.Types.SizeClass;
import lk.swiftdrop.parcel.domain.model.BookingDetails;
import lk.swiftdrop.parcel.domain.model.Parcel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The Parcel schema in contracts/openapi/parcel-service.v1.yaml.
 *
 * @param history present on a single parcel; left out of lists
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParcelResponse(UUID id, String trackingNumber, ParcelStatus status, ParcelSource source,
                             String merchantOrderRef, AddressDto pickup, AddressDto dropoff, ContactDto recipient,
                             BigDecimal weightKg, SizeClass sizeClass, ServiceLevel serviceLevel, Money fee,
                             String notes, String courierId, List<StatusHistoryEntryResponse> history,
                             Instant createdAt) {

    /** A parcel with its history, for the detail page and for the answer to a change. */
    public static ParcelResponse detailed(Parcel parcel) {
        return from(parcel, parcel.history().stream().map(StatusHistoryEntryResponse::from).toList());
    }

    /** A parcel as a list row. */
    public static ParcelResponse summary(Parcel parcel) {
        return from(parcel, null);
    }

    private static ParcelResponse from(Parcel parcel, List<StatusHistoryEntryResponse> history) {
        BookingDetails details = parcel.details();
        return new ParcelResponse(parcel.id(), parcel.trackingNumber().value(), parcel.status(), parcel.source(),
                parcel.merchantOrderRef(), AddressDto.from(details.pickup()), AddressDto.from(details.dropoff()),
                ContactDto.from(details.recipient()), details.weightKg(), details.sizeClass(),
                details.serviceLevel(), details.fee(), details.notes(), parcel.courierId(), history,
                parcel.createdAt());
    }
}
