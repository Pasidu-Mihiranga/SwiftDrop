package lk.swiftdrop.merchant.application.port.out;

import lk.swiftdrop.merchant.domain.model.MerchantStatus;

/**
 * Business meters for merchant onboarding (the M7 Grafana dashboard). The application layer calls
 * these only after its transaction has committed, so a change that rolls back is never counted.
 */
public interface MerchantMetrics {

    /**
     * A merchant's status changed and the change is stored.
     *
     * @param from the previous status, or null for a new application
     * @param to   the new status
     */
    void statusChanged(MerchantStatus from, MerchantStatus to);

    /** A merchant's API client secret was replaced. */
    void credentialRotated();
}
