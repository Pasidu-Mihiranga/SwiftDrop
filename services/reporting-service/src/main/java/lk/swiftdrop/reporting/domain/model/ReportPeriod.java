package lk.swiftdrop.reporting.domain.model;

import lk.swiftdrop.reporting.domain.exception.InvalidReportPeriodException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * An inclusive range of business days. Capped at a year so a single request cannot ask the
 * database to scan the whole history.
 */
public record ReportPeriod(LocalDate from, LocalDate to) {

    public static final int MAX_DAYS = 366;

    public ReportPeriod {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new InvalidReportPeriodException("'from' must be on or before 'to'.");
        }
        if (length(from, to) > MAX_DAYS) {
            throw new InvalidReportPeriodException("A report can cover at most " + MAX_DAYS + " days.");
        }
    }

    /** Every day in the period, in order; used to zero-fill chart series. */
    public List<LocalDate> days() {
        return from.datesUntil(to.plusDays(1)).toList();
    }

    private static long length(LocalDate from, LocalDate to) {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }
}
