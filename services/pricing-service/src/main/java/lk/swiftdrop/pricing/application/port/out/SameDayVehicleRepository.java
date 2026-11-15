package lk.swiftdrop.pricing.application.port.out;

import lk.swiftdrop.pricing.domain.model.SameDayVehicle;
import lk.swiftdrop.pricing.domain.model.VehicleType;

import java.util.List;
import java.util.Optional;

/** Reads what each vehicle can take on a same-day job. */
public interface SameDayVehicleRepository {

    /** Empty when that vehicle is not offered. */
    Optional<SameDayVehicle> findActive(VehicleType type);

    /** Every vehicle currently offered. Empty means vehicle limits are not configured. */
    List<SameDayVehicle> findAllActive();
}
