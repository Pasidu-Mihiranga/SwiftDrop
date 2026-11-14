package lk.swiftdrop.user.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface CourierProfileJpaRepository extends JpaRepository<CourierProfileEntity, String> {
}
