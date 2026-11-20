package lk.swiftdrop.merchant.application.command;

import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.events.common.Types.Actor;
import lk.swiftdrop.events.common.Types.ActorRole;
import lk.swiftdrop.events.merchant.MerchantLifecycleChanged;
import lk.swiftdrop.merchant.application.port.out.ApiCredentialRepository;
import lk.swiftdrop.merchant.application.port.out.IdentityProvisioner;
import lk.swiftdrop.merchant.application.port.out.MerchantRepository;
import lk.swiftdrop.merchant.domain.model.ApiCredential;
import lk.swiftdrop.merchant.domain.exception.BrNumberAlreadyRegisteredException;
import lk.swiftdrop.merchant.domain.exception.MerchantApplicationExistsException;
import lk.swiftdrop.merchant.domain.exception.MerchantNotFoundException;
import lk.swiftdrop.merchant.domain.model.BusinessDetails;
import lk.swiftdrop.merchant.domain.model.Merchant;
import lk.swiftdrop.merchant.domain.model.MerchantStatus;
import lk.swiftdrop.outbox.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Merchant onboarding: a user applies, and an admin approves, rejects or suspends.
 *
 * <p>Every change and its {@code MerchantLifecycleChanged} event commit in one transaction, so no
 * status change is lost and no event is published for a change that did not happen. The state
 * rules live in {@link Merchant}; this class only loads, applies and stores.
 *
 * <p>Role checks (who may call what) are made by the API layer before these methods run.
 */
@Service
public class MerchantOnboardingService {

    private final MerchantRepository merchants;
    private final ApiCredentialRepository credentials;
    private final IdentityProvisioner identity;
    private final OutboxWriter outbox;
    private final TransactionOperations transaction;
    private final Clock clock;

    public MerchantOnboardingService(MerchantRepository merchants, ApiCredentialRepository credentials,
                                     IdentityProvisioner identity, OutboxWriter outbox,
                                     TransactionOperations transaction, Clock clock) {
        this.merchants = merchants;
        this.credentials = credentials;
        this.identity = identity;
        this.outbox = outbox;
        this.transaction = transaction;
        this.clock = clock;
    }

    /**
     * A signed-in user applies. A user whose earlier application was rejected may apply again with
     * corrected details; the rejected application is kept as history.
     *
     * @throws MerchantApplicationExistsException when the user already has an application in play
     * @throws BrNumberAlreadyRegisteredException when another active application uses the number
     */
    public Merchant register(AuthenticatedUser applicant, BusinessDetails business) {
        return transaction.execute(status -> {
            if (merchants.existsActiveByOwner(applicant.id())) {
                throw new MerchantApplicationExistsException();
            }
            if (merchants.existsActiveByBrNumber(business.brNumber())) {
                throw new BrNumberAlreadyRegisteredException();
            }
            // The unique indexes behind save() settle a race between two identical applications.
            Merchant merchant = merchants.save(Merchant.register(applicant.id(), business, clock.instant()));
            // The applicant does not hold the MERCHANT role yet: they apply from an ordinary account.
            outbox.publish(toEvent(merchant, null, new Actor(applicant.id(), ActorRole.CUSTOMER)));
            return merchant;
        });
    }

    /**
     * Accepts a PENDING application and gives the merchant API access (design figure 6).
     *
     * <ol>
     *   <li>The state rule is checked first, so a wrong decision never reaches Keycloak.</li>
     *   <li>Keycloak is called before anything is committed: the merchant's client-credentials client
     *       is created and the applicant gets the MERCHANT role and merchant_id attribute.</li>
     *   <li>Only then, in one transaction: APPROVED, the api_credential record and the event.</li>
     * </ol>
     *
     * If Keycloak fails the answer is 503 and the merchant stays PENDING. Every Keycloak call is
     * idempotent, so approving again simply finishes the job.
     */
    public Merchant approve(AuthenticatedUser admin, UUID merchantId) {
        Merchant pending = merchants.findById(merchantId).orElseThrow(MerchantNotFoundException::new);
        pending.approve(admin.id(), clock.instant());

        String clientId = identity.provisionClient(pending.id());
        identity.grantMerchantRole(pending.ownerUserId(), pending.id());

        return transaction.execute(status -> {
            // Reloaded inside the transaction: another admin may have decided in the meantime.
            Merchant current = merchants.findById(merchantId).orElseThrow(MerchantNotFoundException::new);
            Merchant approved = merchants.save(current.approve(admin.id(), clock.instant()));
            if (credentials.findActiveByMerchant(approved.id()).isEmpty()) {
                credentials.save(ApiCredential.issue(approved.id(), clientId, clock.instant()));
            }
            outbox.publish(toEvent(approved, current.status(), new Actor(admin.id(), ActorRole.ADMIN)));
            return approved;
        });
    }

    /** Turns down a PENDING application with a reason the applicant can read. */
    public Merchant reject(AuthenticatedUser admin, UUID merchantId, String reason) {
        return decide(admin, merchantId, merchant -> merchant.reject(admin.id(), reason, clock.instant()));
    }

    /** Stops an APPROVED merchant: other services refuse its API orders once they see the event. */
    public Merchant suspend(AuthenticatedUser admin, UUID merchantId) {
        return decide(admin, merchantId, merchant -> merchant.suspend(admin.id(), clock.instant()));
    }

    private Merchant decide(AuthenticatedUser admin, UUID merchantId, UnaryOperator<Merchant> decision) {
        return transaction.execute(status -> {
            Merchant current = merchants.findById(merchantId).orElseThrow(MerchantNotFoundException::new);
            Merchant decided = merchants.save(decision.apply(current));
            outbox.publish(toEvent(decided, current.status(), new Actor(admin.id(), ActorRole.ADMIN)));
            return decided;
        });
    }

    private static MerchantLifecycleChanged toEvent(Merchant merchant, MerchantStatus previous, Actor changedBy) {
        return new MerchantLifecycleChanged(merchant.id(), merchant.business().businessName(), merchant.ownerUserId(),
                merchant.business().contactEmail(), toEventStatus(previous), toEventStatus(merchant.status()),
                merchant.rejectionReason(), changedBy, merchant.updatedAt());
    }

    private static MerchantLifecycleChanged.MerchantStatus toEventStatus(MerchantStatus status) {
        return status == null ? null : MerchantLifecycleChanged.MerchantStatus.valueOf(status.name());
    }
}
