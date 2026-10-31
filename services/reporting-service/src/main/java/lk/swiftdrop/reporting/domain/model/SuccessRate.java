package lk.swiftdrop.reporting.domain.model;

/** Share of delivery attempts that ended in DELIVERED rather than FAILED. */
public record SuccessRate(long delivered, long failed) {

    /** @return a value between 0 and 1, or {@code null} when there were no attempts to judge */
    public Double rate() {
        long attempts = delivered + failed;
        return attempts == 0 ? null : (double) delivered / attempts;
    }
}
