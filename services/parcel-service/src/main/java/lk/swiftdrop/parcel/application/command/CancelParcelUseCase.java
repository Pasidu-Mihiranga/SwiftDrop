package lk.swiftdrop.parcel.application.command;

import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.events.common.Types.Actor;
import lk.swiftdrop.events.common.Types.ActorRole;
import lk.swiftdrop.events.parcel.ParcelStatusChanged;
import lk.swiftdrop.outbox.OutboxWriter;
import lk.swiftdrop.parcel.application.port.out.ParcelRepository;
import lk.swiftdrop.parcel.domain.model.Parcel;
import lk.swiftdrop.parcel.domain.policy.ParcelAccessPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Cancels a parcel that has not been picked up. One transaction sets CANCELLED, appends the
 * history row and writes {@code ParcelStatusChanged}. The parcel's version stops two cancels from
 * both succeeding.
 */
@Service
public class CancelParcelUseCase {

    private final ParcelRepository parcels;
    private final OutboxWriter outbox;
    private final Clock clock;

    public CancelParcelUseCase(ParcelRepository parcels, OutboxWriter outbox, Clock clock) {
        this.parcels = parcels;
        this.outbox = outbox;
        this.clock = clock;
    }

    /**
     * @throws ResourceNotFoundException when the parcel is missing or the caller is not its owner
     * @throws lk.swiftdrop.parcel.domain.exception.ParcelNotCancellableException when it has already moved on
     */
    @Transactional
    public Parcel cancel(AuthenticatedUser caller, UUID parcelId) {
        Parcel parcel = parcels.findById(parcelId)
                .filter(found -> ParcelAccessPolicy.canCancel(found, caller))
                .orElseThrow(() -> ResourceNotFoundException.of("Parcel", parcelId));
        Parcel cancelled = parcels.save(parcel.cancel(caller.id(), ActorRole.CUSTOMER, clock.instant()));
        outbox.publish(new ParcelStatusChanged(cancelled.id(), cancelled.trackingNumber().value(),
                cancelled.customerId(), cancelled.merchantId(), cancelled.courierId(), parcel.status(),
                cancelled.status(), null, null, null, null, new Actor(caller.id(), ActorRole.CUSTOMER),
                cancelled.updatedAt()));
        return cancelled;
    }
}
