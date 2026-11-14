package lk.swiftdrop.parcel.application.port.out;

import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.parcel.application.model.PagedResult;
import lk.swiftdrop.parcel.domain.model.Parcel;
import lk.swiftdrop.parcel.domain.model.TrackingNumber;

import java.util.Optional;
import java.util.UUID;

public interface ParcelRepository {

    Parcel save(Parcel parcel);

    Optional<Parcel> findById(UUID id);

    boolean existsByTrackingNumber(TrackingNumber trackingNumber);

    PagedResult<Parcel> findByCustomer(String customerId, ParcelStatus status, int page, int size);
}
