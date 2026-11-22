package lk.swiftdrop.dispatch;
import lk.swiftdrop.events.*;
import lk.swiftdrop.events.parcel.*;
import lk.swiftdrop.events.common.Types.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.UUID;
public final class DispatchFixtures {
    public static ParcelCreated parcel(UUID id) {
        return new ParcelCreated(id, "SD" + id.toString().substring(0,12), ParcelSource.CUSTOMER, "customer", null, null,
            new Location("Colombo", "Colombo", "WP"), new Location("Kandy", "Kandy", "CP"), SizeClass.M,
            BigDecimal.ONE, ServiceLevel.STANDARD, Money.lkr(BigDecimal.TEN), null, Instant.now().minusSeconds(120));
    }
    public static ParcelStatusChanged status(UUID id, ParcelStatus status, Instant at) {
        return new ParcelStatusChanged(id, "SD", "customer", null, "courier1", ParcelStatus.CREATED, status,
            null, null, null, null, Actor.SYSTEM, at);
    }
}
