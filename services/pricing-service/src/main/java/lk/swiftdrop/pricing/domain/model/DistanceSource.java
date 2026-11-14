package lk.swiftdrop.pricing.domain.model;

/** Where a lane's distance came from, so a quote priced on a guess can be told apart. */
public enum DistanceSource {

    /** Measured along the road network by a routing engine. */
    ROUTED,

    /** A straight line scaled by the configured road factor, because no router answered. */
    ESTIMATED
}
