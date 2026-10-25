package lk.swiftdrop.pricing.application.query;

import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.pricing.application.port.out.DistrictAssignmentRepository;
import lk.swiftdrop.pricing.application.port.out.SizeClassRepository;
import lk.swiftdrop.pricing.domain.model.SizeClassRate;
import lk.swiftdrop.pricing.domain.model.ZoneCode;
import lk.swiftdrop.pricing.domain.policy.FeeCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;

/** Resolves configured coverage and size limits before calculating a fee. */
@Service
@Transactional(readOnly = true)
public class QuotePricingService {

    private final DistrictAssignmentRepository districts;
    private final SizeClassRepository sizeClasses;
    private final RateCardSelectionService rateCards;
    private final FeeCalculator calculator = new FeeCalculator();

    public QuotePricingService(DistrictAssignmentRepository districts, SizeClassRepository sizeClasses,
                               RateCardSelectionService rateCards) {
        this.districts = districts;
        this.sizeClasses = sizeClasses;
        this.rateCards = rateCards;
    }

    public PreparedQuote prepare(QuotePricingInput input, LocalDate quoteDate) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(quoteDate, "quoteDate");
        ZoneCode fromZone = activeZone(input.fromDistrict());
        ZoneCode toZone = activeZone(input.toDistrict());
        SizeClassRate sizeClass = sizeClasses.findByCode(input.sizeClass())
                .orElseThrow(() -> new IllegalStateException("Size class " + input.sizeClass() + " is not configured"));
        if (!sizeClass.accepts(input.weightKg())) {
            throw new BusinessRuleViolationException("SIZE_CLASS_WEIGHT_EXCEEDED",
                    "Weight exceeds the configured limit for size class " + input.sizeClass());
        }
        return new PreparedQuote(input, fromZone, toZone,
                calculator.calculate(rateCards.select(fromZone, toZone, quoteDate), sizeClass,
                        input.weightKg(), input.serviceLevel(), input.codAmount()));
    }

    private ZoneCode activeZone(String district) {
        return districts.findActiveZoneCodeByDistrict(district)
                .orElseThrow(() -> new BusinessRuleViolationException("DISTRICT_NOT_SERVICED",
                        "District is not served by an active zone"));
    }
}
