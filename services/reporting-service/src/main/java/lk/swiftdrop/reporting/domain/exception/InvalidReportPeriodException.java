package lk.swiftdrop.reporting.domain.exception;

/** A report was asked for a date range that is reversed or too long. */
public class InvalidReportPeriodException extends RuntimeException {

    public InvalidReportPeriodException(String message) {
        super(message);
    }
}
