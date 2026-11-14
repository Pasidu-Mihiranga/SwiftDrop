package lk.swiftdrop.user.application.port.out;

import lk.swiftdrop.user.domain.model.CourierProfile;

import java.util.Collection;
import java.util.Map;

/** Stores the dispatch details of couriers. */
public interface CourierProfileRepository {

    /** Profiles of the given couriers, keyed by courier id; couriers without one are simply absent. */
    Map<String, CourierProfile> findByCourierIds(Collection<String> courierIds);

    /** Inserts or replaces the courier's profile. The courier's user profile must already exist. */
    CourierProfile save(CourierProfile profile);
}
