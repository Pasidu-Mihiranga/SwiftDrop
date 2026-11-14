package lk.swiftdrop.pricing.infrastructure.persistence;

import lk.swiftdrop.pricing.application.port.out.RemoteAreaSurchargeRepository;
import lk.swiftdrop.pricing.domain.model.RemoteAreaSurcharge;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JPA adapter for the remote-area surcharge. */
@Repository
class RemoteAreaSurchargeRepositoryAdapter implements RemoteAreaSurchargeRepository {

    private final RemoteAreaSurchargeJpaRepository surcharges;

    RemoteAreaSurchargeRepositoryAdapter(RemoteAreaSurchargeJpaRepository surcharges) {
        this.surcharges = surcharges;
    }

    @Override
    public Optional<RemoteAreaSurcharge> findInForce(String district, LocalDate on) {
        if (district == null || district.isBlank()) {
            return Optional.empty();
        }
        List<RemoteAreaSurchargeEntity> inForce = surcharges.findInForce(district.strip(), on);
        // A unique constraint on (district, effective_from) means at most one row can start on
        // a given day, so the newest in force is unambiguous.
        return inForce.isEmpty() ? Optional.empty() : Optional.of(toDomain(inForce.get(0)));
    }

    @Override
    public List<RemoteAreaSurcharge> findAll() {
        return surcharges.findAllByOrderByDistrictAscEffectiveFromDesc().stream()
                .map(RemoteAreaSurchargeRepositoryAdapter::toDomain).toList();
    }

    @Override
    public boolean hasPeriodStartingOnOrAfter(String district, LocalDate date) {
        return surcharges.existsByDistrictIgnoreCaseAndEffectiveFromGreaterThanEqual(district.strip(), date);
    }

    @Override
    @Transactional
    public RemoteAreaSurcharge publish(String district, BigDecimal flatFee, LocalDate effectiveFrom) {
        String trimmed = district.strip();
        // Close whatever is in force on the day the new charge starts, so the periods meet
        // without overlapping and the old row survives for an already-issued quote to cite.
        surcharges.findInForce(trimmed, effectiveFrom)
                .forEach(existing -> existing.closeOn(effectiveFrom));
        RemoteAreaSurchargeEntity saved = surcharges.save(
                new RemoteAreaSurchargeEntity(UUID.randomUUID(), trimmed, flatFee, effectiveFrom));
        return toDomain(saved);
    }

    private static RemoteAreaSurcharge toDomain(RemoteAreaSurchargeEntity entity) {
        return new RemoteAreaSurcharge(entity.getDistrict(), entity.getFlatFee(),
                entity.getEffectiveFrom(), entity.getEffectiveTo());
    }
}
