package lk.swiftdrop.reporting.domain.model;

/** Totals for one dropoff zone; {@link #UNKNOWN_ZONE} holds parcels whose ParcelCreated has not arrived yet. */
public record ZoneCounts(String zoneCode, DeliveryCounts counts) {

    public static final String UNKNOWN_ZONE = "UNKNOWN";
}
