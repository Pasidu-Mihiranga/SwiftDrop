package lk.swiftdrop.merchant.application.command;

import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.merchant.application.exception.ApiCredentialNotFoundException;
import lk.swiftdrop.merchant.application.model.RotatedCredential;
import lk.swiftdrop.merchant.application.port.out.ApiCredentialRepository;
import lk.swiftdrop.merchant.application.port.out.IdentityProvisioner;
import lk.swiftdrop.merchant.application.port.out.MerchantRepository;
import lk.swiftdrop.merchant.domain.exception.MerchantNotApprovedException;
import lk.swiftdrop.merchant.domain.exception.MerchantNotFoundException;
import lk.swiftdrop.merchant.domain.model.ApiCredential;
import lk.swiftdrop.merchant.domain.model.Merchant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * A merchant's API credentials (design figure 8: rotate secret).
 *
 * <p>The secret lives only in Keycloak. This service keeps the record of the client and when its
 * secret last changed, and hands the new secret back exactly once; it never stores or logs it.
 */
@Service
public class MerchantCredentialService {

    private static final Logger log = LoggerFactory.getLogger(MerchantCredentialService.class);

    private final MerchantRepository merchants;
    private final ApiCredentialRepository credentials;
    private final IdentityProvisioner identity;
    private final TransactionOperations transaction;
    private final Clock clock;

    public MerchantCredentialService(MerchantRepository merchants, ApiCredentialRepository credentials,
                                     IdentityProvisioner identity, TransactionOperations transaction, Clock clock) {
        this.merchants = merchants;
        this.credentials = credentials;
        this.identity = identity;
        this.transaction = transaction;
        this.clock = clock;
    }

    /**
     * Replaces the caller's client secret and returns the new one; the old one stops working at once.
     *
     * <p>The merchant is the one named by the caller's token ({@code merchant_id}, set by an admin
     * on approval), and the caller must also be its owner; nothing is read from the request. Any
     * other caller gets the same 404 as one with no merchant, so no merchant's existence leaks.
     *
     * <p>One transaction, with Keycloak called last:
     * <ol>
     *   <li>the merchant must be APPROVED and have a credential that is not revoked;</li>
     *   <li>{@code rotated_at} is written, which also locks the row, so two rotations of the same
     *       credential run one after the other;</li>
     *   <li>Keycloak regenerates the secret.</li>
     * </ol>
     * If Keycloak fails the transaction rolls back and the answer is 503. Usually nothing changed and the
     * old secret still works; but if Keycloak applied the change and its answer was lost (a read
     * timeout), the old secret is already gone. Retrying is always safe and issues a working secret.
     *
     * @throws MerchantNotFoundException     when the caller is not the owner of an existing merchant
     * @throws MerchantNotApprovedException  when the merchant is not APPROVED (e.g. suspended)
     * @throws ApiCredentialNotFoundException when the merchant has no credential in use
     */
    public RotatedCredential rotate(AuthenticatedUser caller) {
        UUID merchantId = merchantIdOf(caller).orElseThrow(MerchantNotFoundException::new);
        RotatedCredential rotated = transaction.execute(status -> {
            Merchant merchant = merchants.findById(merchantId)
                    .filter(found -> found.ownerUserId().equals(caller.id()))
                    .orElseThrow(MerchantNotFoundException::new);
            if (!merchant.isApproved()) {
                throw new MerchantNotApprovedException(merchant.status());
            }
            ApiCredential credential = credentials.findActiveByMerchant(merchantId)
                    .orElseThrow(ApiCredentialNotFoundException::new);
            credentials.save(credential.rotate(clock.instant()));
            String secret = identity.regenerateClientSecret(credential.keycloakClientId());
            return new RotatedCredential(credential.keycloakClientId(), secret);
        });
        log.info("API credential {} rotated for merchant {}", rotated.clientId(), merchantId);
        return rotated;
    }

    /** The merchant_id claim, if it is present and a UUID. */
    private static Optional<UUID> merchantIdOf(AuthenticatedUser caller) {
        return caller.merchantId().flatMap(value -> {
            try {
                return Optional.of(UUID.fromString(value));
            } catch (IllegalArgumentException malformed) {
                return Optional.empty();
            }
        });
    }
}
