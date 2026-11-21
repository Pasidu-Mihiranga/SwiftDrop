package lk.swiftdrop.pricing.application.port.out;

import lk.swiftdrop.pricing.domain.model.PricingSettings;

import java.time.LocalDate;
import java.util.Optional;

/** Reads the fee-wide settings (fuel, tax, minimum charge) in force on a date. */
public interface PricingSettingsRepository {

    /** Empty when none is configured, in which case none of the three rules applies. */
    Optional<PricingSettings> findInForce(LocalDate on);
}
