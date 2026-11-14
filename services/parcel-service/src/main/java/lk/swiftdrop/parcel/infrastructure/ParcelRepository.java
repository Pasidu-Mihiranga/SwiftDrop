package lk.swiftdrop.parcel.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.List;

public interface ParcelRepository extends JpaRepository<ParcelEntity, UUID> {
    List<ParcelEntity> findAllByCourierId(String courierId);
}
