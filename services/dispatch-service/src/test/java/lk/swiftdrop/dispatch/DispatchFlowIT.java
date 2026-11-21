package lk.swiftdrop.dispatch;

import lk.swiftdrop.dispatch.application.*;
import lk.swiftdrop.events.*;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.dispatch.infrastructure.DispatchListeners;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static lk.swiftdrop.dispatch.DispatchFixtures.*;
import static lk.swiftdrop.dispatch.application.DispatchModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class DispatchFlowIT {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.15-alpine");
    @Container @ServiceConnection static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.2");
    @Autowired DispatchService service;
    @Autowired DispatchStore store;
    @Autowired DispatchListeners listeners;
    @Autowired KafkaTemplate<String,String> kafka;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcClient db;
    @Autowired MockMvc mvc;
    @Autowired TransactionTemplate tx;
    @MockitoBean CourierDirectory directory;
    @BeforeEach void setup() {
        db.sql("TRUNCATE assignment, parcel_summary, outbox_event, processed_event CASCADE").update();
        when(directory.couriers(anyString())).thenReturn(List.of(new Courier("courier1","Courier One",null,20,true),new Courier("courier2","Courier Two",null,20,true)));
    }
    void book(UUID id) throws Exception {
        var event = EventEnvelope.wrap(parcel(id),"parcel-service",null);
        kafka.send(Topics.PARCEL_CREATED,id.toString(),mapper.writeValueAsString(event)).get(10,TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertEquals(1,service.unassigned(null,0,20).totalElements()));
    }
    @Test void bookingAllocationKafkaReassignmentAndRunSheetIsolation() throws Exception {
        UUID id = UUID.randomUUID(); book(id);
        assertEquals(1, service.unassigned("CP",0,20).totalElements());
        assertEquals(0, service.unassigned("WP",0,20).totalElements());
        var a = service.assign(id,"courier1",null,"dispatcher",false,"token");
        assertEquals(0, service.unassigned(null,0,20).totalElements());
        assertEquals(1, service.workloads("token").getFirst().activeCount());
        assertEquals(1, service.runs(null,"courier1").size());
        assertTrue(service.runs(null,"courier2").isEmpty());
        mvc.perform(get("/api/v1/courier/run-sheet?courierId=courier1").with(jwt().jwt(j -> j.subject("courier2"))
            .authorities(new SimpleGrantedAuthority("ROLE_COURIER")))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        assertThrows(lk.swiftdrop.common.web.error.ConflictException.class,() -> service.assign(id,"courier2",null,"dispatcher",false,"token"));
        try (var consumer = consumer()) {
            consumer.subscribe(List.of(Topics.PARCEL_ASSIGNED));
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                var records = consumer.poll(Duration.ofMillis(500));
                assertTrue(java.util.stream.StreamSupport.stream(records.spliterator(),false)
                    .anyMatch(r -> r.key().equals(id.toString()) && r.value().contains("courier1")));
            });
        }
        service.reassign(a.id(),"courier2","dispatcher",false,"token");
        assertTrue(service.runs(null,"courier1").isEmpty());
        assertEquals(1, service.runs(null,"courier2").size());
        assertEquals(0, service.workloads("token").getFirst().activeCount());
        assertEquals(1, service.workloads("token").get(1).activeCount());
        assertEquals("courier1", db.sql("SELECT previous_courier_id FROM assignment").query(String.class).single());
    }
    @Test void duplicateStatusAndOutOfOrderCreationDoNotDriftWorkload() throws Exception {
        UUID id = UUID.randomUUID(); book(id);
        service.assign(id,"courier1",null,"d",false,"token");
        var at = Instant.now();
        var event = EventEnvelope.wrap(status(id,ParcelStatus.DELIVERED,at),"parcel-service",null);
        String json = mapper.writeValueAsString(event);
        kafka.send(Topics.PARCEL_STATUS_CHANGED,id.toString(),json).get();
        kafka.send(Topics.PARCEL_STATUS_CHANGED,id.toString(),json).get();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertEquals(1,service.workloads("token").getFirst().deliveredToday()));
        service.created(EventEnvelope.wrap(parcel(id),"parcel-service",null));
        service.status(EventEnvelope.wrap(status(id,ParcelStatus.IN_TRANSIT,at.minusSeconds(1)),"parcel-service",null));
        assertEquals(0, service.workloads("token").getFirst().activeCount());
        assertEquals(1, service.workloads("token").getFirst().deliveredToday());
        assertEquals(1, db.sql("SELECT count(*) FROM processed_event WHERE event_id=:id").param("id",event.eventId()).query(Long.class).single());
        UUID early = UUID.randomUUID();
        service.status(EventEnvelope.wrap(status(early,ParcelStatus.CANCELLED,at),"parcel-service",null));
        service.created(EventEnvelope.wrap(parcel(early),"parcel-service",null));
        assertEquals(0,service.unassigned(null,0,20).totalElements());
    }
    @Test void failedTodayAndMidnightBoundariesAndMissingResources() throws Exception {
        UUID id = UUID.randomUUID(); book(id); service.assign(id,"courier1",null,"d",false,"token");
        service.status(EventEnvelope.wrap(status(id,ParcelStatus.FAILED,Instant.now()),"parcel-service",null));
        assertEquals(1,service.workloads("token").getFirst().failedToday());
        assertEquals(0,service.workloads("token").getFirst().activeCount());
        assertEquals(0,store.counts(service.today().plusDays(1),ZoneId.of("Asia/Colombo")).get("courier1").failedToday());
        assertThrows(lk.swiftdrop.common.web.error.ResourceNotFoundException.class,() -> service.assign(UUID.randomUUID(),"courier1",null,"d",false,"token"));
        assertThrows(lk.swiftdrop.common.web.error.ResourceNotFoundException.class,() -> service.reassign(UUID.randomUUID(),"courier1","d",false,"token"));
    }
    @Test void concurrentDispatchersProduceExactlyOneAllocation() throws Exception {
        UUID id = UUID.randomUUID(); book(id);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (String courier : List.of("courier1","courier2")) results.add(executor.submit(() -> {
                start.await();
                try { service.assign(id,courier,null,"d",false,"token"); return true; }
                catch (lk.swiftdrop.common.web.error.ConflictException ex) { return false; }
            }));
            start.countDown(); int successes = 0;
            for(var result : results) if(result.get(15,TimeUnit.SECONDS)) successes++;
            assertEquals(1,successes);
        }
        assertEquals(1,db.sql("SELECT count(*) FROM assignment").query(Long.class).single());
        assertEquals(1,db.sql("SELECT count(*) FROM outbox_event").query(Long.class).single());
    }
    @Test void projectionAndIdempotencyMarkerRollBackTogether() {
        UUID id = UUID.randomUUID(); var event = EventEnvelope.wrap(parcel(id),"parcel-service",null);
        assertThrows(IllegalStateException.class,() -> tx.executeWithoutResult(status -> {
            service.created(event); throw new IllegalStateException("rollback");
        }));
        assertEquals(0,service.unassigned(null,0,20).totalElements());
        service.created(event); service.created(event);
        assertEquals(1,service.unassigned(null,0,20).totalElements());
    }
    private KafkaConsumer<String,String> consumer() {
        Map<String,Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG,"dispatch-test-"+UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,"earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,StringDeserializer.class);
        return new KafkaConsumer<>(props);
    }
}
