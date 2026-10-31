package lk.swiftdrop.reporting.domain.model;

public record CourierPerformance(String courierId, long delivered, long failed) {

    public SuccessRate successRate() {
        return new SuccessRate(delivered, failed);
    }
}
