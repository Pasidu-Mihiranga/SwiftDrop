package lk.swiftdrop.parcel.application.port.out;

import lk.swiftdrop.events.common.Types.FailureReason;
import lk.swiftdrop.events.common.Types.ParcelSource;
import lk.swiftdrop.events.common.Types.ParcelStatus;

/**
 * Business counters for bookings (M3) and the status lifecycle (M4), shown on the Grafana
 * "Parcels" dashboard. Every tag value is a closed list, so the number of series is fixed.
 */
public interface ParcelMetrics {

    /** Why a booking was refused before a parcel was created. */
    enum QuoteRejection { EXPIRED, MISMATCH, PRICING_UNAVAILABLE }

    void parcelBooked(ParcelSource source);

    /** A committed status change; {@code reason} is set only for FAILED. */
    void statusChanged(ParcelStatus to, FailureReason reason);

    void quoteRejected(QuoteRejection reason);
}
