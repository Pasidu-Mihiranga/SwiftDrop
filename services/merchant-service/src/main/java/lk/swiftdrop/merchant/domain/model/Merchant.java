package lk.swiftdrop.merchant.domain.model;

import lk.swiftdrop.merchant.domain.exception.InvalidMerchantTransitionException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The merchant aggregate: a business application and its lifecycle (State pattern).
 *
 * <p>Immutable. Each admin decision returns a new merchant, and only the moves allowed by
 * {@link MerchantStatus#canMoveTo} succeed; every other move throws
 * {@link InvalidMerchantTransitionException}.
 *
 * @param ownerUserId     Keycloak subject of the user who applied
 * @param reviewedBy      the admin who made the latest decision; null while PENDING
 * @param reviewedAt      when that decision was made; null while PENDING
 * @param rejectionReason set only when REJECTED
 * @param version         optimistic-lock version; null until the merchant has been stored
 */
public record Merchant(UUID id, String ownerUserId, BusinessDetails business, MerchantStatus status,
                       String reviewedBy, Instant reviewedAt, String rejectionReason, Instant createdAt,
                       Instant updatedAt, Long version) {

    /** Same limit as the merchant-service contract and the merchant.rejection_reason column. */
    public static final int MAX_REASON_LENGTH = 500;

    public Merchant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerUserId, "ownerUserId");
        Objects.requireNonNull(business, "business");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** A new application from a signed-in user, waiting for an admin in status PENDING. */
    public static Merchant register(String ownerUserId, BusinessDetails business, Instant now) {
        return new Merchant(UUID.randomUUID(), ownerUserId, business, MerchantStatus.PENDING, null, null, null,
                now, now, null);
    }

    public boolean isApproved() {
        return status == MerchantStatus.APPROVED;
    }

    /** An admin accepts the application. Only a PENDING merchant can be approved. */
    public Merchant approve(String adminId, Instant now) {
        return decide(MerchantStatus.APPROVED, adminId, null, now);
    }

    /**
     * An admin turns the application down. Only a PENDING merchant can be rejected, and the
     * reason is kept so the applicant can see it.
     *
     * @throws IllegalArgumentException when the reason is blank or longer than {@value #MAX_REASON_LENGTH}
     */
    public Merchant reject(String adminId, String reason, Instant now) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A rejection needs a reason");
        }
        String stripped = reason.strip();
        if (stripped.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("A rejection reason is at most " + MAX_REASON_LENGTH + " characters");
        }
        return decide(MerchantStatus.REJECTED, adminId, stripped, now);
    }

    /** An admin stops an approved merchant: its API orders are refused and its webhooks pause. */
    public Merchant suspend(String adminId, Instant now) {
        return decide(MerchantStatus.SUSPENDED, adminId, null, now);
    }

    private Merchant decide(MerchantStatus target, String adminId, String reason, Instant now) {
        Objects.requireNonNull(adminId, "adminId");
        Objects.requireNonNull(now, "now");
        if (!status.canMoveTo(target)) {
            throw new InvalidMerchantTransitionException(status, target);
        }
        return new Merchant(id, ownerUserId, business, target, adminId, now, reason, createdAt, now, version);
    }
}
