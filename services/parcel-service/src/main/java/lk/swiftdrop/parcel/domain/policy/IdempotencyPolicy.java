package lk.swiftdrop.parcel.domain.policy;

import lk.swiftdrop.parcel.domain.model.IdempotencyRecord;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

/**
 * Decides what a merchant's order request with an Idempotency-Key means (design figure 13):
 * a key not seen before is a NEW order, the same key with the same body is a REPLAY of the first
 * result, and the same key with a different body is a CONFLICT.
 */
public final class IdempotencyPolicy {

    public enum Decision { NEW, REPLAY, CONFLICT }

    private IdempotencyPolicy() {
    }

    public static Decision decide(Optional<IdempotencyRecord> existing, String requestHash) {
        if (existing.isEmpty()) {
            return Decision.NEW;
        }
        byte[] stored = existing.get().requestHash().getBytes(StandardCharsets.US_ASCII);
        byte[] incoming = requestHash.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(stored, incoming) ? Decision.REPLAY : Decision.CONFLICT;
    }
}
