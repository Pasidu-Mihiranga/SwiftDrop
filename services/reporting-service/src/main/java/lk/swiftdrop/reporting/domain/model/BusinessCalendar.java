package lk.swiftdrop.reporting.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Decides which day an event belongs to. Reports follow the operator's local day, not UTC: a
 * parcel delivered at 01:00 on the 21st in Colombo happened at 19:30 on the 20th in UTC, and must
 * still count for the 21st.
 */
public record BusinessCalendar(ZoneId zone) {

    public BusinessCalendar {
        Objects.requireNonNull(zone, "zone");
    }

    public LocalDate dateOf(Instant instant) {
        return LocalDate.ofInstant(instant, zone);
    }
}
