package lk.swiftdrop.pricing.domain.model;

/** How quickly a parcel is to be delivered, and therefore how it is priced. */
public enum ServiceLevel {

    /** Priced by zone, delivered on the normal intercity schedule. */
    STANDARD,

    /** Priced by zone with a multiplier, moved ahead of standard traffic. */
    EXPRESS,

    /**
     * Priced by road distance rather than by zone, collected and delivered the same day.
     *
     * <p>Zones are too coarse for local work: a run across a city and a run to the far side of
     * the district would otherwise be the same lane at the same price.
     */
    SAME_DAY
}
