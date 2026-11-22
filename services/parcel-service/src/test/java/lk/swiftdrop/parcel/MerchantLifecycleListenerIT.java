package lk.swiftdrop.parcel;

import lk.swiftdrop.events.EventEnvelope;
import lk.swiftdrop.events.Topics;
import lk.swiftdrop.events.common.Types.Actor;
import lk.swiftdrop.events.common.Types.ActorRole;
import lk.swiftdrop.events.common.Types.ServiceLevel;
import lk.swiftdrop.events.common.Types.SizeClass;
import lk.swiftdrop.events.merchant.MerchantLifecycleChanged;
import lk.swiftdrop.events.merchant.MerchantLifecycleChanged.MerchantStatus;
import lk.swiftdrop.parcel.application.command.MerchantOrderCommand;
import lk.swiftdrop.parcel.application.command.MerchantOrderService;
import lk.swiftdrop.parcel.application.exception.MerchantAccessDeniedException;
import lk.swiftdrop.parcel.application.port.out.MerchantStatusCache;
import lk.swiftdrop.parcel.domain.model.IdempotencyKey;
import lk.swiftdrop.parcel.infrastructure.messaging.MerchantLifecycleListener;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

/**
 * The lifecycle listener against real Kafka and PostgreSQL, fed records exactly as merchant-service's
 * outbox relay sends them: the envelope as a JSON string, keyed by merchant id, with the eventId,
 * eventType and correlationId headers.
 */
@Testcontainers
@SpringBootTest(properties = {
        // test-only key (32 bytes, base64); real deployments read PII_ENCRYPTION_KEY
        "swiftdrop.pii.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "swiftdrop.outbox.relay-enabled=false",
        // retry quickly in tests; production waits 1s, doubling up to 30s
        "swiftdrop.kafka.retry.initial-interval=100ms",
        "swiftdrop.kafka.retry.max-interval=400ms"})
@ExtendWith(OutputCaptureExtension.class)
class MerchantLifecycleListenerIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.15-alpine");
    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.2");

    private static final PricingStub PRICING = new PricingStub();
    private static final Instant T1 = Instant.parse("2026-11-20T08:00:00Z");
    private static final Instant T2 = T1.plusSeconds(3600);
    private static final Duration WAIT = Duration.ofSeconds(30);

    @Autowired
    private KafkaTemplate<String, String> kafka;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MerchantOrderService orders;
    @MockitoSpyBean
    private MerchantStatusCache cache;
    @Autowired
    private KafkaListenerEndpointRegistry listeners;

    @DynamicPropertySource
    static void pricing(DynamicPropertyRegistry registry) {
        registry.add("swiftdrop.pricing.base-url", PRICING::baseUrl);
    }

    @AfterAll
    static void stopPricing() {
        PRICING.close();
    }

    @BeforeEach
    void cleanUp() {
        PRICING.reset();
        jdbc.update("DELETE FROM outbox_event");
        jdbc.update("DELETE FROM idempotency_record");
        jdbc.update("DELETE FROM parcel");
        jdbc.update("DELETE FROM merchant_status");
        jdbc.update("DELETE FROM processed_event");
    }

    private static EventEnvelope<MerchantLifecycleChanged> event(UUID merchantId, MerchantStatus status,
                                                                 Instant changedAt, String businessName,
                                                                 String email) {
        MerchantLifecycleChanged payload = new MerchantLifecycleChanged(merchantId, businessName, "owner-1", email,
                null, status, status == MerchantStatus.REJECTED ? "BR certificate unreadable" : null,
                new Actor("admin-1", ActorRole.ADMIN), changedAt);
        return EventEnvelope.wrap(payload, "merchant-service", "corr-" + UUID.randomUUID());
    }

    private static EventEnvelope<MerchantLifecycleChanged> event(UUID merchantId, MerchantStatus status,
                                                                 Instant changedAt) {
        return event(merchantId, status, changedAt, "Lanka Books", "lankabooks@example.com");
    }

    /** As OutboxRelay sends it. */
    private void publish(EventEnvelope<MerchantLifecycleChanged> envelope) throws Exception {
        sendRaw(envelope.data().merchantId().toString(), json.writeValueAsString(envelope), envelope.eventId());
    }

    private void sendRaw(String key, String value, UUID eventId) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(Topics.MERCHANT_LIFECYCLE, key, value);
        record.headers().add(Topics.HEADER_EVENT_ID, eventId.toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add(Topics.HEADER_EVENT_TYPE,
                MerchantLifecycleChanged.TYPE.getBytes(StandardCharsets.UTF_8));
        record.headers().add(Topics.HEADER_CORRELATION_ID, "corr-it".getBytes(StandardCharsets.UTF_8));
        kafka.send(record).get();
    }

    private boolean processed(UUID eventId) {
        return jdbc.queryForObject("SELECT count(*) FROM processed_event WHERE event_id = ? AND consumer = ?",
                Integer.class, eventId, "parcel-merchant-status") == 1;
    }

    private Map<String, Object> row(UUID merchantId) {
        return jdbc.queryForMap("SELECT status, business_name, contact_email, changed_at, updated_at "
                + "FROM merchant_status WHERE merchant_id = ?", merchantId.toString());
    }

    private void awaitProcessed(UUID eventId) {
        await().atMost(WAIT).until(() -> processed(eventId));
    }

    @Test
    void anApprovalCreatesTheStatusWithTheSenderDetailsAndLaterChangesUpdateIt() throws Exception {
        UUID merchant = UUID.randomUUID();
        var approved = event(merchant, MerchantStatus.APPROVED, T1, "Lanka Books (Pvt) Ltd", "orders@lankabooks.lk");

        publish(approved);
        awaitProcessed(approved.eventId());

        Map<String, Object> stored = row(merchant);
        assertThat(stored.get("status")).isEqualTo("APPROVED");
        assertThat(stored.get("business_name")).isEqualTo("Lanka Books (Pvt) Ltd");
        assertThat(stored.get("contact_email")).isEqualTo("orders@lankabooks.lk");
        assertThat(((Timestamp) stored.get("changed_at")).toInstant()).isEqualTo(T1);

        var suspended = event(merchant, MerchantStatus.SUSPENDED, T2, "Lanka Books Ltd", null);
        publish(suspended);
        awaitProcessed(suspended.eventId());

        assertThat(row(merchant).get("status")).isEqualTo("SUSPENDED");
        assertThat(row(merchant).get("business_name")).isEqualTo("Lanka Books Ltd");
        assertThat(row(merchant).get("contact_email")).isNull();
    }

    @Test
    void pendingAndRejectedMerchantsAreRecordedAsSuch() throws Exception {
        UUID pending = UUID.randomUUID();
        UUID rejected = UUID.randomUUID();
        var applied = event(pending, MerchantStatus.PENDING, T1);
        var turnedDown = event(rejected, MerchantStatus.REJECTED, T1);

        publish(applied);
        publish(turnedDown);
        awaitProcessed(applied.eventId());
        awaitProcessed(turnedDown.eventId());

        assertThat(row(pending).get("status")).isEqualTo("PENDING");
        assertThat(row(rejected).get("status")).isEqualTo("REJECTED");
    }

    @Test
    void aDuplicateDeliveryIsProcessedOnce() throws Exception {
        UUID merchant = UUID.randomUUID();
        var approved = event(merchant, MerchantStatus.APPROVED, T1);
        publish(approved);
        awaitProcessed(approved.eventId());
        Object updatedAt = row(merchant).get("updated_at");

        publish(approved);
        // same key, so same partition: once the sentinel is processed, the duplicate before it was too
        var sentinel = event(merchant, MerchantStatus.PENDING, T1.minusSeconds(60));
        publish(sentinel);
        awaitProcessed(sentinel.eventId());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_event WHERE event_id = ?", Integer.class,
                approved.eventId())).isEqualTo(1);
        assertThat(row(merchant).get("updated_at")).as("the duplicate changed nothing").isEqualTo(updatedAt);
        verify(cache, org.mockito.Mockito.times(1))
                .apply(argThat(s -> s.merchantId().equals(merchant.toString()) && s.changedAt().equals(T1)));
    }

    @Test
    void aLateApprovalCannotReopenASuspendedMerchant() throws Exception {
        UUID merchant = UUID.randomUUID();
        var suspended = event(merchant, MerchantStatus.SUSPENDED, T2);
        var lateApproval = event(merchant, MerchantStatus.APPROVED, T1);

        publish(suspended);
        publish(lateApproval);
        awaitProcessed(suspended.eventId());
        awaitProcessed(lateApproval.eventId());

        assertThat(row(merchant).get("status")).isEqualTo("SUSPENDED");
        assertThat(((Timestamp) row(merchant).get("changed_at")).toInstant()).isEqualTo(T2);
    }

    @Test
    void aMalformedMessageIsSkippedWithoutBlockingTheNextOne() throws Exception {
        UUID merchant = UUID.randomUUID();
        UUID garbageId = UUID.randomUUID();
        sendRaw(merchant.toString(), "{not json", garbageId);
        sendRaw(merchant.toString(), json.writeValueAsString(event(merchant, MerchantStatus.APPROVED, T1))
                .replace("\"businessName\":\"Lanka Books\"", "\"businessName\":null"), UUID.randomUUID());
        var valid = event(merchant, MerchantStatus.APPROVED, T1);

        publish(valid);
        awaitProcessed(valid.eventId());

        assertThat(row(merchant).get("status")).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_event", Integer.class))
                .as("only the valid event is marked processed").isEqualTo(1);
    }

    @Test
    void aFailedUpdateIsNotMarkedConsumedAndIsRetriedUntilItSucceeds(CapturedOutput output) throws Exception {
        UUID merchant = UUID.randomUUID();
        AtomicInteger failuresLeft = new AtomicInteger(2);
        var approved = event(merchant, MerchantStatus.APPROVED, T1);
        doAnswer(call -> {
            if (failuresLeft.getAndDecrement() > 0) {
                throw new DataAccessResourceFailureException("database briefly unavailable");
            }
            return call.callRealMethod();
        }).when(cache).apply(argThat(s -> s != null && s.merchantId().equals(merchant.toString())));

        publish(approved);
        awaitProcessed(approved.eventId());

        assertThat(row(merchant).get("status")).isEqualTo("APPROVED");
        // Had a failed attempt kept its processed_event marker, the redelivery would have stopped at
        // the duplicate check and never reached the cache again: three calls prove both rolled back.
        verify(cache, atLeast(3)).apply(argThat(s -> s != null && s.merchantId().equals(merchant.toString())));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_event WHERE event_id = ?", Integer.class,
                approved.eventId())).isEqualTo(1);
        assertThat(output.getAll())
                .contains("failed (attempt 1), retrying: DataAccessResourceFailureException")
                .contains("failed (attempt 2), retrying: DataAccessResourceFailureException")
                .as("neither the record nor the failure's message is logged")
                .doesNotContain("lankabooks@example.com").doesNotContain("database briefly unavailable");
    }

    @Test
    void anApprovalFromKafkaOpensTheOrderApiAndASuspensionClosesIt() throws Exception {
        UUID merchant = UUID.randomUUID();
        var caller = ParcelFixtures.user("service-account-merchant", "MERCHANT_API", merchant.toString());
        MerchantOrderCommand order = new MerchantOrderCommand("ORD-1", ParcelFixtures.PICKUP, ParcelFixtures.DROPOFF,
                ParcelFixtures.RECIPIENT, new BigDecimal("2"), SizeClass.S, ServiceLevel.STANDARD, null);
        assertThatThrownBy(() -> orders.create(caller, new IdempotencyKey("idem-key-k-0"), order))
                .as("unknown merchants fail closed").isInstanceOf(MerchantAccessDeniedException.class);

        var approved = event(merchant, MerchantStatus.APPROVED, T1);
        publish(approved);
        awaitProcessed(approved.eventId());
        assertThat(orders.create(caller, new IdempotencyKey("idem-key-k-1"), order).parcel().merchantId())
                .isEqualTo(merchant.toString());

        var suspended = event(merchant, MerchantStatus.SUSPENDED, T2);
        publish(suspended);
        awaitProcessed(suspended.eventId());
        assertThatThrownBy(() -> orders.create(caller, new IdempotencyKey("idem-key-k-2"), order))
                .isInstanceOf(MerchantAccessDeniedException.class);
    }

    @Test
    void theListenerConsumesAsTheParcelServiceGroup() {
        assertThat(listeners.getListenerContainer(MerchantLifecycleListener.LISTENER_ID).getGroupId())
                .isEqualTo("parcel-service");
    }
}
