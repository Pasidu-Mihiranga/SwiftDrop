package lk.swiftdrop.parcel.application.command;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lk.swiftdrop.parcel.infrastructure.metrics.MicrometerParcelMetrics;
import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.common.web.error.BadRequestException;
import lk.swiftdrop.common.web.error.ConflictException;
import lk.swiftdrop.events.common.Types.ActorRole;
import lk.swiftdrop.events.common.Types.ParcelSource;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.events.parcel.ParcelCreated;
import lk.swiftdrop.outbox.OutboxWriter;
import lk.swiftdrop.parcel.ParcelFixtures;
import lk.swiftdrop.parcel.application.exception.MerchantAccessDeniedException;
import lk.swiftdrop.parcel.application.exception.OrderNotPriceableException;
import lk.swiftdrop.parcel.application.exception.PricingUnavailableException;
import lk.swiftdrop.parcel.application.model.MerchantOrderFilter;
import lk.swiftdrop.parcel.application.model.MerchantOrderResult;
import lk.swiftdrop.parcel.application.model.MerchantQuoteRequest;
import lk.swiftdrop.parcel.application.model.PagedResult;
import lk.swiftdrop.parcel.application.port.out.DuplicateIdempotencyKeyException;
import lk.swiftdrop.parcel.application.port.out.IdempotencyStore;
import lk.swiftdrop.parcel.application.port.out.MerchantOrderMetrics;
import lk.swiftdrop.parcel.application.port.out.MerchantOrderMetrics.Outcome;
import lk.swiftdrop.parcel.application.port.out.MerchantStatusCache;
import lk.swiftdrop.parcel.application.port.out.ParcelRepository;
import lk.swiftdrop.parcel.application.port.out.QuotePort;
import lk.swiftdrop.parcel.domain.exception.IdempotencyKeyReusedException;
import lk.swiftdrop.parcel.domain.model.IdempotencyKey;
import lk.swiftdrop.parcel.domain.model.IdempotencyRecord;
import lk.swiftdrop.parcel.domain.model.MerchantStanding;
import lk.swiftdrop.parcel.domain.model.MerchantStatus;
import lk.swiftdrop.parcel.domain.model.Parcel;
import lk.swiftdrop.parcel.domain.model.TrackingNumber;
import lk.swiftdrop.parcel.domain.model.TrackingNumberFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantOrderServiceTest {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private double count(String name, String tag, String value) {
        return registry.get(name).tag(tag, value).counter().count();
    }


    private static final Clock CLOCK = Clock.fixed(ParcelFixtures.NOW, ZoneOffset.UTC);
    private static final String MERCHANT = "00000000-0000-4000-8000-0000000000a1";
    private static final String OTHER_MERCHANT = "00000000-0000-4000-8000-0000000000b2";
    private static final AuthenticatedUser API_CLIENT =
            ParcelFixtures.user("service-account-merchant-a1", "MERCHANT_API", MERCHANT);
    private static final IdempotencyKey KEY = new IdempotencyKey("order-1001-attempt");
    private static final MerchantStanding APPROVED = new MerchantStanding(MERCHANT, MerchantStatus.APPROVED,
            "Lanka Books", "lankabooks@example.com", ParcelFixtures.NOW.minusSeconds(3600));

    @Mock
    private MerchantStatusCache merchants;
    @Mock
    private IdempotencyStore idempotency;
    @Mock
    private QuotePort pricing;
    @Mock
    private ParcelRepository parcels;
    @Mock
    private OutboxWriter outbox;
    @Mock
    private MerchantOrderMetrics metrics;

    private final RecordingTransactions transactions = new RecordingTransactions();
    private MerchantOrderService service;

    /** Runs the callback like a transaction and records whether it committed or rolled back. */
    static final class RecordingTransactions implements TransactionOperations {
        int committed;
        int rolledBack;

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            try {
                T result = action.doInTransaction(new SimpleTransactionStatus());
                committed++;
                return result;
            } catch (RuntimeException failed) {
                rolledBack++;
                throw failed;
            }
        }
    }

    @BeforeEach
    void setUp() {
        service = new MerchantOrderService(merchants, idempotency, pricing, parcels,
                new TrackingNumberFactory(CLOCK, new Random(7)), outbox, metrics, transactions, CLOCK,
                new MicrometerParcelMetrics(registry));
        lenient().when(merchants.find(MERCHANT)).thenReturn(Optional.of(APPROVED));
        lenient().when(merchants.findForOrder(MERCHANT)).thenReturn(Optional.of(APPROVED));
        lenient().when(idempotency.find(anyString(), any())).thenReturn(Optional.empty());
        lenient().when(pricing.quote(any())).thenReturn(ParcelFixtures.quote(UUID.randomUUID(),
                ParcelFixtures.NOW.plusSeconds(900)));
        lenient().when(parcels.save(any())).thenAnswer(call -> stored(call.getArgument(0)));
    }

    private static Parcel stored(Parcel parcel) {
        return new Parcel(parcel.id(), parcel.trackingNumber(), parcel.source(), parcel.customerId(),
                parcel.merchantId(), parcel.merchantOrderRef(), parcel.details(), parcel.status(), parcel.courierId(),
                parcel.history(), parcel.createdAt(), parcel.updatedAt(), 0L);
    }

    private static MerchantOrderCommand order() {
        return RequestFingerprintTest.order();
    }

    private static Parcel existingOrderOf(String merchantId) {
        return stored(Parcel.bookForMerchant(TrackingNumber.of(LocalDate.of(2026, 10, 20), "PRV001"), merchantId,
                "ORD-1001", "service-account", ParcelFixtures.details(), ParcelFixtures.NOW.minusSeconds(60)));
    }

    private static IdempotencyRecord recordFor(Parcel parcel, String hash) {
        return new IdempotencyRecord(parcel.merchantId(), KEY, parcel.id(), hash, ParcelFixtures.NOW.minusSeconds(60));
    }

    private void assertNothingWritten() {
        verify(parcels, never()).save(any());
        verify(idempotency, never()).save(any());
        verifyNoInteractions(outbox);
    }

    // --- a new order

    @Test
    void anApprovedMerchantsOrderIsPricedStoredAndPublishedAsItsOwn() {
        MerchantOrderResult result = service.create(API_CLIENT, KEY, order());

        Parcel parcel = result.parcel();
        assertThat(result.replayed()).isFalse();
        assertThat(parcel.source()).isEqualTo(ParcelSource.MERCHANT);
        assertThat(parcel.merchantId()).as("from the token, never the request").isEqualTo(MERCHANT);
        assertThat(parcel.customerId()).isNull();
        assertThat(parcel.merchantOrderRef()).isEqualTo("ORD-1001");
        assertThat(parcel.status()).isEqualTo(ParcelStatus.CREATED);
        assertThat(parcel.details().fee().amount()).isEqualByComparingTo("650.00");
        assertThat(parcel.history().getFirst().actorId()).isEqualTo("service-account-merchant-a1");
        assertThat(parcel.history().getFirst().actorRole()).isEqualTo(ActorRole.MERCHANT_API);
        assertThat(transactions.committed).isEqualTo(1);

        ArgumentCaptor<IdempotencyRecord> record = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(idempotency).save(record.capture());
        assertThat(record.getValue().merchantId()).isEqualTo(MERCHANT);
        assertThat(record.getValue().key()).isEqualTo(KEY);
        assertThat(record.getValue().parcelId()).isEqualTo(parcel.id());
        assertThat(record.getValue().requestHash()).isEqualTo(RequestFingerprint.of(order()));
        assertThat(count("parcel.bookings", "source", "merchant")).isEqualTo(1);
    }

    @Test
    void theEventNamesTheMerchantsBusinessAsSender() {
        service.create(API_CLIENT, KEY, order());

        ArgumentCaptor<ParcelCreated> event = ArgumentCaptor.forClass(ParcelCreated.class);
        verify(outbox).publish(event.capture());
        assertThat(event.getValue().source()).isEqualTo(ParcelSource.MERCHANT);
        assertThat(event.getValue().merchantId()).isEqualTo(MERCHANT);
        assertThat(event.getValue().merchantOrderRef()).isEqualTo("ORD-1001");
        assertThat(event.getValue().customerId()).isNull();
        assertThat(event.getValue().contacts().senderName()).isEqualTo("Lanka Books");
        assertThat(event.getValue().contacts().senderEmail()).isEqualTo("lankabooks@example.com");
        assertThat(event.getValue().pickup().city()).as("no street line on Kafka").isEqualTo("Colombo 03");
    }

    @Test
    void pricingIsAskedFirstAndEverythingElseIsWrittenInOneTransaction() {
        service.create(API_CLIENT, KEY, order());

        InOrder order = inOrder(merchants, pricing, parcels, idempotency, outbox);
        order.verify(merchants).find(MERCHANT);
        order.verify(pricing).quote(any());
        order.verify(merchants).findForOrder(MERCHANT);
        order.verify(parcels).save(any());
        order.verify(idempotency).save(any());
        order.verify(outbox).publish(any());
        assertThat(transactions.committed).isEqualTo(1);
    }

    @Test
    void theCodAmountIsSentToPricing() {
        MerchantOrderCommand withCod = new MerchantOrderCommand("ORD-1001", ParcelFixtures.PICKUP,
                ParcelFixtures.DROPOFF, ParcelFixtures.RECIPIENT, new BigDecimal("2"), order().sizeClass(),
                order().serviceLevel(), new BigDecimal("4500.00"));

        service.create(API_CLIENT, KEY, withCod);

        ArgumentCaptor<MerchantQuoteRequest> request = ArgumentCaptor.forClass(MerchantQuoteRequest.class);
        verify(pricing).quote(request.capture());
        assertThat(request.getValue().codAmount()).isEqualByComparingTo("4500.00");
        assertThat(request.getValue().fromDistrict()).isEqualTo("Colombo");
        assertThat(request.getValue().toDistrict()).isEqualTo("Kandy");
    }

    // --- who may order

    @Test
    void aTokenWithoutAMerchantIsRefusedBeforeAnythingElse() {
        AuthenticatedUser noMerchant = ParcelFixtures.user("service-account-x", "MERCHANT_API", null);

        assertThatThrownBy(() -> service.create(noMerchant, KEY, order()))
                .isInstanceOfSatisfying(MerchantAccessDeniedException.class, e -> {
                    assertThat(e.status().value()).isEqualTo(403);
                    assertThat(e.code()).isEqualTo(MerchantAccessDeniedException.NO_MERCHANT);
                });
        verifyNoInteractions(merchants, idempotency, pricing, parcels, outbox);
    }

    @Test
    void aMerchantParcelServiceHasNeverHeardOfIsRefused() {
        when(merchants.find(MERCHANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(API_CLIENT, KEY, order()))
                .isInstanceOfSatisfying(MerchantAccessDeniedException.class,
                        e -> assertThat(e.code()).isEqualTo(MerchantAccessDeniedException.NOT_APPROVED));
        verifyNoInteractions(idempotency, pricing, outbox);
        assertNothingWritten();
    }

    @ParameterizedTest
    @EnumSource(value = MerchantStatus.class, names = {"PENDING", "REJECTED", "SUSPENDED"})
    void aMerchantThatIsNotApprovedIsRefused(MerchantStatus status) {
        when(merchants.find(MERCHANT)).thenReturn(Optional.of(new MerchantStanding(MERCHANT, status, "Lanka Books",
                null, ParcelFixtures.NOW)));

        assertThatThrownBy(() -> service.create(API_CLIENT, KEY, order()))
                .isInstanceOfSatisfying(MerchantAccessDeniedException.class,
                        e -> assertThat(e.status().value()).isEqualTo(403));
        verifyNoInteractions(idempotency, pricing, outbox);
        assertNothingWritten();
    }

    @Test
    void aMerchantSuspendedWhilePricingRanIsRefusedAtPersistenceAndNothingIsWritten() {
        when(merchants.findForOrder(MERCHANT)).thenReturn(Optional.of(new MerchantStanding(MERCHANT,
                MerchantStatus.SUSPENDED, "Lanka Books", null, ParcelFixtures.NOW)));

        assertThatThrownBy(() -> service.create(API_CLIENT, KEY, order()))
                .isInstanceOfSatisfying(MerchantAccessDeniedException.class,
                        e -> assertThat(e.code()).isEqualTo(MerchantAccessDeniedException.NOT_APPROVED));
        verify(pricing).quote(any());
        assertNothingWritten();
        assertThat(transactions.rolledBack).isEqualTo(1);
        assertThat(transactions.committed).isZero();
    }

    @Test
    void aMerchantThatVanishedFromTheCacheWhilePricingRanIsRefused() {
        when(merchants.findForOrder(MERCHANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(API_CLIENT, KEY, order()))
                .isInstanceOf(MerchantAccessDeniedException.class);
        assertNothingWritten();
    }

    @Test
    void theSenderDetailsAreTheOnesReadAtPersistence() {
        when(merchants.findForOrder(MERCHANT)).thenReturn(Optional.of(new MerchantStanding(MERCHANT,
                MerchantStatus.APPROVED, "Lanka Books Ltd", "orders@lankabooks.lk", ParcelFixtures.NOW)));

        service.create(API_CLIENT, KEY, order());

        ArgumentCaptor<ParcelCreated> event = ArgumentCaptor.forClass(ParcelCreated.class);
        verify(outbox).publish(event.capture());
        assertThat(event.getValue().contacts().senderName()).isEqualTo("Lanka Books Ltd");
        assertThat(event.getValue().contacts().senderEmail()).isEqualTo("orders@lankabooks.lk");
    }

    // --- idempotency

    @Test
    void theSameKeyAndBodyReplaysTheOriginalParcelWithoutPricingOrWriting() {
        Parcel original = existingOrderOf(MERCHANT);
        when(idempotency.find(MERCHANT, KEY)).thenReturn(Optional.of(recordFor(original, RequestFingerprint.of(order()))));
        when(parcels.findById(original.id())).thenReturn(Optional.of(original));

        MerchantOrderResult result = service.create(API_CLIENT, KEY, order());

        assertThat(result.replayed()).isTrue();
        assertThat(result.parcel().id()).isEqualTo(original.id());
        verifyNoInteractions(pricing, outbox);
        assertNothingWritten();
        assertThat(transactions.rolledBack).isZero();
        assertThat(count("parcel.bookings", "source", "merchant")).as("a replay books nothing").isZero();
    }

    @Test
    void theSameKeyWithADifferentBodyIsAConflictAndNothingHappens() {
        Parcel original = existingOrderOf(MERCHANT);
        when(idempotency.find(MERCHANT, KEY)).thenReturn(Optional.of(recordFor(original, "f".repeat(64))));

        assertThatThrownBy(() -> service.create(API_CLIENT, KEY, order()))
                .isInstanceOfSatisfying(IdempotencyKeyReusedException.class,
                        e -> assertThat(e.status().value()).isEqualTo(409));
        verifyNoInteractions(pricing, outbox);
        assertNothingWritten();
    }

    @Test
    void keysAreLookedUpForTheTokensMerchantOnly() {
        service.create(API_CLIENT, KEY, order());

        verify(idempotency).find(eq(MERCHANT), eq(KEY));
        verify(idempotency, never()).find(eq(OTHER_MERCHANT), any());
    }

    @Test
    void aRecordPointingAtAnotherMerchantsParcelIsNeverReturned() {
        Parcel someoneElses = existingOrderOf(OTHER_MERCHANT);
        when(idempotency.find(MERCHANT, KEY))
                .thenReturn(Optional.of(new IdempotencyRecord(MERCHANT, KEY, someoneElses.id(),
                        RequestFingerprint.of(order()), ParcelFixtures.NOW)));
        when(parcels.findById(someoneElses.id())).thenReturn(Optional.of(someoneElses));

        assertThatThrownBy(() -> service.create(API_CLIENT, KEY, order())).isInstanceOf(IllegalStateException.class);
    }

    // --- concurrent requests with the same key

    @Test
    void losingARaceWithTheSameBodyRollsBackAndReplaysTheWinnersParcel() {
        Parcel winner = existingOrderOf(MERCHANT);
        when(idempotency.find(MERCHANT, KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(recordFor(winner, RequestFingerprint.of(order()))));
        doThrow(new DuplicateIdempotencyKeyException(null)).when(idempotency).save(any());
        when(parcels.findById(winner.id())).thenReturn(Optional.of(winner));

        MerchantOrderResult result = service.create(API_CLIENT, KEY, order());

        assertThat(result.replayed()).isTrue();
        assertThat(result.parcel().id()).as("the winner's parcel, not a second one").isEqualTo(winner.id());
        assertThat(transactions.rolledBack).as("the loser's parcel and outbox row are undone").isEqualTo(1);
    }

    @Test
    void losingARaceWithADifferentBodyIsAConflict() {
        Parcel winner = existingOrderOf(MERCHANT);
        when(idempotency.find(MERCHANT, KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(recordFor(winner, "e".repeat(64))));
        doThrow(new DuplicateIdempotencyKeyException(null)).when(idempotency).save(any());

        assertThatThrownBy(() -> service.create(API_CLIENT, KEY, order()))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertThat(transactions.rolledBack).isEqualTo(1);
    }

    @Test
    void aRaceWhoseWinnerLeftNoRecordAsksTheMerchantToRetry() {
        doThrow(new DuplicateIdempotencyKeyException(null)).when(idempotency).save(any());

        assertThatThrownBy(() -> service.create(API_CLIENT, KEY, order()))
                .isInstanceOfSatisfying(ConflictException.class,
                        e -> assertThat(e.code()).isEqualTo("IDEMPOTENCY_KEY_IN_PROGRESS"));
        assertThat(transactions.rolledBack).isEqualTo(1);
    }

    // --- pricing failures leave nothing behind

    @Test
    void whenPricingIsUnavailableNothingIsStoredAndTheKeyStaysFree() {
        when(pricing.quote(any())).thenThrow(new PricingUnavailableException(new RuntimeException("timeout")));

        assertThatThrownBy(() -> service.create(API_CLIENT, KEY, order()))
                .isInstanceOfSatisfying(PricingUnavailableException.class,
                        e -> assertThat(e.status().value()).isEqualTo(503));
        assertNothingWritten();
        assertThat(transactions.committed + transactions.rolledBack).as("no transaction was opened").isZero();
    }

    @Test
    void whenPricingRefusesTheOrderNothingIsStored() {
        when(pricing.quote(any())).thenThrow(new OrderNotPriceableException("DISTRICT_NOT_SERVICED",
                "Jaffna is not serviced yet."));

        assertThatThrownBy(() -> service.create(API_CLIENT, KEY, order()))
                .isInstanceOfSatisfying(OrderNotPriceableException.class, e -> {
                    assertThat(e.status().value()).isEqualTo(422);
                    assertThat(e.code()).isEqualTo("DISTRICT_NOT_SERVICED");
                });
        assertNothingWritten();
    }

    // --- metrics: every request counted once, with how it ended

    @Test
    void aNewOrderIsCountedAsCreatedOnceItHasCommitted() {
        service.create(API_CLIENT, KEY, order());

        verify(metrics).orderRequest(Outcome.CREATED);
        verifyNoMoreInteractions(metrics);
        assertThat(transactions.committed).isEqualTo(1);
    }

    @Test
    void aRetryWithTheSameKeyAndBodyIsCountedAsAReplay() {
        Parcel original = existingOrderOf(MERCHANT);
        when(idempotency.find(MERCHANT, KEY)).thenReturn(Optional.of(recordFor(original, RequestFingerprint.of(order()))));
        when(parcels.findById(original.id())).thenReturn(Optional.of(original));

        service.create(API_CLIENT, KEY, order());

        verify(metrics).orderRequest(Outcome.REPLAYED);
        verifyNoMoreInteractions(metrics);
    }

    @Test
    void losingARaceWithTheSameBodyIsCountedAsAReplayNotACreation() {
        Parcel winner = existingOrderOf(MERCHANT);
        when(idempotency.find(MERCHANT, KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(recordFor(winner, RequestFingerprint.of(order()))));
        doThrow(new DuplicateIdempotencyKeyException(null)).when(idempotency).save(any());
        when(parcels.findById(winner.id())).thenReturn(Optional.of(winner));

        service.create(API_CLIENT, KEY, order());

        verify(metrics).orderRequest(Outcome.REPLAYED);
        verifyNoMoreInteractions(metrics);
    }

    @Test
    void aRefusedRequestIsCountedWithWhyAndStillRefused() {
        Parcel original = existingOrderOf(MERCHANT);
        when(idempotency.find(MERCHANT, KEY)).thenReturn(Optional.of(recordFor(original, "f".repeat(64))));

        assertThatThrownBy(() -> service.create(API_CLIENT, KEY, order()))
                .isInstanceOf(IdempotencyKeyReusedException.class);

        verify(metrics).orderRequest(Outcome.IDEMPOTENCY_CONFLICT);
        verifyNoMoreInteractions(metrics);
    }

    @Test
    void anUnavailablePricingServiceIsCountedAsSuch() {
        when(pricing.quote(any())).thenThrow(new PricingUnavailableException(new RuntimeException("timeout")));

        assertThatThrownBy(() -> service.create(API_CLIENT, KEY, order())).isInstanceOf(PricingUnavailableException.class);

        verify(metrics).orderRequest(Outcome.PRICING_UNAVAILABLE);
    }

    @Test
    void eachRefusalMapsToItsOwnOutcome() {
        assertThat(MerchantOrderService.outcomeOf(new IdempotencyKeyReusedException()))
                .isEqualTo(Outcome.IDEMPOTENCY_CONFLICT);
        assertThat(MerchantOrderService.outcomeOf(new ConflictException("IDEMPOTENCY_KEY_IN_PROGRESS", "retry")))
                .isEqualTo(Outcome.IDEMPOTENCY_IN_PROGRESS);
        assertThat(MerchantOrderService.outcomeOf(new ConflictException("PARCEL_MODIFIED", "changed")))
                .as("another conflict is not an idempotency one").isEqualTo(Outcome.FAILED);
        assertThat(MerchantOrderService.outcomeOf(MerchantAccessDeniedException.notApproved()))
                .isEqualTo(Outcome.NOT_APPROVED);
        assertThat(MerchantOrderService.outcomeOf(MerchantAccessDeniedException.noMerchant()))
                .isEqualTo(Outcome.NO_MERCHANT);
        assertThat(MerchantOrderService.outcomeOf(new OrderNotPriceableException("DISTRICT_NOT_SERVICED", "No")))
                .isEqualTo(Outcome.NOT_PRICEABLE);
        assertThat(MerchantOrderService.outcomeOf(new PricingUnavailableException(new RuntimeException("x"))))
                .isEqualTo(Outcome.PRICING_UNAVAILABLE);
        assertThat(MerchantOrderService.outcomeOf(new IllegalStateException("database down")))
                .isEqualTo(Outcome.FAILED);
    }

    // --- listing

    @Test
    void aMerchantListsOnlyItsOwnOrders() {
        MerchantOrderFilter filter = new MerchantOrderFilter(ParcelStatus.CREATED, Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-11-01T00:00:00Z"));
        PagedResult<Parcel> page = new PagedResult<>(List.of(existingOrderOf(MERCHANT)), 0, 20, 1, 1);
        when(parcels.findByMerchant(MERCHANT, filter, 0, 20)).thenReturn(page);

        assertThat(service.list(API_CLIENT, filter, 0, 20)).isSameAs(page);
        verify(parcels, never()).findByMerchant(eq(OTHER_MERCHANT), any(), anyInt(), anyInt());
    }

    @Test
    void aSuspendedMerchantMayStillReadItsHistory() {
        when(parcels.findByMerchant(eq(MERCHANT), any(), eq(0), eq(20)))
                .thenReturn(new PagedResult<>(List.of(), 0, 20, 0, 0));

        service.list(API_CLIENT, MerchantOrderFilter.none(), 0, 20);

        verifyNoInteractions(merchants);
    }

    @Test
    void listingNeedsAMerchantInTheToken() {
        assertThatThrownBy(() -> service.list(ParcelFixtures.user("u", "MERCHANT", null), MerchantOrderFilter.none(),
                0, 20)).isInstanceOf(MerchantAccessDeniedException.class);
        verifyNoInteractions(parcels);
    }

    @Test
    void aDateRangeThatEndsBeforeItStartsIsABadRequest() {
        Instant day = Instant.parse("2026-10-10T00:00:00Z");

        assertThatThrownBy(() -> service.list(API_CLIENT, new MerchantOrderFilter(null, day, day), 0, 20))
                .isInstanceOfSatisfying(BadRequestException.class,
                        e -> assertThat(e.code()).isEqualTo("INVALID_DATE_RANGE"));
        verifyNoInteractions(parcels);
    }
}
