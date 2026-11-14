package lk.swiftdrop.user.application.command;

import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.user.application.model.IdentityClaims;
import lk.swiftdrop.user.application.port.out.AddressRepository;
import lk.swiftdrop.user.domain.model.Address;
import lk.swiftdrop.user.domain.model.AddressDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The caller's own address book. Rules: at most {@value Address#MAX_PER_PERSON} addresses, and while
 * any exist exactly one of them is the default (the first one saved becomes it automatically, and
 * deleting the default promotes the oldest remaining address).
 *
 * <p>Another person's address id behaves exactly like an unknown one (404), so ids reveal nothing.
 */
@Service
@Transactional
public class AddressBookService {

    private final AddressRepository addresses;
    private final UserProfileService profiles;

    public AddressBookService(AddressRepository addresses, UserProfileService profiles) {
        this.addresses = addresses;
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public List<Address> list(String ownerId) {
        return addresses.findByOwner(ownerId);
    }

    /** Saves a new address. The caller's profile is created first if this is their very first request. */
    public Address add(IdentityClaims caller, AddressDetails details, boolean makeDefault) {
        profiles.getOrCreate(caller);
        List<Address> existing = addresses.findByOwner(caller.subject());
        if (existing.size() >= Address.MAX_PER_PERSON) {
            throw new BusinessRuleViolationException("ADDRESS_LIMIT_REACHED",
                    "An address book holds at most " + Address.MAX_PER_PERSON + " addresses; delete one first");
        }
        boolean isDefault = existing.isEmpty() || makeDefault;
        if (isDefault && !existing.isEmpty()) {
            addresses.clearDefault(caller.subject());
        }
        return addresses.save(new Address(UUID.randomUUID(), caller.subject(), details, isDefault, null, null));
    }

    /**
     * Replaces the address. {@code makeDefault == true} moves the default here; there is no way to
     * leave the book without a default, so {@code false} never removes it.
     */
    public Address update(String ownerId, UUID addressId, AddressDetails details, boolean makeDefault) {
        Address current = owned(ownerId, addressId);
        if (makeDefault && !current.isDefault()) {
            addresses.clearDefault(ownerId);
        }
        return addresses.save(current.withDetails(details).asDefault(current.isDefault() || makeDefault));
    }

    public void delete(String ownerId, UUID addressId) {
        Address current = owned(ownerId, addressId);
        addresses.delete(current);
        if (current.isDefault()) {
            addresses.findByOwner(ownerId).stream().findFirst()
                    .ifPresent(oldest -> addresses.save(oldest.asDefault(true)));
        }
    }

    private Address owned(String ownerId, UUID addressId) {
        return addresses.findOwned(addressId, ownerId).orElseThrow(() -> ResourceNotFoundException.of("Address", addressId));
    }
}
