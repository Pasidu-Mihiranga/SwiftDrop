package lk.swiftdrop.dispatch.domain;

public final class AllocationRules {
    private AllocationRules() { }
    public static boolean active(String status) {
        return !java.util.Set.of("DELIVERED", "FAILED", "CANCELLED").contains(status);
    }
    public static void validate(String status, String previousCourier, String courier) {
        if (!active(status)) throw new IllegalArgumentException("Completed or failed parcels cannot be allocated");
        if (courier.equals(previousCourier)) throw new IllegalArgumentException("Choose a different courier");
    }
}
