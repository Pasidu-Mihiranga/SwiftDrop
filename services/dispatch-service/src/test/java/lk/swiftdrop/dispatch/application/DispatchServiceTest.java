package lk.swiftdrop.dispatch.application;

import lk.swiftdrop.common.web.error.*;
import lk.swiftdrop.events.*;
import lk.swiftdrop.events.dispatch.ParcelAssigned;
import lk.swiftdrop.outbox.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.time.*;
import java.util.*;
import static lk.swiftdrop.dispatch.application.DispatchModels.*;
import static lk.swiftdrop.dispatch.DispatchFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DispatchServiceTest {
    DispatchStore store = mock(DispatchStore.class);
    CourierDirectory directory = mock(CourierDirectory.class);
    IdempotentConsumer idempotency = mock(IdempotentConsumer.class);
    OutboxWriter outbox = mock(OutboxWriter.class);
    Instant now = Instant.parse("2026-11-21T20:00:00Z");
    DispatchService service = new DispatchService(store,directory,idempotency,outbox,Clock.fixed(now,ZoneOffset.UTC),"Asia/Colombo");
    UUID id = UUID.randomUUID();
    @BeforeEach void setup() {
        when(directory.couriers("token")).thenReturn(List.of(new Courier("c1","Courier One","zone",20,true),new Courier("c2","Courier Two",null,10,false)));
    }
    Parcel summary(String status, UUID assignment, String courier) {
        return new Parcel(id,"SD1","CP","M","Colombo","Kandy",now,status,assignment,courier,service.today());
    }
    @Test void localBusinessDateAndReadQueries() {
        assertEquals(LocalDate.of(2026,11,22),service.today());
        service.unassigned("CP",0,20); verify(store).unassigned("CP",0,20);
        service.runs(null,"c1"); verify(store).assignments(service.today(),"c1");
        service.runs(LocalDate.of(2026,11,23),null); verify(store).assignments(LocalDate.of(2026,11,23),null);
    }
    @Test void publishesAllocationWithActorAndRunDate() {
        var p = summary("CREATED",null,null); when(store.lock(id)).thenReturn(p);
        when(store.save(p,"c1",service.today(),"dispatcher",now)).thenReturn(new Allocation(UUID.randomUUID(),id,"c1",null,service.today(),now));
        service.assign(id,"c1",null,"dispatcher",false,"token");
        var captor = ArgumentCaptor.forClass(ParcelAssigned.class); verify(outbox).publish(captor.capture());
        assertEquals("c1",captor.getValue().courierId()); assertNull(captor.getValue().previousCourierId());
        assertEquals("DISPATCHER",captor.getValue().assignedBy().role().name());
    }
    @Test void doubleAllocationAndCompletedParcelsAreConflicts() {
        when(store.lock(id)).thenReturn(summary("CREATED",UUID.randomUUID(),"other"));
        assertThrows(ConflictException.class,() -> service.assign(id,"c1",null,"d",false,"token"));
        when(store.lock(id)).thenReturn(summary("DELIVERED",null,null));
        assertThrows(ConflictException.class,() -> service.assign(id,"c1",null,"d",false,"token"));
        verifyNoInteractions(outbox);
    }
    @Test void rejectsUnavailableAndPastDate() {
        assertThrows(BusinessRuleViolationException.class,() -> service.assign(id,"c2",null,"d",false,"token"));
        assertThrows(BusinessRuleViolationException.class,() -> service.assign(id,"unknown",null,"d",false,"token"));
        when(store.lock(id)).thenReturn(summary("CREATED",null,null));
        assertThrows(BusinessRuleViolationException.class,() -> service.assign(id,"c1",service.today().minusDays(1),"d",false,"token"));
        verifyNoInteractions(outbox);
    }
    @Test void reassignPublishesPreviousCourierAndKeepsDate() {
        UUID assignment = UUID.randomUUID(); var p = summary("CREATED",assignment,"old");
        when(store.lockAssignment(assignment)).thenReturn(p);
        when(store.save(p,"c1",service.today(),"admin",now)).thenReturn(new Allocation(assignment,id,"c1","old",service.today(),now));
        service.reassign(assignment,"c1","admin",true,"token");
        var captor = ArgumentCaptor.forClass(ParcelAssigned.class); verify(outbox).publish(captor.capture());
        assertEquals("old",captor.getValue().previousCourierId()); assertEquals("ADMIN",captor.getValue().assignedBy().role().name());
    }
    @Test void workloadsIncludeIdleCouriers() {
        when(store.counts(service.today(),ZoneId.of("Asia/Colombo"))).thenReturn(Map.of("c1",new Counts(3,2,1)));
        var loads = service.workloads("token"); assertEquals(3,loads.getFirst().activeCount());
        assertEquals(2,loads.getFirst().deliveredToday()); assertEquals(1,loads.getFirst().failedToday());
        assertEquals(0,loads.get(1).activeCount()); assertEquals("Courier Two",loads.get(1).courierName());
    }
    @Test void duplicateEventsNeverReachProjection() {
        var created = EventEnvelope.wrap(parcel(id),"parcel-service",null);
        var status = EventEnvelope.wrap(status(id,lk.swiftdrop.events.common.Types.ParcelStatus.DELIVERED,now),"parcel-service",null);
        when(idempotency.firstDelivery(created.eventId(),"dispatch-created")).thenReturn(true,false);
        when(idempotency.firstDelivery(status.eventId(),"dispatch-status")).thenReturn(true,false);
        service.created(created); service.created(created); service.status(status); service.status(status);
        verify(store,times(1)).created(created.data()); verify(store,times(1)).status(status.data());
    }
}
