package lk.swiftdrop.parcel.domain.model;

/** A merchant's lifecycle status as merchant-service publishes it. Only APPROVED may place orders. */
public enum MerchantStatus { PENDING, APPROVED, REJECTED, SUSPENDED }
