package lk.swiftdrop.dispatch.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class DispatchModels {
    private DispatchModels() { }
    public record Parcel(UUID parcelId, String trackingNumber, String zone, String sizeClass,
                         String pickupCity, String dropoffCity, Instant createdAt, String status,
                         UUID assignmentId, String courierId, LocalDate runDate) { }
    public record Page(List<Parcel> content, long totalElements, int page, int size) { }
    public record Courier(String id, String fullName, String homeZoneId, Integer maxDailyCapacity,
                          boolean assignable) { }
    public record Workload(String courierId, String courierName, String homeZoneId, Integer capacity,
                           boolean assignable, long activeCount, long deliveredToday, long failedToday) { }
    public record Counts(long activeCount, long deliveredToday, long failedToday) { }
    public record Allocation(UUID id, UUID parcelId, String courierId, String previousCourierId,
                             LocalDate runDate, Instant assignedAt) { }
}
