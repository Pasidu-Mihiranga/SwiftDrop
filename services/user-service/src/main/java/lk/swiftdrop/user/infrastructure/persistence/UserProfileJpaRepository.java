package lk.swiftdrop.user.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserProfileJpaRepository extends JpaRepository<UserProfileEntity, String> {

    /**
     * Native insert so that two simultaneous first requests cannot both fail: PostgreSQL waits for
     * the other insert and then skips this one. The phone is always empty at creation, so no
     * encrypted column is written here.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO user_profile (id, email, full_name, status)
            VALUES (:id, :email, :fullName, 'ACTIVE')
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") String id, @Param("email") String email, @Param("fullName") String fullName);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO user_role (user_id, role_code)
            VALUES (:userId, :roleCode)
            ON CONFLICT (user_id, role_code) DO NOTHING
            """, nativeQuery = true)
    int insertRoleIfAbsent(@Param("userId") String userId, @Param("roleCode") String roleCode);
}
