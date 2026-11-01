package lk.swiftdrop.pricing.domain.model;

/** Which measure a parcel was billed on. */
public enum WeightBasis {

    /** The parcel is dense enough that what it weighs is what it costs. */
    ACTUAL,

    /** The parcel displaces more than it weighs, so it is billed on the space it takes up. */
    VOLUMETRIC
}
