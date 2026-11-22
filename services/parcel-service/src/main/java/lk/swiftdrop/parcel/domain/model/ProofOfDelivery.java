package lk.swiftdrop.parcel.domain.model;

/**
 * What a courier hands in to prove a delivery: the recipient's name, or an uploaded photo or
 * signature (by its proof id).
 */
public record ProofOfDelivery(Type type, String receivedBy, String proofId) {

    public enum Type { NAME, PHOTO, SIGNATURE }

    /** Whether this proof is complete for its type. */
    public boolean isComplete() {
        return switch (type) {
            case NAME -> receivedBy != null && !receivedBy.isBlank();
            case PHOTO, SIGNATURE -> proofId != null && !proofId.isBlank();
        };
    }
}
