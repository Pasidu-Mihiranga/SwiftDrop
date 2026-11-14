package lk.swiftdrop.pricing.application.command;

import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.common.web.error.ConflictException;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.pricing.application.port.out.MerchantAgreementRepository;
import lk.swiftdrop.pricing.application.port.out.ZoneRepository;
import lk.swiftdrop.pricing.domain.model.LaneOverride;
import lk.swiftdrop.pricing.domain.model.MerchantAgreement;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Maintains the terms merchants ship under.
 *
 * <p>Terms are versioned like every other price: new ones can only start after the newest
 * period for that merchant, and the row they replace is kept so a quote issued under it can
 * still be explained.
 */
@Service
public class MerchantAgreementAdminService {

    private final MerchantAgreementRepository agreements;
    private final ZoneRepository zones;
    private final Clock clock;

    public MerchantAgreementAdminService(MerchantAgreementRepository agreements, ZoneRepository zones,
                                         Clock clock) {
        this.agreements = agreements;
        this.zones = zones;
        this.clock = clock;
    }

    /** Merchant identities live in merchant-service, so this starts from who has terms today. */
    @Transactional(readOnly = true)
    public List<MerchantAgreement> listInForce() {
        return agreements.findAllInForce(LocalDate.now(clock));
    }

    @Transactional(readOnly = true)
    public List<MerchantAgreement> history(UUID merchantId) {
        return agreements.findByMerchant(merchantId);
    }

    @Transactional
    public MerchantAgreement publish(MerchantAgreement terms, LocalDate effectiveFrom) {
        Set<String> lanes = new HashSet<>();
        for (LaneOverride lane : terms.laneOverrides()) {
            requireZone(lane);
            if (!lanes.add(lane.fromZone() + ">" + lane.toZone())) {
                throw new BusinessRuleViolationException("DUPLICATE_LANE_OVERRIDE",
                        "Lane " + lane.fromZone() + " to " + lane.toZone() + " is listed twice");
            }
        }
        if (agreements.hasPeriodStartingOnOrAfter(terms.merchantId(), effectiveFrom)) {
            throw new ConflictException("AGREEMENT_PERIOD_CONFLICT",
                    "Terms for this merchant already start on or after " + effectiveFrom
                            + ". Publish from a later date.");
        }
        return agreements.publish(terms, effectiveFrom);
    }

    private void requireZone(LaneOverride lane) {
        if (!zones.existsByCode(lane.fromZone())) {
            throw ResourceNotFoundException.of("Zone", lane.fromZone());
        }
        if (!zones.existsByCode(lane.toZone())) {
            throw ResourceNotFoundException.of("Zone", lane.toZone());
        }
    }
}
