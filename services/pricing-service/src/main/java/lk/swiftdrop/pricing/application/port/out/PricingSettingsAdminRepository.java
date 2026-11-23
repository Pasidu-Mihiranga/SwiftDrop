package lk.swiftdrop.pricing.application.port.out;

import lk.swiftdrop.pricing.application.model.Versioned;
import lk.swiftdrop.pricing.domain.model.PricingSettings;

import java.time.LocalDate;
import java.util.List;

/** Maintains the versioned fee-wide settings. */
public interface PricingSettingsAdminRepository {

    /** Every version ever published, newest first. */
    List<Versioned<PricingSettings>> findAllVersions();

    boolean hasVersionStartingOnOrAfter(LocalDate day);

    /** Closes the version in force on {@code effectiveFrom} and adds the new one from that day. */
    Versioned<PricingSettings> publish(PricingSettings settings, LocalDate effectiveFrom);
}
