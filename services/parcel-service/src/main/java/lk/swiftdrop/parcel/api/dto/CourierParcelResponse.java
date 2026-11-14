package lk.swiftdrop.parcel.api.dto;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import java.util.UUID;
public record CourierParcelResponse(UUID id, String trackingNumber, ParcelStatus status, String customerId, String merchantId, String courierId) { }
