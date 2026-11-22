package lk.swiftdrop.parcel.api.dto;

import java.time.Instant;

/** A short-lived link to view a proof-of-delivery file. */
public record ProofLinkResponse(String url, Instant expiresAt) {
}
