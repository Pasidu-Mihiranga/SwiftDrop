package lk.swiftdrop.reporting.application.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.SortedSet;
import java.util.UUID;

/**
 * Write side of the read model: the per-parcel facts and the daily rollups derived from them.
 * Every method runs inside the caller's transaction.
 */
public interface ProjectionStore {

    /**
     * Serialises all work on one parcel until the transaction ends, so two events for the same
     * parcel never work out their affected days from each other's uncommitted state.
     */
    void lockParcel(UUID parcelId);

    /** Records what ParcelCreated knows; replaces those columns if they were already set. */
    void upsertCreated(CreatedFact fact);

    /** Keeps the most recent assignment, whatever order the assignment events arrive in. */
    void upsertAssignment(AssignmentFact fact);

    void insertTransition(TransitionFact fact);

    /** Business days whose rollups include this parcel: its creation day and each transition day. */
    SortedSet<LocalDate> daysTouchedBy(UUID parcelId);

    /** Rebuilds both rollups for one day from the facts, holding a lock on that day meanwhile. */
    void recomputeDay(LocalDate day);

    record CreatedFact(UUID parcelId, String trackingNumber, String zoneCode, String source, String merchantId,
                       Instant createdAt, LocalDate createdDate) {
    }

    record AssignmentFact(UUID parcelId, String trackingNumber, String courierId, Instant assignedAt) {
    }

    record TransitionFact(UUID eventId, UUID parcelId, String fromStatus, String toStatus, String courierId,
                          String failureReason, Instant changedAt, LocalDate businessDate) {
    }
}
