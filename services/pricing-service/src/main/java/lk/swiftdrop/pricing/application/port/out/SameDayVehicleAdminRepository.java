package lk.swiftdrop.pricing.application.port.out;

import lk.swiftdrop.pricing.application.model.SameDayVehicleConfig;
import lk.swiftdrop.pricing.domain.model.VehicleType;

import java.util.List;
import java.util.Optional;

/** Maintains the same-day vehicles. The set of vehicle types is fixed; their limits are not. */
public interface SameDayVehicleAdminRepository {

    List<SameDayVehicleConfig> findAllVehicles();

    Optional<SameDayVehicleConfig> findByType(VehicleType type);

    SameDayVehicleConfig save(SameDayVehicleConfig vehicle);
}
