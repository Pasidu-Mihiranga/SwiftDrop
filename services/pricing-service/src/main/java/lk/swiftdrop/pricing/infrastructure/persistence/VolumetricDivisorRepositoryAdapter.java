package lk.swiftdrop.pricing.infrastructure.persistence;

import lk.swiftdrop.pricing.application.port.out.VolumetricDivisorRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** JPA adapter for the volumetric divisor. */
@Repository
class VolumetricDivisorRepositoryAdapter implements VolumetricDivisorRepository {

    private final VolumetricDivisorJpaRepository divisors;

    VolumetricDivisorRepositoryAdapter(VolumetricDivisorJpaRepository divisors) {
        this.divisors = divisors;
    }

    @Override
    public Optional<BigDecimal> findInForce(LocalDate on) {
        List<VolumetricDivisorEntity> inForce = divisors.findInForce(on);
        // A unique constraint on effective_from means at most one row can start on a given
        // day, so the newest in force is unambiguous.
        return inForce.isEmpty() ? Optional.empty() : Optional.of(inForce.get(0).getDivisor());
    }
}
