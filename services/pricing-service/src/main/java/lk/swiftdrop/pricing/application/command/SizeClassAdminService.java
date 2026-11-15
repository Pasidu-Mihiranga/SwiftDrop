package lk.swiftdrop.pricing.application.command;

import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.pricing.application.port.out.SizeClassRepository;
import lk.swiftdrop.pricing.domain.model.Dimensions;
import lk.swiftdrop.pricing.domain.model.SizeClassCode;
import lk.swiftdrop.pricing.domain.model.SizeClassRate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** Updates only existing size-class configuration; class codes are fixed. */
@Service
public class SizeClassAdminService {

    private final SizeClassRepository sizeClasses;

    public SizeClassAdminService(SizeClassRepository sizeClasses) {
        this.sizeClasses = sizeClasses;
    }

    @Transactional(readOnly = true)
    public List<SizeClassRate> list() {
        return sizeClasses.findAll();
    }

    @Transactional
    public SizeClassRate update(SizeClassCode code, BigDecimal maxWeightKg,
                                Dimensions maxDimensions, BigDecimal multiplier) {
        SizeClassRate current = sizeClasses.findByCode(code)
                .orElseThrow(() -> ResourceNotFoundException.of("Size class", code));
        SizeClassRate updated = new SizeClassRate(code,
                maxWeightKg == null ? current.maxWeightKg() : maxWeightKg,
                maxDimensions == null ? current.maxDimensions() : maxDimensions,
                multiplier);
        return sizeClasses.save(updated);
    }
}
