package lk.swiftdrop.pricing.infrastructure.persistence;

import lk.swiftdrop.pricing.application.model.Paged;
import lk.swiftdrop.pricing.application.model.RateCardFilter;
import lk.swiftdrop.pricing.application.port.out.RateCardRepository;
import lk.swiftdrop.pricing.domain.model.RateCard;
import lk.swiftdrop.pricing.domain.model.ZoneCode;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
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

    /** Lanes read left to right, and within a lane the newest rates come first. */
    private static final Sort ORDER = Sort.by(
            Sort.Order.asc("fromZone.code"), Sort.Order.asc("toZone.code"), Sort.Order.desc("effectiveFrom"));

    @Override
    public Paged<RateCard> findPage(RateCardFilter filter, int page, int size) {
        Page<RateCardEntity> found = rateCards.findAll(matching(filter), PageRequest.of(page, size, ORDER));
        return new Paged<>(found.getContent().stream().map(PricingPersistenceMapper::toDomain).toList(),
                page, size, found.getTotalElements());
    }

    /**
     * Only the filters that were actually supplied become predicates.
     *
     * <p>Expressing the optional filters as a specification rather than as
     * {@code (:param IS NULL OR ...)} in JPQL matters on Postgres: a parameter that appears only
     * inside an IS NULL test has no inferable type, and the driver rejects the statement.
     */
    private static Specification<RateCardEntity> matching(RateCardFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.fromZone() != null) {
                predicates.add(builder.equal(root.get("fromZone").get("code"), filter.fromZone().value()));
            }
            if (filter.toZone() != null) {
                predicates.add(builder.equal(root.get("toZone").get("code"), filter.toZone().value()));
            }
            if (filter.activeOn() != null) {
                LocalDate on = filter.activeOn();
                predicates.add(builder.lessThanOrEqualTo(root.get("effectiveFrom"), on));
                // effectiveTo is an exclusive bound, so a card ending on this date no longer applies.
                predicates.add(builder.or(
                        builder.isNull(root.get("effectiveTo")),
                        builder.greaterThan(root.get("effectiveTo"), on)));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    @Override
    public List<RateCard> findByPair(ZoneCode fromZone, ZoneCode toZone) {
        return rateCards.findByPair(fromZone.value(), toZone.value()).stream()
                .map(PricingPersistenceMapper::toDomain)
                .toList();
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
