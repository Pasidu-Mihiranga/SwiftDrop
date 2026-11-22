package lk.swiftdrop.dispatch.application;

import lk.swiftdrop.common.web.error.*;
import lk.swiftdrop.dispatch.domain.AllocationRules;
import lk.swiftdrop.events.*;
import lk.swiftdrop.events.common.Types.*;
import lk.swiftdrop.events.dispatch.ParcelAssigned;
import lk.swiftdrop.events.parcel.*;
import lk.swiftdrop.outbox.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static lk.swiftdrop.dispatch.application.DispatchModels.*;

@Service
public class DispatchService {
    private final DispatchStore store;
    private final CourierDirectory directory;
    private final IdempotentConsumer idempotency;
    private final OutboxWriter outbox;
    private final Clock clock;
    private final ZoneId zone;
    public DispatchService(DispatchStore store, CourierDirectory directory, IdempotentConsumer idempotency,
                           OutboxWriter outbox, Clock clock,
                           @Value("${swiftdrop.dispatch.time-zone:Asia/Colombo}") String zone) {
        this.store = store; this.directory = directory; this.idempotency = idempotency;
        this.outbox = outbox; this.clock = clock; this.zone = ZoneId.of(zone);
    }
    public LocalDate today() { return LocalDate.now(clock.withZone(zone)); }
    @Transactional
    public void created(EventEnvelope<ParcelCreated> event) {
        if (idempotency.firstDelivery(event.eventId(), "dispatch-created")) store.created(event.data());
    }
    @Transactional
    public void status(EventEnvelope<ParcelStatusChanged> event) {
        if (idempotency.firstDelivery(event.eventId(), "dispatch-status")) store.status(event.data());
    }
    @Transactional(readOnly = true)
    public Page unassigned(String zone, int page, int size) { return store.unassigned(zone, page, size); }
    @Transactional(readOnly = true)
    public List<Parcel> runs(LocalDate date, String courier) { return store.assignments(date == null ? today() : date, courier); }
    public List<Workload> workloads(String token) {
        var counts = store.counts(today(), zone);
        return directory.couriers(token).stream().map(c -> {
            var n = counts.getOrDefault(c.id(), new Counts(0, 0, 0));
            return new Workload(c.id(), c.fullName(), c.homeZoneId(), c.maxDailyCapacity(), c.assignable(),
                    n.activeCount(), n.deliveredToday(), n.failedToday());
        }).toList();
    }
    @Transactional
    public Allocation assign(UUID parcelId, String courier, LocalDate date, String actor, boolean admin, String token) {
        var eligible = eligible(courier, token);
        var parcel = store.lock(parcelId);
        if (parcel.assignmentId() != null) throw new ConflictException("ALREADY_ASSIGNED", "Parcel already has an assignment");
        return allocate(parcel, eligible, date == null ? today() : date, actor, admin);
    }
    @Transactional
    public Allocation reassign(UUID id, String courier, String actor, boolean admin, String token) {
        var eligible = eligible(courier, token);
        var parcel = store.lockAssignment(id);
        return allocate(parcel, eligible, parcel.runDate(), actor, admin);
    }
    private Courier eligible(String courier, String token) {
        return directory.couriers(token).stream().filter(c -> c.id().equals(courier) && c.assignable()).findFirst()
            .orElseThrow(() -> new BusinessRuleViolationException("COURIER_UNAVAILABLE", "Courier is not available for assignment"));
    }
    private Allocation allocate(Parcel parcel, Courier courier, LocalDate date, String actor, boolean admin) {
        try { AllocationRules.validate(parcel.status(), parcel.courierId(), courier.id()); }
        catch (IllegalArgumentException ex) { throw new ConflictException("INVALID_ALLOCATION", ex.getMessage()); }
        if (date.isBefore(today())) throw new BusinessRuleViolationException("PAST_RUN_DATE", "Run date cannot be in the past");
        var result = store.save(parcel, courier.id(), date, actor, clock.instant());
        outbox.publish(new ParcelAssigned(result.id(), parcel.parcelId(), parcel.trackingNumber(), courier.id(),
            result.previousCourierId(), date, new Actor(actor, admin ? ActorRole.ADMIN : ActorRole.DISPATCHER), result.assignedAt()));
        return result;
    }
}
