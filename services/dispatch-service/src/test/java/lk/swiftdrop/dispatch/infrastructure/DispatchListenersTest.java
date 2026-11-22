package lk.swiftdrop.dispatch.infrastructure;
import lk.swiftdrop.dispatch.application.DispatchService;
import lk.swiftdrop.events.*;
import lk.swiftdrop.events.parcel.*;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.UUID;
import static lk.swiftdrop.dispatch.DispatchFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DispatchListenersTest {
    @Test void readsSupportedEventsAndRejectsMalformedEnvelopes() {
        var mapper = JsonMapper.builder().build(); var service = mock(DispatchService.class);
        var listener = new DispatchListeners(mapper,service);
        var created = EventEnvelope.wrap(parcel(UUID.randomUUID()),"parcel-service",null);
        var changed = EventEnvelope.wrap(status(created.data().parcelId(),ParcelStatus.DELIVERED,Instant.now()),"parcel-service",null);
        listener.created(mapper.writeValueAsString(created)); listener.status(mapper.writeValueAsString(changed));
        verify(service).created(created); verify(service).status(changed);
        assertThrows(RuntimeException.class,() -> listener.created("not json"));
        for (String json : java.util.List.of("null", "{}",mapper.writeValueAsString(new EventEnvelope<>(null,"ParcelCreated",1,Instant.now(),null,"p",created.data())),
            mapper.writeValueAsString(new EventEnvelope<>(UUID.randomUUID(),"ParcelCreated",2,Instant.now(),null,"p",created.data())),
            mapper.writeValueAsString(new EventEnvelope<>(UUID.randomUUID(),"WrongType",1,Instant.now(),null,"p",created.data()))))
            assertThrows(RuntimeException.class,() -> listener.created(json));
    }
}
