package lk.swiftdrop.pricing.infrastructure.persistence;

import lk.swiftdrop.pricing.application.port.out.ZoneRepository;
import lk.swiftdrop.pricing.domain.model.Zone;
import lk.swiftdrop.pricing.domain.model.ZoneCode;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class ZoneRepositoryAdapter implements ZoneRepository {

    private final ZoneJpaRepository zones;

    ZoneRepositoryAdapter(ZoneJpaRepository zones) {
        this.zones = zones;
    }

    @Override
    public List<Zone> findActive() {
        return zones.findByActiveIsTrueOrderByCodeAsc().stream().map(PricingPersistenceMapper::toDomain).toList();
    }

    @Override
    public List<Zone> findAll() {
        return zones.findAllByOrderByCodeAsc().stream().map(PricingPersistenceMapper::toDomain).toList();
    }

    @Override
    public Optional<Zone> findById(UUID id) {
        return zones.findById(id).map(PricingPersistenceMapper::toDomain);
    }

    @Override
    public Optional<Zone> findByCode(ZoneCode code) {
        return zones.findByCode(code.value()).map(PricingPersistenceMapper::toDomain);
    }

    @Override
    public boolean existsByCode(ZoneCode code) {
        return zones.existsByCode(code.value());
    }

    @Override
    public Zone save(Zone zone) {
        ZoneEntity entity = zone.id() == null
                ? new ZoneEntity(UUID.randomUUID(), zone.code().value(), zone.name(), zone.active())
                : zones.findById(zone.id()).orElseThrow(
                        () -> new IllegalStateException("Zone " + zone.id() + " disappeared while being saved"));
        if (zone.id() != null) {
            entity.rename(zone.name(), zone.active());
        }
        return PricingPersistenceMapper.toDomain(zones.save(entity));
    }
}
