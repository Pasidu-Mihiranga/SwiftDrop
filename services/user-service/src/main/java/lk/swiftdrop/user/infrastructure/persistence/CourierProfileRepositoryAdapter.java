package lk.swiftdrop.user.infrastructure.persistence;

import lk.swiftdrop.user.application.port.out.CourierProfileRepository;
import lk.swiftdrop.user.domain.model.CourierProfile;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
class CourierProfileRepositoryAdapter implements CourierProfileRepository {

    private final CourierProfileJpaRepository profiles;

    CourierProfileRepositoryAdapter(CourierProfileJpaRepository profiles) {
        this.profiles = profiles;
    }

    @Override
    public Map<String, CourierProfile> findByCourierIds(Collection<String> courierIds) {
        if (courierIds.isEmpty()) {
            return Map.of();
        }
        return profiles.findAllById(courierIds).stream()
                .map(CourierProfileRepositoryAdapter::toDomain)
                .collect(Collectors.toUnmodifiableMap(CourierProfile::courierId, Function.identity()));
    }

    @Override
    public CourierProfile save(CourierProfile profile) {
        CourierProfileEntity entity = profiles.findById(profile.courierId())
                .orElseGet(() -> new CourierProfileEntity(profile.courierId()));
        entity.apply(profile.vehicleType(), profile.homeZoneId(), profile.maxDailyCapacity(), profile.active());
        return toDomain(profiles.saveAndFlush(entity));
    }

    private static CourierProfile toDomain(CourierProfileEntity entity) {
        return new CourierProfile(entity.getUserId(), entity.getVehicleType(), entity.getHomeZoneId(),
                entity.getMaxDailyCapacity(), entity.isActive(), entity.getUpdatedAt());
    }
}
