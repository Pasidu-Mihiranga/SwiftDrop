package lk.swiftdrop.pricing.infrastructure.persistence;

import lk.swiftdrop.pricing.application.port.out.MerchantAgreementRepository;
import lk.swiftdrop.pricing.domain.model.LaneOverride;
import lk.swiftdrop.pricing.domain.model.MerchantAgreement;
import lk.swiftdrop.pricing.domain.model.ZoneCode;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JPA adapter for merchant agreements and their lane overrides. */
@Repository
class MerchantAgreementRepositoryAdapter implements MerchantAgreementRepository {

    private final MerchantAgreementJpaRepository agreements;
    private final ZoneJpaRepository zones;

    MerchantAgreementRepositoryAdapter(MerchantAgreementJpaRepository agreements, ZoneJpaRepository zones) {
        this.agreements = agreements;
        this.zones = zones;
    }

    @Override
    public Optional<MerchantAgreement> findInForce(UUID merchantId, LocalDate on) {
        if (merchantId == null) {
            return Optional.empty();
        }
        List<MerchantAgreementEntity> inForce = agreements.findInForce(merchantId, on);
        // A unique constraint on (merchant_id, effective_from) means at most one row can start
        // on a given day, so the newest in force is unambiguous.
        return inForce.isEmpty() ? Optional.empty() : Optional.of(toDomain(inForce.get(0)));
    }

    @Override
    public List<MerchantAgreement> findByMerchant(UUID merchantId) {
        return agreements.findByMerchantIdOrderByEffectiveFromDesc(merchantId).stream()
                .map(MerchantAgreementRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public List<MerchantAgreement> findAllInForce(LocalDate on) {
        return agreements.findAllInForce(on).stream()
                .map(MerchantAgreementRepositoryAdapter::toDomain)
                .toList();
    }

    @Override
    public boolean hasPeriodStartingOnOrAfter(UUID merchantId, LocalDate date) {
        return agreements.existsByMerchantIdAndEffectiveFromGreaterThanEqual(merchantId, date);
    }

    @Override
    @Transactional
    public MerchantAgreement publish(MerchantAgreement agreement, LocalDate effectiveFrom) {
        agreements.findInForce(agreement.merchantId(), effectiveFrom)
                .forEach(existing -> existing.closeOn(effectiveFrom));
        MerchantAgreementEntity entity = new MerchantAgreementEntity(UUID.randomUUID(),
                agreement.merchantId(), agreement.discountPct(), agreement.discountFlat(),
                agreement.waivesRemoteArea(), agreement.waivesInsurance(), agreement.waivesCodFee(),
                effectiveFrom);
        for (LaneOverride override : agreement.laneOverrides()) {
            entity.addLaneOverride(new LaneOverrideEntity(UUID.randomUUID(),
                    zone(override.fromZone()), zone(override.toZone()),
                    override.discountPct(), override.flatBaseFee()));
        }
        return toDomain(agreements.save(entity));
    }

    private ZoneEntity zone(ZoneCode code) {
        return zones.findByCode(code.value()).orElseThrow(
                () -> new IllegalArgumentException("Zone " + code + " does not exist"));
    }

    private static MerchantAgreement toDomain(MerchantAgreementEntity entity) {
        List<LaneOverride> overrides = entity.getLaneOverrides().stream()
                .map(o -> new LaneOverride(ZoneCode.of(o.getFromZone().getCode()),
                        ZoneCode.of(o.getToZone().getCode()), o.getDiscountPct(), o.getFlatBaseFee()))
                .toList();
        return new MerchantAgreement(entity.getId(), entity.getMerchantId(), entity.getDiscountPct(),
                entity.getDiscountFlat(), entity.isWaivesRemoteArea(), entity.isWaivesInsurance(),
                entity.isWaivesCodFee(), overrides, entity.getEffectiveFrom(), entity.getEffectiveTo());
    }
}
