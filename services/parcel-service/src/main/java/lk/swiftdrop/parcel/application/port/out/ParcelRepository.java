package lk.swiftdrop.parcel.application.port.out;

import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.parcel.application.model.MerchantOrderFilter;
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

    /** The parcels assigned to one courier, newest first, optionally in one status. */
    PagedResult<Parcel> findByCourier(String courierId, ParcelStatus status, int page, int size);

    /** One merchant's orders matching the filter, newest first. Other merchants' orders are never included. */
    PagedResult<Parcel> findByMerchant(String merchantId, MerchantOrderFilter filter, int page, int size);
}
