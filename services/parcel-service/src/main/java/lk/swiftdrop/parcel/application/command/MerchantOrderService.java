package lk.swiftdrop.parcel.application.command;

import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.common.web.error.BadRequestException;
import lk.swiftdrop.common.web.error.ConflictException;
import lk.swiftdrop.events.common.Types.Location;
import lk.swiftdrop.events.parcel.ParcelCreated;
import lk.swiftdrop.outbox.OutboxWriter;
import lk.swiftdrop.parcel.application.exception.MerchantAccessDeniedException;
import lk.swiftdrop.parcel.application.exception.OrderNotPriceableException;
import lk.swiftdrop.parcel.application.exception.PricingUnavailableException;
import lk.swiftdrop.parcel.application.model.MerchantOrderFilter;
import lk.swiftdrop.parcel.application.model.MerchantOrderResult;
import lk.swiftdrop.parcel.application.model.MerchantQuoteRequest;
import lk.swiftdrop.parcel.application.model.PagedResult;
import lk.swiftdrop.parcel.application.model.Quote;
import lk.swiftdrop.parcel.application.port.out.DuplicateIdempotencyKeyException;
import lk.swiftdrop.parcel.application.port.out.IdempotencyStore;
import lk.swiftdrop.parcel.application.port.out.MerchantOrderMetrics;
import lk.swiftdrop.parcel.application.port.out.MerchantOrderMetrics.Outcome;
import lk.swiftdrop.parcel.application.port.out.MerchantStatusCache;
import lk.swiftdrop.parcel.application.port.out.ParcelRepository;
import lk.swiftdrop.parcel.application.port.out.QuotePort;
import lk.swiftdrop.parcel.domain.exception.IdempotencyKeyReusedException;
import lk.swiftdrop.parcel.domain.model.Address;
import lk.swiftdrop.parcel.domain.model.BookingDetails;
import lk.swiftdrop.parcel.domain.model.IdempotencyKey;
import lk.swiftdrop.parcel.domain.model.IdempotencyRecord;
import lk.swiftdrop.parcel.domain.model.MerchantStanding;
import lk.swiftdrop.parcel.domain.model.Parcel;
import lk.swiftdrop.parcel.domain.model.TrackingNumber;
import lk.swiftdrop.parcel.domain.model.TrackingNumberFactory;
import lk.swiftdrop.parcel.domain.policy.IdempotencyPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.util.Optional;

/**
 * The merchant order API (design figures 7, 10 and 13).
 *
 * <p>Creating an order, in this order:
 * <ol>
 *   <li>The merchant is the one in the caller's token, never the request, and must be APPROVED in
 *       the status cache; an unknown merchant is refused (403).</li>
 *   <li>The Idempotency-Key is looked up for that merchant: the same body replays the original
 *       parcel, a different body is a 409. Neither calls pricing nor writes anything.</li>
 *   <li>pricing-service prices the order, outside any transaction. If it fails, nothing has been
 *       written, so the merchant can retry with the same key.</li>
 *   <li>One transaction re-checks that the merchant is still APPROVED (it may have been suspended
 *       while pricing ran), holding a shared lock on its status so a suspension cannot commit
 *       until this order has, and then stores the parcel, its idempotency record and the
 *       ParcelCreated outbox row.
 *       If a concurrent request stored the same key first, the record insert fails, everything in
 *       this transaction rolls back, and the request is answered from the winner's record instead.
 *       So one key can never create two parcels.</li>
 * </ol>
 */
@Service
public class MerchantOrderService {

    private final MerchantStatusCache merchants;
    private final IdempotencyStore idempotency;
    private final QuotePort pricing;
    private final ParcelRepository parcels;
    private final TrackingNumberFactory trackingNumbers;
    private final OutboxWriter outbox;
    private final MerchantOrderMetrics metrics;
    private final TransactionOperations transaction;
    private final Clock clock;

    /** The 409 answered when a concurrent request with the same key has not finished. */
    static final String KEY_IN_PROGRESS = "IDEMPOTENCY_KEY_IN_PROGRESS";

    public MerchantOrderService(MerchantStatusCache merchants, IdempotencyStore idempotency, QuotePort pricing,
                                ParcelRepository parcels, TrackingNumberFactory trackingNumbers, OutboxWriter outbox,
                                MerchantOrderMetrics metrics, TransactionOperations transaction, Clock clock) {
        this.merchants = merchants;
        this.idempotency = idempotency;
        this.pricing = pricing;
        this.parcels = parcels;
        this.trackingNumbers = trackingNumbers;
        this.outbox = outbox;
        this.metrics = metrics;
        this.transaction = transaction;
        this.clock = clock;
    }

    /**
     * Every request is counted once, with how it ended (see {@link MerchantOrderMetrics.Outcome}); a
     * new order only once its transaction has committed.
     *
     * @throws MerchantAccessDeniedException  403 when the token names no merchant or it is not APPROVED
     * @throws IdempotencyKeyReusedException  409 when the key was used for a different order
     * @throws PricingUnavailableException    503, nothing stored
     * @throws OrderNotPriceableException     422, nothing stored
     */
    public MerchantOrderResult create(AuthenticatedUser caller, IdempotencyKey key, MerchantOrderCommand order) {
        MerchantOrderResult result;
        try {
            result = createOrReplay(caller, key, order);
        } catch (RuntimeException refused) {
            metrics.orderRequest(outcomeOf(refused));
            throw refused;
        }
        metrics.orderRequest(result.replayed() ? Outcome.REPLAYED : Outcome.CREATED);
        return result;
    }

    /** How a refused request ended, for the metrics. Subclasses are checked before their parents. */
    static Outcome outcomeOf(RuntimeException refused) {
        if (refused instanceof IdempotencyKeyReusedException) {
            return Outcome.IDEMPOTENCY_CONFLICT;
        }
        if (refused instanceof ConflictException conflict && KEY_IN_PROGRESS.equals(conflict.code())) {
            return Outcome.IDEMPOTENCY_IN_PROGRESS;
        }
        if (refused instanceof MerchantAccessDeniedException denied) {
            return MerchantAccessDeniedException.NO_MERCHANT.equals(denied.code())
                    ? Outcome.NO_MERCHANT : Outcome.NOT_APPROVED;
        }
        if (refused instanceof OrderNotPriceableException) {
            return Outcome.NOT_PRICEABLE;
        }
        if (refused instanceof PricingUnavailableException) {
            return Outcome.PRICING_UNAVAILABLE;
        }
        return Outcome.FAILED;
    }

    private MerchantOrderResult createOrReplay(AuthenticatedUser caller, IdempotencyKey key,
                                               MerchantOrderCommand order) {
        String merchantId = merchantOf(caller);
        // Checked first so a merchant that is not approved never reaches pricing; checked again at persistence.
        merchants.find(merchantId)
                .filter(MerchantStanding::isApproved)
                .orElseThrow(MerchantAccessDeniedException::notApproved);
        String requestHash = RequestFingerprint.of(order);

        Optional<MerchantOrderResult> earlier = resolveExisting(merchantId, key, requestHash);
        if (earlier.isPresent()) {
            return earlier.get();
        }

        Quote quote = pricing.quote(toQuoteRequest(order));
        try {
            Parcel parcel = transaction.execute(status -> book(caller, merchantId, key, requestHash, order, quote));
            return new MerchantOrderResult(parcel, false);
        } catch (DuplicateIdempotencyKeyException lostTheRace) {
            // The winner has committed; answer exactly as a later retry would be answered.
            return resolveExisting(merchantId, key, requestHash).orElseThrow(() -> new ConflictException(
                    KEY_IN_PROGRESS,
                    "Another request with this Idempotency-Key did not finish. Retry with the same key."));
        }
    }

    /**
     * The caller's merchant's orders, newest first. A suspended merchant can still read its history;
     * only the token's merchant id is used, so no merchant can list another's orders.
     *
     * @throws MerchantAccessDeniedException 403 when the token names no merchant
     * @throws BadRequestException           400 when {@code from} is not before {@code to}
     */
    public PagedResult<Parcel> list(AuthenticatedUser caller, MerchantOrderFilter filter, int page, int size) {
        String merchantId = merchantOf(caller);
        if (filter.from() != null && filter.to() != null && !filter.from().isBefore(filter.to())) {
            throw new BadRequestException("INVALID_DATE_RANGE", "'from' must be before 'to'.");
        }
        return parcels.findByMerchant(merchantId, filter, page, size);
    }

    /** REPLAY returns the original parcel, CONFLICT is a 409, NEW is empty. */
    private Optional<MerchantOrderResult> resolveExisting(String merchantId, IdempotencyKey key, String requestHash) {
        Optional<IdempotencyRecord> record = idempotency.find(merchantId, key);
        return switch (IdempotencyPolicy.decide(record, requestHash)) {
            case NEW -> Optional.empty();
            case CONFLICT -> throw new IdempotencyKeyReusedException();
            case REPLAY -> Optional.of(new MerchantOrderResult(originalParcel(merchantId, record.get()), true));
        };
    }

    /** Loaded in a short transaction: the parcel's history is read with it. */
    private Parcel originalParcel(String merchantId, IdempotencyRecord record) {
        return transaction.execute(status -> parcels.findById(record.parcelId())
                .filter(parcel -> merchantId.equals(parcel.merchantId()))
                .orElseThrow(() -> new IllegalStateException("Idempotency record without its merchant parcel")));
    }

    private Parcel book(AuthenticatedUser caller, String merchantId, IdempotencyKey key, String requestHash,
                        MerchantOrderCommand order, Quote quote) {
        // Before anything is written: a suspension during pricing refuses the order, and rolls back nothing.
        MerchantStanding merchant = merchants.findForOrder(merchantId)
                .filter(MerchantStanding::isApproved)
                .orElseThrow(MerchantAccessDeniedException::notApproved);
        TrackingNumber trackingNumber = trackingNumbers.issue(parcels::existsByTrackingNumber);
        BookingDetails details = new BookingDetails(order.pickup(), order.dropoff(), quote.fromZone(), quote.toZone(),
                order.recipient(), quote.weightKg(), quote.sizeClass(), quote.serviceLevel(), quote.fee(),
                quote.quoteId(), null);
        Parcel parcel = parcels.save(Parcel.bookForMerchant(trackingNumber, merchant.merchantId(),
                order.merchantOrderRef(), caller.id(), details, clock.instant()));
        idempotency.save(new IdempotencyRecord(merchant.merchantId(), key, parcel.id(), requestHash, clock.instant()));
        outbox.publish(toEvent(parcel, merchant));
        return parcel;
    }

    private static String merchantOf(AuthenticatedUser caller) {
        return caller.merchantId().filter(id -> !id.isBlank()).orElseThrow(MerchantAccessDeniedException::noMerchant);
    }

    private static MerchantQuoteRequest toQuoteRequest(MerchantOrderCommand order) {
        return new MerchantQuoteRequest(order.pickup().city(), order.pickup().district(), order.dropoff().city(),
                order.dropoff().district(), order.weightKg(), order.sizeClass(), order.serviceLevel(),
                order.codAmount());
    }

    /** The sender is the merchant's business, as merchant-service last described it. */
    private static ParcelCreated toEvent(Parcel parcel, MerchantStanding merchant) {
        BookingDetails details = parcel.details();
        return new ParcelCreated(parcel.id(), parcel.trackingNumber().value(), parcel.source(), parcel.customerId(),
                parcel.merchantId(), parcel.merchantOrderRef(),
                coarse(details.pickup(), details.pickupZone()), coarse(details.dropoff(), details.dropoffZone()),
                details.sizeClass(), details.weightKg(), details.serviceLevel(), details.fee(),
                new ParcelCreated.Contacts(merchant.businessName(), merchant.contactEmail(), null,
                        details.recipient().name(), details.recipient().phone()),
                parcel.createdAt());
    }

    /** Street lines never travel on Kafka: only the city, district and zone. */
    private static Location coarse(Address address, String zoneCode) {
        return new Location(address.city(), address.district(), zoneCode);
    }
}
