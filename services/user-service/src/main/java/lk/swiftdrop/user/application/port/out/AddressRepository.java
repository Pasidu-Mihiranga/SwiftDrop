package lk.swiftdrop.user.application.port.out;

import lk.swiftdrop.user.domain.model.Address;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Stores address books. Every read is scoped to an owner, so one person can never load another's address. */
public interface AddressRepository {

    /** The owner's addresses: the default first, then oldest first. */
    List<Address> findByOwner(String ownerId);

    /** The address only if it belongs to this owner. */
    Optional<Address> findOwned(UUID addressId, String ownerId);

    /** Inserts a new address or updates an existing one. */
    Address save(Address address);

    /** Removes the default flag from the owner's current default, if any. */
    void clearDefault(String ownerId);

    void delete(Address address);
}
