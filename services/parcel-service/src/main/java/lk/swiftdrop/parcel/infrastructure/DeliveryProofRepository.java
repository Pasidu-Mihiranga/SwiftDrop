package lk.swiftdrop.parcel.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface DeliveryProofRepository extends JpaRepository<DeliveryProofEntity, UUID> { }
