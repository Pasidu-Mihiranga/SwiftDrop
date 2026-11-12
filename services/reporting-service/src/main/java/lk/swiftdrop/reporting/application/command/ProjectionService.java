package lk.swiftdrop.reporting.application.command;

import lk.swiftdrop.events.EventEnvelope;
import lk.swiftdrop.events.common.Types.Location;
import lk.swiftdrop.events.dispatch.ParcelAssigned;
import lk.swiftdrop.events.parcel.ParcelCreated;
import lk.swiftdrop.events.parcel.ParcelStatusChanged;
import lk.swiftdrop.outbox.IdempotentConsumer;
import lk.swiftdrop.reporting.application.port.out.ProjectionStore;
import lk.swiftdrop.reporting.application.port.out.ProjectionStore.AssignmentFact;
import lk.swiftdrop.reporting.application.port.out.ProjectionStore.CreatedFact;
import lk.swiftdrop.reporting.application.port.out.ProjectionStore.TransitionFact;
import lk.swiftdrop.reporting.domain.model.BusinessCalendar;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Applies parcel events to the reporting read model. Each event is handled in one transaction:
 * drop it if it was seen before, record the fact, then rebuild the rollups for every day the
 * parcel touches. Rebuilding (instead of adding one) is what makes the result independent of
 * the order events arrive in. Each handler returns {@code false} for a duplicate delivery.
 */
@Service
@Transactional
public class ProjectionService {

    static final String CREATED_CONSUMER = "reporting-parcel-created";
    static final String STATUS_CONSUMER = "reporting-parcel-status";
    static final String ASSIGNED_CONSUMER = "reporting-parcel-assigned";

    private final IdempotentConsumer idempotency;
    private final ProjectionStore store;
    private final BusinessCalendar calendar;

    public ProjectionService(IdempotentConsumer idempotency, ProjectionStore store, BusinessCalendar calendar) {
        this.idempotency = idempotency;
        this.store = store;
        this.calendar = calendar;
    }

    public boolean onParcelCreated(EventEnvelope<ParcelCreated> event) {
        if (!idempotency.firstDelivery(event.eventId(), CREATED_CONSUMER)) {
            return false;
        }
        ParcelCreated parcel = event.data();
        Instant createdAt = parcel.createdAt() != null ? parcel.createdAt() : event.occurredAt();
        store.lockParcel(parcel.parcelId());
        store.upsertCreated(new CreatedFact(parcel.parcelId(), parcel.trackingNumber(), zoneOf(parcel.dropoff()),
                parcel.source() == null ? null : parcel.source().name(), parcel.merchantId(),
                createdAt, calendar.dateOf(createdAt)));
        refresh(parcel.parcelId());
        return true;
    }

    public boolean onStatusChanged(EventEnvelope<ParcelStatusChanged> event) {
        if (!idempotency.firstDelivery(event.eventId(), STATUS_CONSUMER)) {
            return false;
        }
        ParcelStatusChanged change = event.data();
        Instant changedAt = change.changedAt() != null ? change.changedAt() : event.occurredAt();
        store.lockParcel(change.parcelId());
        store.insertTransition(new TransitionFact(event.eventId(), change.parcelId(),
                change.fromStatus() == null ? null : change.fromStatus().name(), change.toStatus().name(),
                change.courierId(), change.failureReason() == null ? null : change.failureReason().name(),
                changedAt, calendar.dateOf(changedAt)));
        refresh(change.parcelId());
        return true;
    }

    public boolean onParcelAssigned(EventEnvelope<ParcelAssigned> event) {
        if (!idempotency.firstDelivery(event.eventId(), ASSIGNED_CONSUMER)) {
            return false;
        }
        ParcelAssigned assignment = event.data();
        Instant assignedAt = assignment.assignedAt() != null ? assignment.assignedAt() : event.occurredAt();
        store.lockParcel(assignment.parcelId());
        store.upsertAssignment(new AssignmentFact(assignment.parcelId(), assignment.trackingNumber(),
                assignment.courierId(), assignedAt));
        // A transition that carries no courier id is credited to the assigned courier, so those days change too.
        refresh(assignment.parcelId());
        return true;
    }

    /** Days are rebuilt in ascending order, so concurrent transactions take the day locks in the same order. */
    private void refresh(UUID parcelId) {
        store.daysTouchedBy(parcelId).forEach(store::recomputeDay);
    }

    private static String zoneOf(Location location) {
        return location == null ? null : location.zoneCode();
    }
}
