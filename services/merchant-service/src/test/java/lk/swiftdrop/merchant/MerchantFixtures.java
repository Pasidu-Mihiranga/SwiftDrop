package lk.swiftdrop.merchant;

import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.merchant.domain.model.BusinessDetails;
import lk.swiftdrop.merchant.domain.model.Merchant;
import lk.swiftdrop.merchant.domain.model.PickupAddress;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/** Shared test data. Example values are illustrative, not real businesses. */
public final class MerchantFixtures {

    public static final Instant NOW = Instant.parse("2026-11-19T08:00:00Z");
    public static final String OWNER = "user-lanka-books";
    public static final String ADMIN = "admin-1";

    private MerchantFixtures() {
    }

    public static BusinessDetails lankaBooks() {
        return new BusinessDetails("Lanka Books", "PV 12345", "lankabooks@example.com", "+94771234567",
                "https://lankabooks.example.com",
                new PickupAddress("12 Temple Road", null, "Kandy", "Kandy", "20000"));
    }

    public static Merchant pending() {
        return Merchant.register(OWNER, lankaBooks(), NOW);
    }

    /** The merchant as the repository returns it after a save: with a version. */
    public static Merchant stored(Merchant merchant) {
        long version = merchant.version() == null ? 0 : merchant.version() + 1;
        return new Merchant(merchant.id(), merchant.ownerUserId(), merchant.business(), merchant.status(),
                merchant.reviewedBy(), merchant.reviewedAt(), merchant.rejectionReason(), merchant.createdAt(),
                merchant.updatedAt(), version);
    }

    /** A signed-in user with no SwiftDrop role yet, as an applicant usually is. */
    public static AuthenticatedUser user(String id) {
        return new AuthenticatedUser(id, Optional.of(id + "@example.com"), Optional.of("Test User"), Set.of(),
                Optional.empty());
    }

    public static AuthenticatedUser admin(String id) {
        return new AuthenticatedUser(id, Optional.of(id + "@example.com"), Optional.of("Admin"), Set.of("ADMIN"),
                Optional.empty());
    }
}
