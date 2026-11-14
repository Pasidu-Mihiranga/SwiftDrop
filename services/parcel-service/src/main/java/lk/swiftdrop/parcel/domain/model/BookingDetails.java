package lk.swiftdrop.parcel.domain.model;

import lk.swiftdrop.events.common.Types.Money;
import lk.swiftdrop.events.common.Types.ServiceLevel;
import lk.swiftdrop.events.common.Types.SizeClass;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What was booked. None of it changes after the booking.
 *
 * @param fee     copied from the quote; this service never calculates a price
 * @param quoteId the pricing-service quote the fee came from
 */
public record BookingDetails(Address pickup, Address dropoff, String pickupZone, String dropoffZone,
                             Recipient recipient, BigDecimal weightKg, SizeClass sizeClass,
                             ServiceLevel serviceLevel, Money fee, UUID quoteId, String notes) {
}
