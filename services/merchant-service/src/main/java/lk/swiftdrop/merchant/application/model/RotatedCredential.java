package lk.swiftdrop.merchant.application.model;

import java.util.Objects;

/**
 * A merchant's client id with its new secret, held only long enough to answer the request that
 * rotated it. {@link #toString()} masks the secret so it cannot reach a log by accident.
 */
public record RotatedCredential(String clientId, String clientSecret) {

    public RotatedCredential {
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(clientSecret, "clientSecret");
    }

    @Override
    public String toString() {
        return "RotatedCredential[clientId=" + clientId + ", clientSecret=****]";
    }
}
