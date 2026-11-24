package lk.swiftdrop.parcel.application.command;

import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.events.common.Types.Location;
import lk.swiftdrop.events.parcel.ParcelCreated;
import lk.swiftdrop.outbox.OutboxWriter;
import lk.swiftdrop.parcel.application.port.out.ParcelMetrics;
import lk.swiftdrop.events.common.Types.ParcelSource;
import lk.swiftdrop.parcel.application.exception.PricingUnavailableException;
import lk.swiftdrop.parcel.application.model.Quote;
import lk.swiftdrop.parcel.application.port.out.ParcelRepository;
import lk.swiftdrop.parcel.application.port.out.PricingPort;
import lk.swiftdrop.parcel.domain.exception.QuoteExpiredException;
import lk.swiftdrop.parcel.domain.exception.QuoteMismatchException;
import lk.swiftdrop.parcel.domain.model.Address;
import lk.swiftdrop.parcel.domain.model.BookingDetails;
import lk.swiftdrop.parcel.domain.model.Parcel;
import lk.swiftdrop.parcel.domain.model.TrackingNumber;
import lk.swiftdrop.parcel.domain.model.TrackingNumberFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;

/**
 * Books a delivery from a quote the customer has confirmed.
 *
 * <p>The quote is reloaded first, outside any transaction, so a slow pricing-service never holds a
 * database connection. Only a valid quote reaches the transaction, which stores the parcel, its
 * first history row and the {@code ParcelCreated} outbox row together. If the quote cannot be
 * validated, nothing is written (fail closed).
 */
@Service
public class CreateParcelUseCase {

    private final ParcelMetrics metrics;

    private final PricingPort pricing;
    private final ParcelRepository parcels;
    private final TrackingNumberFactory trackingNumbers;
    private final OutboxWriter outbox;
    private final TransactionOperations transaction;
    private final Clock clock;

    public CreateParcelUseCase(PricingPort pricing, ParcelRepository parcels, TrackingNumberFactory trackingNumbers,
                               OutboxWriter outbox, TransactionOperations transaction, Clock clock,
                               ParcelMetrics metrics) {
        this.metrics = metrics;
        this.pricing = pricing;
        this.parcels = parcels;
        this.trackingNumbers = trackingNumbers;
        this.outbox = outbox;
        this.transaction = transaction;
        this.clock = clock;
    }

    public Parcel create(AuthenticatedUser customer, CreateParcelCommand command) {
        Quote quote;
        try {
            quote = pricing.findQuote(command.quoteId())
                    .filter(found -> clock.instant().isBefore(found.expiresAt()))
                    .orElse(null);
        } catch (PricingUnavailableException unavailable) {
            metrics.quoteRejected(ParcelMetrics.QuoteRejection.PRICING_UNAVAILABLE);
            throw unavailable;
        }
        if (quote == null) {
            metrics.quoteRejected(ParcelMetrics.QuoteRejection.EXPIRED);
            throw new QuoteExpiredException();
        }
        if (!pricedForRoute(quote, command)) {
            metrics.quoteRejected(ParcelMetrics.QuoteRejection.MISMATCH);
            throw new QuoteMismatchException();
        }
        Quote accepted = quote;
        Parcel parcel = transaction.execute(status -> book(customer, command, accepted));
        metrics.parcelBooked(ParcelSource.CUSTOMER);
        return parcel;
    }

    private Parcel book(AuthenticatedUser customer, CreateParcelCommand command, Quote quote) {
        TrackingNumber trackingNumber = trackingNumbers.issue(parcels::existsByTrackingNumber);
        BookingDetails details = new BookingDetails(command.pickup(), command.dropoff(), quote.fromZone(),
                quote.toZone(), command.recipient(), quote.weightKg(), quote.sizeClass(), quote.serviceLevel(),
                quote.fee(), quote.quoteId(), command.notes());
        Parcel parcel = parcels.save(Parcel.bookForCustomer(trackingNumber, customer.id(), details, clock.instant()));
        outbox.publish(toEvent(parcel, customer));
        return parcel;
    }

    /** A quote priced for one route must not be spent on another. */
    private static boolean pricedForRoute(Quote quote, CreateParcelCommand command) {
        return sameDistrict(quote.fromDistrict(), command.pickup().district())
                && sameDistrict(quote.toDistrict(), command.dropoff().district());
    }

    private static boolean sameDistrict(String quoted, String booked) {
        return quoted == null || quoted.strip().equalsIgnoreCase(booked.strip());
    }

    private static ParcelCreated toEvent(Parcel parcel, AuthenticatedUser customer) {
        BookingDetails details = parcel.details();
        return new ParcelCreated(parcel.id(), parcel.trackingNumber().value(), parcel.source(), parcel.customerId(),
                parcel.merchantId(), parcel.merchantOrderRef(),
                coarse(details.pickup(), details.pickupZone()), coarse(details.dropoff(), details.dropoffZone()),
                details.sizeClass(), details.weightKg(), details.serviceLevel(), details.fee(),
                new ParcelCreated.Contacts(customer.name().orElse(null), customer.email().orElse(null), null,
                        details.recipient().name(), details.recipient().phone()),
                parcel.createdAt());
    }

    /** Street lines never travel on Kafka: only the city, district and zone. */
    private static Location coarse(Address address, String zoneCode) {
        return new Location(address.city(), address.district(), zoneCode);
    }
}
