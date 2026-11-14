package lk.swiftdrop.parcel;

import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.events.common.Types.Money;
import lk.swiftdrop.events.common.Types.ServiceLevel;
import lk.swiftdrop.events.common.Types.SizeClass;
import lk.swiftdrop.parcel.application.model.Quote;
import lk.swiftdrop.parcel.domain.model.Address;
import lk.swiftdrop.parcel.domain.model.BookingDetails;
import lk.swiftdrop.parcel.domain.model.Parcel;
import lk.swiftdrop.parcel.domain.model.Recipient;
import lk.swiftdrop.parcel.domain.model.TrackingNumber;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Test data: a 2 kg parcel from Colombo 03 to Kandy, the booking in the acceptance criteria. */
public final class ParcelFixtures {

    public static final Instant NOW = Instant.parse("2026-10-20T08:00:00Z");
    public static final Address PICKUP =
            new Address("12 Galle Road", null, "Colombo 03", "Colombo", "00300", null, null);
    public static final Address DROPOFF =
            new Address("45 Peradeniya Road", "Flat 2", "Kandy", "Kandy", "20000", null, null);
    public static final Recipient RECIPIENT = new Recipient("Saman Perera", "0712345678", "saman@example.com");

    private ParcelFixtures() {
    }

    public static AuthenticatedUser customer(String id) {
        return user(id, "CUSTOMER", null);
    }

    public static AuthenticatedUser user(String id, String role, String merchantId) {
        return new AuthenticatedUser(id, Optional.of(id + "@example.com"), Optional.of("Nimali Silva"), Set.of(role),
                Optional.ofNullable(merchantId));
    }

    public static Quote quote(UUID quoteId, Instant expiresAt) {
        return new Quote(quoteId, Money.lkr(new BigDecimal("650.00")), "WP-COL", "CP-KDY", "Colombo", "Kandy",
                new BigDecimal("2.00"), SizeClass.S, ServiceLevel.STANDARD, expiresAt);
    }

    public static BookingDetails details() {
        return new BookingDetails(PICKUP, DROPOFF, "WP-COL", "CP-KDY", RECIPIENT, new BigDecimal("2.00"), SizeClass.S,
                ServiceLevel.STANDARD, Money.lkr(new BigDecimal("650.00")), UUID.randomUUID(), "Leave at reception");
    }

    /** A stored parcel in status CREATED, booked by the given customer. */
    public static Parcel bookedBy(String customerId) {
        Parcel booked = Parcel.bookForCustomer(TrackingNumber.of(LocalDate.of(2026, 10, 20), "K7Q2M9"), customerId,
                details(), NOW);
        return new Parcel(booked.id(), booked.trackingNumber(), booked.source(), booked.customerId(),
                booked.merchantId(), booked.merchantOrderRef(), booked.details(), booked.status(), booked.courierId(),
                booked.history(), booked.createdAt(), booked.updatedAt(), 0L);
    }
}
