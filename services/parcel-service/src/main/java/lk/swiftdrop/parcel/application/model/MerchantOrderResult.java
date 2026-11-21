package lk.swiftdrop.parcel.application.model;

import lk.swiftdrop.parcel.domain.model.Parcel;

/**
 * The parcel a merchant order request resolved to.
 *
 * @param replayed true when the request repeated an earlier one with the same Idempotency-Key, so
 *                 nothing new was created (the API answers 200 instead of 201)
 */
public record MerchantOrderResult(Parcel parcel, boolean replayed) {
}
