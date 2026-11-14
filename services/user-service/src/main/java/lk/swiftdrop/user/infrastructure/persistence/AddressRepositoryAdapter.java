package lk.swiftdrop.user.infrastructure.persistence;

import lk.swiftdrop.common.web.error.ConflictException;
import lk.swiftdrop.user.application.port.out.AddressRepository;
import lk.swiftdrop.user.domain.model.Address;
import lk.swiftdrop.user.domain.model.AddressDetails;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class AddressRepositoryAdapter implements AddressRepository {

    private final AddressJpaRepository addresses;

    AddressRepositoryAdapter(AddressJpaRepository addresses) {
        this.addresses = addresses;
    }

    @Override
    public List<Address> findByOwner(String ownerId) {
        return addresses.findByUserIdOrderByIsDefaultDescCreatedAtAscIdAsc(ownerId).stream()
                .map(AddressRepositoryAdapter::toDomain).toList();
    }

    @Override
    public Optional<Address> findOwned(UUID addressId, String ownerId) {
        return addresses.findByIdAndUserId(addressId, ownerId).map(AddressRepositoryAdapter::toDomain);
    }

    @Override
    public Address save(Address address) {
        AddressEntity entity = addresses.findById(address.id())
                .orElseGet(() -> new AddressEntity(address.id(), address.ownerId()));
        AddressDetails details = address.details();
        entity.apply(details.label(), details.line1(), details.line2(), details.city(), details.district(),
                details.postalCode(), details.lat(), details.lng(), address.isDefault());
        try {
            return toDomain(addresses.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            // Two changes to the same address book at once both tried to set a default.
            throw new ConflictException("ADDRESS_BOOK_CHANGED",
                    "Your address book was changed at the same time; reload it and try again");
        }
    }

    @Override
    public void clearDefault(String ownerId) {
        addresses.clearDefault(ownerId, Instant.now());
    }

    @Override
    public void delete(Address address) {
        addresses.findByIdAndUserId(address.id(), address.ownerId()).ifPresent(entity -> {
            addresses.delete(entity);
            addresses.flush();
        });
    }

    private static Address toDomain(AddressEntity entity) {
        return new Address(entity.getId(), entity.getUserId(),
                new AddressDetails(entity.getLabel(), entity.getLine1(), entity.getLine2(), entity.getCity(),
                        entity.getDistrict(), entity.getPostalCode(), entity.getLat(), entity.getLng()),
                entity.isDefault(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
