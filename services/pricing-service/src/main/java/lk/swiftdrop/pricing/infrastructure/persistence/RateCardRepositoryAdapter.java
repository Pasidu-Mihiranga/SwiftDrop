package lk.swiftdrop.pricing.infrastructure.persistence;

import lk.swiftdrop.pricing.application.port.out.RateCardRepository;
import lk.swiftdrop.pricing.domain.model.RateCard;
import lk.swiftdrop.pricing.domain.model.ZoneCode;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class RateCardRepositoryAdapter implements RateCardRepository {

    private final RateCardJpaRepository rateCards;
    private final ZoneJpaRepository zones;

    RateCardRepositoryAdapter(RateCardJpaRepository rateCards, ZoneJpaRepository zones) {
        this.rateCards = rateCards;
        this.zones = zones;
    }

    @Override
    public List<RateCard> findInForce(ZoneCode fromZone, ZoneCode toZone, LocalDate on) {
        return rateCards.findInForce(fromZone.value(), toZone.value(), on).stream()
                .map(PricingPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public List<RateCard> findAll() {
        return rateCards.findAllOrdered().stream().map(PricingPersistenceMapper::toDomain).toList();
    }

    @Override
    public Optional<RateCard> findById(UUID id) {
        return rateCards.findById(id).map(PricingPersistenceMapper::toDomain);
    }

    @Override
    public RateCard save(RateCard rateCard) {
        if (rateCard.id() != null) {
            RateCardEntity existing = rateCards.findById(rateCard.id()).orElseThrow(
                    () -> new IllegalStateException("Rate card " + rateCard.id() + " disappeared while being saved"));
            existing.closeOn(rateCard.effectiveTo());
            return PricingPersistenceMapper.toDomain(rateCards.save(existing));
        }
        RateCardEntity entity = new RateCardEntity(UUID.randomUUID(),
                zone(rateCard.fromZone()), zone(rateCard.toZone()),
                rateCard.baseFee(), rateCard.perKgFee(), rateCard.expressMultiplier(), rateCard.codFeePct(),
                rateCard.effectiveFrom(), rateCard.effectiveTo());
        return PricingPersistenceMapper.toDomain(rateCards.save(entity));
    }

    private ZoneEntity zone(ZoneCode code) {
        return zones.findByCode(code.value()).orElseThrow(
                () -> new IllegalArgumentException("Zone " + code + " does not exist"));
    }
}
