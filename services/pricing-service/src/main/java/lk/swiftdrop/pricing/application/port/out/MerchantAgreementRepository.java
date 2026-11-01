package lk.swiftdrop.pricing.application.port.out;

import lk.swiftdrop.pricing.domain.model.MerchantAgreement;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Reads and writes the negotiated terms a merchant ships under. */
public interface MerchantAgreementRepository {

    /**
     * The agreement in force for a merchant on a date, with its lane overrides loaded.
     *
     * <p>Empty for most quotes: an anonymous customer has no merchant, and a merchant without
     * negotiated terms pays the counter price.
     */
    Optional<MerchantAgreement> findInForce(UUID merchantId, LocalDate on);

    List<MerchantAgreement> findByMerchant(UUID merchantId);

    /** Every merchant's agreement in force on a date. */
    List<MerchantAgreement> findAllInForce(LocalDate on);

    /** Whether a period for this merchant already starts on or after a date. */
    boolean hasPeriodStartingOnOrAfter(UUID merchantId, LocalDate date);

    /**
     * Publishes an agreement from a date, closing whatever it replaces.
     *
     * <p>The old row is kept, so a quote issued under the previous terms can still be
     * explained, exactly as a rate card works.
     */
    MerchantAgreement publish(MerchantAgreement agreement, LocalDate effectiveFrom);
}
