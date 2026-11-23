package lk.swiftdrop.parcel.application.query;

import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.parcel.application.model.PagedResult;
import lk.swiftdrop.parcel.application.port.out.ParcelRepository;
import lk.swiftdrop.parcel.domain.model.Parcel;
import lk.swiftdrop.parcel.domain.policy.ParcelAccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Reads parcels for the caller. Ownership is decided by {@link ParcelAccessPolicy} alone. */
@Service
@Transactional(readOnly = true)
public class ParcelQueryService {

    private final ParcelRepository parcels;

    public ParcelQueryService(ParcelRepository parcels) {
        this.parcels = parcels;
    }

    /** The caller's own parcels, newest first. */
    public PagedResult<Parcel> mine(AuthenticatedUser customer, ParcelStatus status, int page, int size) {
        return parcels.findByCustomer(customer.id(), status, page, size);
    }

    /** The parcels assigned to the calling courier, newest first (M4). */
    public PagedResult<Parcel> assignedTo(AuthenticatedUser courier, ParcelStatus status, int page, int size) {
        return parcels.findByCourier(courier.id(), status, page, size);
    }

    /**
     * One parcel with its history.
     *
     * @throws ResourceNotFoundException for a missing id and for a parcel the caller may not see,
     *                                   so the response never reveals that the parcel exists
     */
    public Parcel get(AuthenticatedUser caller, UUID parcelId) {
        return parcels.findById(parcelId)
                .filter(found -> ParcelAccessPolicy.canView(found, caller))
                .orElseThrow(() -> ResourceNotFoundException.of("Parcel", parcelId));
    }

    /**
     * Parcel details for generating a receipt. Only owner, merchant or staff may download a receipt.
     * Unauthorized callers receive a 404 so that existence is never leaked.
     */
    public Parcel getForReceipt(AuthenticatedUser caller, UUID parcelId) {
        return parcels.findById(parcelId)
                .filter(found -> ParcelAccessPolicy.canViewReceipt(found, caller))
                .orElseThrow(() -> ResourceNotFoundException.of("Parcel", parcelId));
    }
}
