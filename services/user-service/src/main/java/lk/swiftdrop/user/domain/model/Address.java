package lk.swiftdrop.user.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * One saved address in a person's address book.
 *
 * @param id        random id; never reveals how many addresses exist
 * @param ownerId   Keycloak subject of the person who saved it
 * @param details   what the person typed
 * @param isDefault whether booking forms pre-select it (at most one per person)
 * @param createdAt {@code null} before it is stored
 * @param updatedAt {@code null} before it is stored
 */
public record Address(UUID id, String ownerId, AddressDetails details, boolean isDefault, Instant createdAt,
                      Instant updatedAt) {

    /** A person may keep this many addresses; more is almost certainly abuse or a bug. */
    public static final int MAX_PER_PERSON = 10;

    public Address {
        if (id == null) {
            throw new IllegalArgumentException("Address id is required");
        }
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("Address owner is required");
        }
        if (details == null) {
            throw new IllegalArgumentException("Address details are required");
        }
    }

    public Address withDetails(AddressDetails newDetails) {
        return new Address(id, ownerId, newDetails, isDefault, createdAt, updatedAt);
    }

    public Address asDefault(boolean makeDefault) {
        return new Address(id, ownerId, details, makeDefault, createdAt, updatedAt);
    }
}
