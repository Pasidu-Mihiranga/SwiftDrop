package lk.swiftdrop.merchant.application.port.out;

import java.util.UUID;

/**
 * What approval needs from the identity provider (Keycloak). Every method is idempotent: calling it
 * again after a partial failure, or after a successful call, leaves the same end state. That is
 * what makes "Keycloak first, then commit" safe to retry.
 *
 * @see lk.swiftdrop.merchant.application.exception.IdentityProviderUnavailableException thrown when
 *      the provider cannot be reached or refuses the service account
 */
public interface IdentityProvisioner {

    /**
     * Ensures the merchant has a confidential OAuth 2.0 client for client credentials: its tokens
     * carry the {@code MERCHANT_API} role, a fixed {@code merchant_id} claim and the
     * {@code swiftdrop-api} audience. The client secret is never returned here: the merchant
     * collects it themselves, so the approving admin never sees it.
     *
     * @return the client id, {@code merchant-<merchantId>}
     */
    String provisionClient(UUID merchantId);

    /**
     * Makes the applicant a merchant user: sets their {@code merchant_id} attribute and grants the
     * {@code MERCHANT} role. They see both after their next sign-in.
     *
     * @throws lk.swiftdrop.merchant.application.exception.MerchantOwnerMissingException when the
     *         account no longer exists
     */
    void grantMerchantRole(String ownerUserId, UUID merchantId);
}
