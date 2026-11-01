package lk.swiftdrop.pricing.infrastructure.persistence;

import lk.swiftdrop.pricing.application.port.out.InsuranceBandRepository;
import lk.swiftdrop.pricing.domain.model.InsuranceBand;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/** JPA adapter for the insurance bands. */
@Repository
class InsuranceBandRepositoryAdapter implements InsuranceBandRepository {

    private final InsuranceBandJpaRepository bands;

    InsuranceBandRepositoryAdapter(InsuranceBandJpaRepository bands) {
        this.bands = bands;
    }

    @Override
    public List<InsuranceBand> findInForce(LocalDate on) {
        return bands.findInForce(on).stream()
                .map(entity -> new InsuranceBand(entity.getUpToDeclaredValue(), entity.getFlatFee(),
                        entity.getPctOfDeclaredValue()))
                .toList();
    }
}
