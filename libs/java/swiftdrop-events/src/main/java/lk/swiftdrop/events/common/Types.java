package lk.swiftdrop.events.common;

/** Shared vocabulary used inside event payloads (see $defs in contracts/events/envelope.schema.json). */
public final class Types {

    private Types() {
    }

    public enum ParcelStatus {
        CREATED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, FAILED, CANCELLED;

        public boolean isTerminal() {
            return this == DELIVERED || this == CANCELLED;
        }
    }

    public enum FailureReason { RECIPIENT_UNAVAILABLE, WRONG_ADDRESS, REFUSED, DAMAGED, OTHER }

    public enum SizeClass { S, M, L, XL }

    public enum ServiceLevel { STANDARD, EXPRESS }

    public enum ParcelSource { CUSTOMER, MERCHANT }

    public enum ActorRole { CUSTOMER, MERCHANT, MERCHANT_API, COURIER, DISPATCHER, ADMIN, SYSTEM }

    /** Coarse location — full street addresses never travel on Kafka. */
    public record Location(String city, String district, String zoneCode) { }

    public record Money(java.math.BigDecimal amount, String currency) {
        public static Money lkr(java.math.BigDecimal amount) {
            return new Money(amount, "LKR");
        }
    }

    /** Who caused the change. */
    public record Actor(String userId, ActorRole role) {
        public static final Actor SYSTEM = new Actor("system", ActorRole.SYSTEM);
    }
}
