package lk.swiftdrop.pricing.infrastructure.persistence;

import lk.swiftdrop.pricing.application.port.out.DistrictAssignmentRepository;
import lk.swiftdrop.pricing.domain.model.DistrictAssignment;
import lk.swiftdrop.pricing.domain.model.ZoneCode;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class DistrictAssignmentRepositoryAdapter implements DistrictAssignmentRepository {

    private final DistrictZoneJpaRepository districts;

    DistrictAssignmentRepositoryAdapter(DistrictZoneJpaRepository districts) {
        this.districts = districts;
    }

    @Override
    public List<DistrictAssignment> findAll() {
        return districts.findAllByOrderByDistrictAsc().stream()
                .map(PricingPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<DistrictAssignment> findByDistrict(String district) {
        return districts.findByDistrictIgnoreCase(district.strip()).map(PricingPersistenceMapper::toDomain);
    }

    @Override
    public Optional<ZoneCode> findActiveZoneCodeByDistrict(String district) {
        return districts.findByDistrictIgnoreCase(district.strip())
                .map(DistrictZoneEntity::getZone)
                .filter(ZoneEntity::isActive)
                .map(zone -> ZoneCode.of(zone.getCode()));
    }
}
