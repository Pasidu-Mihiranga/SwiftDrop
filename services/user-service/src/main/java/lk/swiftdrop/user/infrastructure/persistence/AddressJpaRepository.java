package lk.swiftdrop.user.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AddressJpaRepository extends JpaRepository<AddressEntity, UUID> {

    List<AddressEntity> findByUserIdOrderByIsDefaultDescCreatedAtAscIdAsc(String userId);

    Optional<AddressEntity> findByIdAndUserId(UUID id, String userId);

    /** Runs before a new default is written, so the one-default index is never violated mid-change. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update AddressEntity a set a.isDefault = false, a.updatedAt = :now where a.userId = :userId and a.isDefault = true")
    int clearDefault(@Param("userId") String userId, @Param("now") Instant now);
}
