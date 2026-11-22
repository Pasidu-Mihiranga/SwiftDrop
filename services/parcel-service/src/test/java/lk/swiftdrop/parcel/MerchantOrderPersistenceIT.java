package lk.swiftdrop.parcel;

import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.events.common.Types.ServiceLevel;
import lk.swiftdrop.events.common.Types.SizeClass;
import lk.swiftdrop.parcel.application.command.MerchantOrderCommand;
import lk.swiftdrop.parcel.application.command.MerchantOrderService;
import lk.swiftdrop.parcel.application.exception.MerchantAccessDeniedException;
import lk.swiftdrop.parcel.application.exception.OrderNotPriceableException;
import lk.swiftdrop.parcel.application.exception.PricingUnavailableException;
import lk.swiftdrop.parcel.application.model.MerchantOrderFilter;
import lk.swiftdrop.parcel.application.model.MerchantOrderResult;
import lk.swiftdrop.parcel.application.model.PagedResult;
import lk.swiftdrop.parcel.application.port.out.DuplicateIdempotencyKeyException;
import lk.swiftdrop.parcel.application.port.out.IdempotencyStore;
import lk.swiftdrop.parcel.application.port.out.MerchantStatusCache;
import lk.swiftdrop.parcel.application.port.out.ParcelRepository;
import lk.swiftdrop.parcel.domain.exception.IdempotencyKeyReusedException;
import lk.swiftdrop.parcel.domain.model.IdempotencyKey;
import lk.swiftdrop.parcel.domain.model.IdempotencyRecord;
import lk.swiftdrop.parcel.domain.model.MerchantStanding;
import lk.swiftdrop.parcel.domain.model.MerchantStatus;
import lk.swiftdrop.parcel.domain.model.Parcel;
import lk.swiftdrop.parcel.domain.model.TrackingNumberFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * The merchant-order adapters against real PostgreSQL, and the service wired with them: the status
 * cache keeps the newest change whatever order events arrive in, the idempotency table's primary
 * key stops a second parcel, and a failed order leaves nothing behind.
 */
@Testcontainers
@SpringBootTest(properties = {
        // test-only key (32 bytes, base64); real deployments read PII_ENCRYPTION_KEY
        "swiftdrop.pii.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        // the outbox rows are inspected directly, so the relay must not publish and mark them
        "swiftdrop.outbox.relay-enabled=false"})
class MerchantOrderPersistenceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.15-alpine");
    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.2");

    private static final PricingStub PRICING = new PricingStub();
    private static final String MERCHANT_A = "00000000-0000-4000-8000-0000000000a1";
    private static final String MERCHANT_B = "00000000-0000-4000-8000-0000000000b2";
    private static final Instant T0 = Instant.parse("2026-11-20T08:00:00Z");

    @Autowired
    private MerchantStatusCache cache;
    @Autowired
    private IdempotencyStore idempotency;
    @Autowired
    private ParcelRepository parcels;
    @Autowired
    private MerchantOrderService orders;
    @Autowired
    private TransactionTemplate transaction;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TrackingNumberFactory trackingNumbers;

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
    }

    private static MerchantStanding standing(String merchantId, MerchantStatus status, Instant changedAt) {
        return new MerchantStanding(merchantId, status, "Lanka Books", "lankabooks@example.com", changedAt);
    }

    private static AuthenticatedUser apiClientOf(String merchantId) {
        return ParcelFixtures.user("service-account-merchant-" + merchantId.substring(34), "MERCHANT_API", merchantId);
    }

    private static MerchantOrderCommand order(String ref) {
        return new MerchantOrderCommand(ref, ParcelFixtures.PICKUP, ParcelFixtures.DROPOFF, ParcelFixtures.RECIPIENT,
                new BigDecimal("2"), SizeClass.S, ServiceLevel.STANDARD, null);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private Parcel storedParcelOf(String merchantId) {
        return parcels.save(Parcel.bookForMerchant(trackingNumbers.issue(parcels::existsByTrackingNumber),
                merchantId, "ORD-" + UUID.randomUUID(), "sa", ParcelFixtures.details(), Instant.now()));
    }

    // --- merchant status cache

    @Test
    void anUnknownMerchantHasNoStanding() {
        assertThat(cache.find(MERCHANT_A)).isEmpty();
    }

    @Test
    void theFirstStandingIsStoredWithItsSenderDetails() {
        assertThat(cache.apply(standing(MERCHANT_A, MerchantStatus.APPROVED, T0))).isTrue();

        assertThat(cache.find(MERCHANT_A)).hasValueSatisfying(found -> {
            assertThat(found.status()).isEqualTo(MerchantStatus.APPROVED);
            assertThat(found.businessName()).isEqualTo("Lanka Books");
            assertThat(found.contactEmail()).isEqualTo("lankabooks@example.com");
            assertThat(found.changedAt()).isEqualTo(T0);
        });
    }

    @Test
    void aNewerChangeReplacesTheStandingAndItsDetails() {
        cache.apply(standing(MERCHANT_A, MerchantStatus.APPROVED, T0));

        boolean changed = cache.apply(new MerchantStanding(MERCHANT_A, MerchantStatus.SUSPENDED, "Lanka Books Ltd",
                null, T0.plusSeconds(60)));

        assertThat(changed).isTrue();
        assertThat(cache.find(MERCHANT_A)).hasValueSatisfying(found -> {
            assertThat(found.status()).isEqualTo(MerchantStatus.SUSPENDED);
            assertThat(found.businessName()).isEqualTo("Lanka Books Ltd");
            assertThat(found.contactEmail()).isNull();
        });
    }

    @Test
    void anOlderChangeArrivingLateIsIgnored() {
        cache.apply(standing(MERCHANT_A, MerchantStatus.SUSPENDED, T0.plusSeconds(60)));
        Timestamp updatedBefore = jdbc.queryForObject("SELECT updated_at FROM merchant_status", Timestamp.class);

        boolean changed = cache.apply(standing(MERCHANT_A, MerchantStatus.APPROVED, T0));

        assertThat(changed).isFalse();
        assertThat(cache.find(MERCHANT_A).orElseThrow().status()).isEqualTo(MerchantStatus.SUSPENDED);
        assertThat(jdbc.queryForObject("SELECT updated_at FROM merchant_status", Timestamp.class))
                .as("an ignored event does not touch the row").isEqualTo(updatedBefore);
    }

    @Test
    void theSameChangeDeliveredTwiceIsStoredOnce() {
        assertThat(cache.apply(standing(MERCHANT_A, MerchantStatus.APPROVED, T0))).isTrue();
        assertThat(cache.apply(standing(MERCHANT_A, MerchantStatus.APPROVED, T0))).isFalse();

        assertThat(count("merchant_status")).isEqualTo(1);
    }

    @Test
    void concurrentChangesInAnyOrderEndWithTheNewest() throws Exception {
        List<MerchantStanding> changes = new ArrayList<>();
        MerchantStatus[] statuses = MerchantStatus.values();
        for (int i = 0; i < 12; i++) {
            changes.add(standing(MERCHANT_A, statuses[i % statuses.length], T0.plusSeconds(i)));
        }
        Collections.shuffle(changes, new java.util.Random(42));

        runTogether(changes.stream().map(change -> (Callable<Boolean>) () -> cache.apply(change)).toList());

        MerchantStanding newest = standing(MERCHANT_A, statuses[11 % statuses.length], T0.plusSeconds(11));
        assertThat(cache.find(MERCHANT_A)).contains(newest);
    }

    @Test
    void aSuspensionWaitsForAnOrderTransactionThatHoldsTheStatusLock() throws Exception {
        cache.apply(standing(MERCHANT_A, MerchantStatus.APPROVED, T0));
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> order = pool.submit(() -> transaction.executeWithoutResult(status -> {
                assertThat(cache.findForOrder(MERCHANT_A).orElseThrow().isApproved()).isTrue();
                locked.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            locked.await();
            Future<Boolean> suspension = pool.submit(() ->
                    cache.apply(standing(MERCHANT_A, MerchantStatus.SUSPENDED, T0.plusSeconds(60))));

            Thread.sleep(500);
            assertThat(suspension.isDone()).as("the suspension waits for the order to commit").isFalse();

            release.countDown();
            order.get();
            assertThat(suspension.get()).isTrue();
            assertThat(cache.find(MERCHANT_A).orElseThrow().status()).isEqualTo(MerchantStatus.SUSPENDED);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void theLockedReadOnlyRunsInsideAnOrderTransaction() {
        assertThatThrownBy(() -> cache.findForOrder(MERCHANT_A)).isInstanceOf(IllegalTransactionStateException.class);
    }

    // --- idempotency store

    @Test
    void aRecordIsStoredInTheOrderTransactionAndReadBack() {
        IdempotencyKey key = new IdempotencyKey("idem-key-key-1");
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        UUID parcelId = transaction.execute(status -> {
            Parcel parcel = storedParcelOf(MERCHANT_A);
            idempotency.save(new IdempotencyRecord(MERCHANT_A, key, parcel.id(), "a".repeat(64), createdAt));
            return parcel.id();
        });

        assertThat(idempotency.find(MERCHANT_A, key)).hasValueSatisfying(found -> {
            assertThat(found.parcelId()).isEqualTo(parcelId);
            assertThat(found.requestHash()).isEqualTo("a".repeat(64));
            assertThat(found.createdAt()).isEqualTo(createdAt);
        });
        assertThat(idempotency.find(MERCHANT_B, key)).as("keys belong to one merchant").isEmpty();
    }

    @Test
    void aRecordCanOnlyBeSavedInsideATransaction() {
        assertThatThrownBy(() -> idempotency.save(new IdempotencyRecord(MERCHANT_A, new IdempotencyKey("idem-key-k"),
                UUID.randomUUID(), "a".repeat(64), Instant.now()))).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void aSecondRecordForTheSameKeyIsADuplicateAndRollsBackItsWholeTransaction() {
        IdempotencyKey key = new IdempotencyKey("idem-key-key-dup");
        transaction.executeWithoutResult(status -> idempotency.save(new IdempotencyRecord(MERCHANT_A, key,
                storedParcelOf(MERCHANT_A).id(), "a".repeat(64), Instant.now())));

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            Parcel second = storedParcelOf(MERCHANT_A);
            idempotency.save(new IdempotencyRecord(MERCHANT_A, key, second.id(), "b".repeat(64), Instant.now()));
        })).isInstanceOf(DuplicateIdempotencyKeyException.class);

        assertThat(count("parcel")).as("the second parcel was rolled back with its record").isEqualTo(1);
        assertThat(idempotency.find(MERCHANT_A, key).orElseThrow().requestHash())
                .as("the first record is not overwritten").isEqualTo("a".repeat(64));
    }

    @Test
    void twoMerchantsMayUseTheSameKey() {
        IdempotencyKey key = new IdempotencyKey("idem-key-shared-key");
        transaction.executeWithoutResult(status -> {
            idempotency.save(new IdempotencyRecord(MERCHANT_A, key, storedParcelOf(MERCHANT_A).id(), "a".repeat(64),
                    Instant.now()));
            idempotency.save(new IdempotencyRecord(MERCHANT_B, key, storedParcelOf(MERCHANT_B).id(), "b".repeat(64),
                    Instant.now()));
        });

        assertThat(count("idempotency_record")).isEqualTo(2);
    }

    @Test
    void aRecordForAParcelThatDoesNotExistIsAnErrorNotADuplicate() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> idempotency.save(new IdempotencyRecord(
                MERCHANT_A, new IdempotencyKey("idem-key-k"), UUID.randomUUID(), "a".repeat(64), Instant.now()))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(DuplicateIdempotencyKeyException.class);
    }

    // --- the service, wired with the real adapters

    @Test
    void anApprovedMerchantsOrderCommitsParcelRecordAndEventTogether() {
        cache.apply(standing(MERCHANT_A, MerchantStatus.APPROVED, T0));

        MerchantOrderResult result = orders.create(apiClientOf(MERCHANT_A), new IdempotencyKey("idem-key-k-1"), order("ORD-1"));

        assertThat(result.replayed()).isFalse();
        assertThat(count("parcel")).isEqualTo(1);
        assertThat(count("idempotency_record")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE event_type = 'ParcelCreated' "
                + "AND aggregate_id = ?", Integer.class, result.parcel().id().toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT payload->'data'->'contacts'->>'senderName' FROM outbox_event", String.class))
                .isEqualTo("Lanka Books");
        assertThat(jdbc.queryForObject("SELECT merchant_id FROM parcel", String.class)).isEqualTo(MERCHANT_A);
    }

    @Test
    void repeatingTheRequestReplaysTheSameParcelAndChangingItIsAConflict() {
        cache.apply(standing(MERCHANT_A, MerchantStatus.APPROVED, T0));
        IdempotencyKey key = new IdempotencyKey("idem-key-k-replay");
        Parcel first = orders.create(apiClientOf(MERCHANT_A), key, order("ORD-1")).parcel();

        MerchantOrderResult again = orders.create(apiClientOf(MERCHANT_A), key, order("ORD-1"));

        assertThat(again.replayed()).isTrue();
        assertThat(again.parcel().id()).isEqualTo(first.id());
        assertThat(again.parcel().trackingNumber()).isEqualTo(first.trackingNumber());
        assertThatThrownBy(() -> orders.create(apiClientOf(MERCHANT_A), key, order("ORD-2")))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertThat(count("parcel")).isEqualTo(1);
        assertThat(count("outbox_event")).isEqualTo(1);
        assertThat(PRICING.calls()).as("only the first request was priced").hasSize(1);
    }

    @Test
    void concurrentRequestsWithOneKeyCreateExactlyOneParcel() throws Exception {
        cache.apply(standing(MERCHANT_A, MerchantStatus.APPROVED, T0));
        // every request passes the "no record yet" check before any of them commits
        PRICING.slowNewQuotes(Duration.ofMillis(400));
        IdempotencyKey key = new IdempotencyKey("idem-key-k-race");

        List<MerchantOrderResult> results = runTogether(IntStream.range(0, 6)
                .mapToObj(i -> (Callable<MerchantOrderResult>) () ->
                        orders.create(apiClientOf(MERCHANT_A), key, order("ORD-RACE")))
                .toList());

        assertThat(count("parcel")).isEqualTo(1);
        assertThat(count("idempotency_record")).isEqualTo(1);
        assertThat(count("outbox_event")).isEqualTo(1);
        assertThat(results.stream().map(r -> r.parcel().id()).distinct()).hasSize(1);
        assertThat(results.stream().filter(r -> !r.replayed())).as("exactly one request created it").hasSize(1);
        assertThat(PRICING.calls().size()).as("the requests really overlapped").isGreaterThan(1);
    }

    @Test
    void aMerchantSuspendedWhileItsOrderIsBeingPricedGetsNoOrder() throws Exception {
        cache.apply(standing(MERCHANT_A, MerchantStatus.APPROVED, T0));
        PRICING.slowNewQuotes(Duration.ofSeconds(1));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<MerchantOrderResult> inFlight = pool.submit(() ->
                    orders.create(apiClientOf(MERCHANT_A), new IdempotencyKey("idem-key-suspend"), order("ORD-1")));
            // the approval check has passed and pricing-service is working on the quote
            await().atMost(Duration.ofSeconds(5)).until(() -> PRICING.calls().size() == 1);

            cache.apply(standing(MERCHANT_A, MerchantStatus.SUSPENDED, T0.plusSeconds(60)));

            assertThatThrownBy(inFlight::get).isInstanceOf(ExecutionException.class)
                    .cause().isInstanceOfSatisfying(MerchantAccessDeniedException.class,
                            e -> assertThat(e.code()).isEqualTo(MerchantAccessDeniedException.NOT_APPROVED));
        } finally {
            pool.shutdownNow();
        }
        assertThat(count("parcel") + count("idempotency_record") + count("outbox_event")).isZero();
    }

    @Test
    void whenPricingIsUnavailableNothingIsStored() {
        cache.apply(standing(MERCHANT_A, MerchantStatus.APPROVED, T0));
        PRICING.answerNewQuotes(503, "{}");

        assertThatThrownBy(() -> orders.create(apiClientOf(MERCHANT_A), new IdempotencyKey("idem-key-k-down"), order("ORD-1")))
                .isInstanceOf(PricingUnavailableException.class);

        assertThat(count("parcel") + count("idempotency_record") + count("outbox_event")).isZero();
    }

    @Test
    void whenPricingRefusesTheOrderNothingIsStoredAndTheKeyCanBeReused() {
        cache.apply(standing(MERCHANT_A, MerchantStatus.APPROVED, T0));
        PRICING.answerNewQuotes(422, "{\"code\":\"DISTRICT_NOT_SERVICED\",\"detail\":\"Not serviced\"}");
        IdempotencyKey key = new IdempotencyKey("idem-key-k-fix");

        assertThatThrownBy(() -> orders.create(apiClientOf(MERCHANT_A), key, order("ORD-1")))
                .isInstanceOf(OrderNotPriceableException.class);
        assertThat(count("parcel") + count("idempotency_record") + count("outbox_event")).isZero();

        PRICING.reset();
        assertThat(orders.create(apiClientOf(MERCHANT_A), key, order("ORD-1")).replayed()).isFalse();
    }

    @Test
    void eachMerchantListsOnlyItsOwnOrdersWithFilters() {
        cache.apply(standing(MERCHANT_A, MerchantStatus.APPROVED, T0));
        cache.apply(standing(MERCHANT_B, MerchantStatus.APPROVED, T0));
        Set<UUID> mine = new java.util.HashSet<>();
        for (int i = 0; i < 3; i++) {
            mine.add(orders.create(apiClientOf(MERCHANT_A), new IdempotencyKey("idem-key-a-" + i), order("A-" + i)).parcel().id());
        }
        orders.create(apiClientOf(MERCHANT_B), new IdempotencyKey("idem-key-a-0"), order("B-0"));

        PagedResult<Parcel> firstPage = orders.list(apiClientOf(MERCHANT_A), MerchantOrderFilter.none(), 0, 2);
        PagedResult<Parcel> secondPage = orders.list(apiClientOf(MERCHANT_A), MerchantOrderFilter.none(), 1, 2);

        assertThat(firstPage.totalElements()).isEqualTo(3);
        List<UUID> listed = new ArrayList<>();
        firstPage.content().forEach(p -> listed.add(p.id()));
        secondPage.content().forEach(p -> listed.add(p.id()));
        assertThat(listed).containsExactlyInAnyOrderElementsOf(mine).doesNotHaveDuplicates();
        assertThat(firstPage.content().get(0).createdAt()).isAfterOrEqualTo(firstPage.content().get(1).createdAt());

        assertThat(orders.list(apiClientOf(MERCHANT_A),
                new MerchantOrderFilter(ParcelStatus.DELIVERED, null, null), 0, 20).totalElements()).isZero();
        assertThat(orders.list(apiClientOf(MERCHANT_A),
                new MerchantOrderFilter(ParcelStatus.CREATED, Instant.now().minusSeconds(3600), Instant.now()
                        .plusSeconds(60)), 0, 20).totalElements()).isEqualTo(3);
        assertThat(orders.list(apiClientOf(MERCHANT_A),
                new MerchantOrderFilter(null, Instant.now().plusSeconds(60), null), 0, 20).totalElements()).isZero();
        assertThat(orders.list(apiClientOf(MERCHANT_B), MerchantOrderFilter.none(), 0, 20).content())
                .singleElement().satisfies(p -> assertThat(p.merchantOrderRef()).isEqualTo("B-0"));
    }

    @Test
    void anOrderRefSearchFindsPartOfTheReferenceIgnoringCaseWithinTheMerchantsOwnOrdersOnly() {
        cache.apply(standing(MERCHANT_A, MerchantStatus.APPROVED, T0));
        cache.apply(standing(MERCHANT_B, MerchantStatus.APPROVED, T0));
        String[] refs = {"WEB-1001", "web-1002", "POS-2001", "SALE_50%", "SALEX50Y"};
        for (int i = 0; i < refs.length; i++) {
            orders.create(apiClientOf(MERCHANT_A), new IdempotencyKey("idem-key-s-" + i), order(refs[i]));
        }
        // Merchant B uses a reference that merchant A's search would match.
        orders.create(apiClientOf(MERCHANT_B), new IdempotencyKey("idem-key-s-b"), order("WEB-1001"));

        assertThat(refsFound(MERCHANT_A, "web-100", null, 0, 20)).containsExactlyInAnyOrder("WEB-1001", "web-1002");
        assertThat(refsFound(MERCHANT_A, "1001", null, 0, 20)).containsExactly("WEB-1001");
        assertThat(refsFound(MERCHANT_A, "pos", null, 0, 20)).containsExactly("POS-2001");
        assertThat(refsFound(MERCHANT_A, "NOPE", null, 0, 20)).isEmpty();

        // "%" and "_" are ordinary characters, not LIKE wildcards: "SALEX50Y" must not match "SALE_50%".
        assertThat(refsFound(MERCHANT_A, "SALE_50%", null, 0, 20)).containsExactly("SALE_50%");
        assertThat(refsFound(MERCHANT_A, "%", null, 0, 20)).containsExactly("SALE_50%");
        assertThat(refsFound(MERCHANT_A, "_", null, 0, 20)).containsExactly("SALE_50%");

        // Combined with the other filters, and paged over the matching orders only.
        assertThat(refsFound(MERCHANT_A, "web", ParcelStatus.CREATED, 0, 20)).hasSize(2);
        assertThat(refsFound(MERCHANT_A, "web", ParcelStatus.DELIVERED, 0, 20)).isEmpty();
        PagedResult<Parcel> firstPage = orders.list(apiClientOf(MERCHANT_A),
                new MerchantOrderFilter(null, T0.minusSeconds(86_400 * 365L), null, "web"), 0, 1);
        assertThat(firstPage.totalElements()).isEqualTo(2);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(refsFound(MERCHANT_A, "web", null, 1, 1)).hasSize(1);

        // Merchant B's search never reaches merchant A's orders.
        assertThat(refsFound(MERCHANT_B, "web", null, 0, 20)).containsExactly("WEB-1001");
        assertThat(refsFound(MERCHANT_B, "POS", null, 0, 20)).isEmpty();
    }

    private List<String> refsFound(String merchantId, String orderRef, ParcelStatus status, int page, int size) {
        return orders.list(apiClientOf(merchantId), new MerchantOrderFilter(status, null, null, orderRef), page, size)
                .content().stream().map(Parcel::merchantOrderRef).toList();
    }

    /** Starts every task at the same moment and waits for all of them. */
    private static <T> List<T> runTogether(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
