package lk.swiftdrop.reporting.domain.model;

/**
 * What happened in a slice of time: parcels created, and transitions to DELIVERED, FAILED and
 * CANCELLED. A parcel can fail more than once, so {@code failed} counts attempts, not parcels.
 */
public record DeliveryCounts(long created, long delivered, long failed, long cancelled) {

    public static final DeliveryCounts ZERO = new DeliveryCounts(0, 0, 0, 0);

    public DeliveryCounts plus(DeliveryCounts other) {
        return new DeliveryCounts(created + other.created, delivered + other.delivered,
                failed + other.failed, cancelled + other.cancelled);
    }

    public SuccessRate successRate() {
        return new SuccessRate(delivered, failed);
    }
}
