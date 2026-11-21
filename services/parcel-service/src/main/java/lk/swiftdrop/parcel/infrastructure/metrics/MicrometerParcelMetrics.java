package lk.swiftdrop.parcel.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lk.swiftdrop.events.common.Types.FailureReason;
import lk.swiftdrop.events.common.Types.ParcelSource;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.parcel.application.port.out.ParcelMetrics;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Publishes the parcel business counters through Micrometer (Prometheus names in brackets):
 * {@code parcel.bookings} [{@code parcel_bookings_total}] by {@code source},
 * {@code parcel.status.changes} [{@code parcel_status_changes_total}] by {@code status},
 * {@code parcel.delivery.failures} [{@code parcel_delivery_failures_total}] by {@code reason} and
 * {@code parcel.quote.rejections} [{@code parcel_quote_rejections_total}] by {@code reason}.
 * Every series is registered at zero up front, so increase() also sees the first event of a kind.
 */
@Component
public class MicrometerParcelMetrics implements ParcelMetrics {

    static final String BOOKINGS = "parcel.bookings";
    static final String STATUS_CHANGES = "parcel.status.changes";
    static final String DELIVERY_FAILURES = "parcel.delivery.failures";
    static final String QUOTE_REJECTIONS = "parcel.quote.rejections";

    private final Map<ParcelSource, Counter> bookings;
    private final Map<ParcelStatus, Counter> statusChanges;
    private final Map<FailureReason, Counter> failures;
    private final Map<QuoteRejection, Counter> rejections;

    public MicrometerParcelMetrics(MeterRegistry registry) {
        bookings = counters(registry, ParcelSource.class, BOOKINGS, "source", "Parcels booked, by channel");
        statusChanges = counters(registry, ParcelStatus.class, STATUS_CHANGES, "status",
                "Committed parcel status changes, by the status reached");
        failures = counters(registry, FailureReason.class, DELIVERY_FAILURES, "reason", "Failed deliveries, by reason");
        rejections = counters(registry, QuoteRejection.class, QUOTE_REJECTIONS, "reason",
                "Bookings refused because of the quote, by reason");
    }

    @Override
    public void parcelBooked(ParcelSource source) {
        bookings.get(source).increment();
    }

    @Override
    public void statusChanged(ParcelStatus to, FailureReason reason) {
        statusChanges.get(to).increment();
        if (to == ParcelStatus.FAILED && reason != null) {
            failures.get(reason).increment();
        }
    }

    @Override
    public void quoteRejected(QuoteRejection reason) {
        rejections.get(reason).increment();
    }

    private static <E extends Enum<E>> Map<E, Counter> counters(MeterRegistry registry, Class<E> type, String name,
                                                                String tag, String description) {
        Map<E, Counter> counters = new EnumMap<>(type);
        Function<E, String> value = constant -> constant.name().toLowerCase(Locale.ROOT);
        for (E constant : type.getEnumConstants()) {
            counters.put(constant, Counter.builder(name).description(description).tag(tag, value.apply(constant))
                    .register(registry));
        }
        return counters;
    }
}
