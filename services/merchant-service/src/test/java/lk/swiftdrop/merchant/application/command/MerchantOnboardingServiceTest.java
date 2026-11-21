package lk.swiftdrop.merchant.application.command;

import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.events.common.Types.ActorRole;
import lk.swiftdrop.events.merchant.MerchantLifecycleChanged;
import lk.swiftdrop.merchant.MerchantFixtures;
import lk.swiftdrop.merchant.application.exception.IdentityProviderUnavailableException;
import lk.swiftdrop.merchant.application.port.out.ApiCredentialRepository;
import lk.swiftdrop.merchant.application.port.out.IdentityProvisioner;
import lk.swiftdrop.merchant.application.port.out.MerchantMetrics;
import lk.swiftdrop.merchant.application.port.out.MerchantRepository;
import lk.swiftdrop.merchant.domain.model.ApiCredential;
import org.mockito.InOrder;
import lk.swiftdrop.merchant.domain.exception.BrNumberAlreadyRegisteredException;
import lk.swiftdrop.merchant.domain.exception.InvalidMerchantTransitionException;
import lk.swiftdrop.merchant.domain.exception.MerchantApplicationExistsException;
import lk.swiftdrop.merchant.domain.exception.MerchantNotFoundException;
import lk.swiftdrop.merchant.domain.model.Merchant;
import lk.swiftdrop.merchant.domain.model.MerchantStatus;
import lk.swiftdrop.outbox.OutboxWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static lk.swiftdrop.merchant.MerchantFixtures.NOW;
import static lk.swiftdrop.merchant.MerchantFixtures.OWNER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantOnboardingServiceTest {

    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final AuthenticatedUser APPLICANT = MerchantFixtures.user(OWNER);
    private static final AuthenticatedUser ADMIN = MerchantFixtures.admin("admin-1");

    @Mock
    private MerchantRepository merchants;
    @Mock
    private ApiCredentialRepository credentials;
    @Mock
    private IdentityProvisioner identity;
    @Mock
    private OutboxWriter outbox;
    @Mock
    private MerchantMetrics metrics;

    private MerchantOnboardingService service;

    @BeforeEach
    void setUp() {
        service = new MerchantOnboardingService(merchants, credentials, identity, outbox, metrics,
                TransactionOperations.withoutTransaction(), CLOCK);
        // the stored copy comes back with a version, as the JPA adapter returns it
        lenient().when(merchants.save(any())).thenAnswer(call -> MerchantFixtures.stored(call.getArgument(0)));
        lenient().when(identity.provisionClient(any())).thenAnswer(call -> "merchant-" + call.getArgument(0));
        lenient().when(credentials.findActiveByMerchant(any())).thenReturn(Optional.empty());
    }

    private MerchantLifecycleChanged publishedEvent() {
        ArgumentCaptor<MerchantLifecycleChanged> event = ArgumentCaptor.forClass(MerchantLifecycleChanged.class);
        verify(outbox).publish(event.capture());
        return event.getValue();
    }

    @Test
    void registeringStoresAPendingApplicationAndPublishesIt() {
        Merchant merchant = service.register(APPLICANT, MerchantFixtures.lankaBooks());

        assertThat(merchant.status()).isEqualTo(MerchantStatus.PENDING);
        assertThat(merchant.ownerUserId()).isEqualTo(OWNER);
        MerchantLifecycleChanged event = publishedEvent();
        assertThat(event.merchantId()).isEqualTo(merchant.id());
        assertThat(event.previousStatus()).isNull();
        assertThat(event.status()).isEqualTo(MerchantLifecycleChanged.MerchantStatus.PENDING);
        assertThat(event.businessName()).isEqualTo("Lanka Books");
        assertThat(event.contactEmail()).isEqualTo("lankabooks@example.com");
        assertThat(event.changedBy().userId()).isEqualTo(OWNER);
        assertThat(event.changedBy().role()).isEqualTo(ActorRole.CUSTOMER);
        assertThat(event.changedAt()).isEqualTo(NOW);
    }

    @Test
    void theRegistrationNumberIsCheckedInItsNormalisedForm() {
        service.register(APPLICANT, MerchantFixtures.lankaBooks());

        verify(merchants).existsActiveByBrNumber("PV12345");
    }

    @Test
    void aUserWithAnApplicationInPlayCannotApplyAgain() {
        when(merchants.existsActiveByOwner(OWNER)).thenReturn(true);

        assertThatThrownBy(() -> service.register(APPLICANT, MerchantFixtures.lankaBooks()))
                .isInstanceOf(MerchantApplicationExistsException.class);
        verify(merchants, never()).save(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void aRegistrationNumberInUseIsAConflict() {
        when(merchants.existsActiveByBrNumber("PV12345")).thenReturn(true);

        assertThatThrownBy(() -> service.register(APPLICANT, MerchantFixtures.lankaBooks()))
                .isInstanceOfSatisfying(BrNumberAlreadyRegisteredException.class,
                        e -> assertThat(e.status().value()).isEqualTo(409));
        verify(merchants, never()).save(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void aRejectedApplicantMayApplyAgain() {
        // a rejected application is not "active", so neither check finds anything
        when(merchants.existsActiveByOwner(OWNER)).thenReturn(false);
        when(merchants.existsActiveByBrNumber("PV12345")).thenReturn(false);

        Merchant merchant = service.register(APPLICANT, MerchantFixtures.lankaBooks());

        assertThat(merchant.status()).isEqualTo(MerchantStatus.PENDING);
    }

    @Test
    void approvingPublishesTheChangeWithThePreviousStatus() {
        Merchant pending = MerchantFixtures.stored(MerchantFixtures.pending());
        when(merchants.findById(pending.id())).thenReturn(Optional.of(pending));

        Merchant approved = service.approve(ADMIN, pending.id());

        assertThat(approved.status()).isEqualTo(MerchantStatus.APPROVED);
        assertThat(approved.reviewedBy()).isEqualTo("admin-1");
        MerchantLifecycleChanged event = publishedEvent();
        assertThat(event.previousStatus()).isEqualTo(MerchantLifecycleChanged.MerchantStatus.PENDING);
        assertThat(event.status()).isEqualTo(MerchantLifecycleChanged.MerchantStatus.APPROVED);
        assertThat(event.changedBy().role()).isEqualTo(ActorRole.ADMIN);
        assertThat(event.reason()).isNull();
    }

    @Test
    void approvalProvisionsKeycloakBeforeAnythingIsStored() {
        Merchant pending = MerchantFixtures.stored(MerchantFixtures.pending());
        when(merchants.findById(pending.id())).thenReturn(Optional.of(pending));

        service.approve(ADMIN, pending.id());

        InOrder order = inOrder(identity, merchants, credentials, outbox);
        order.verify(identity).provisionClient(pending.id());
        order.verify(identity).grantMerchantRole(OWNER, pending.id());
        order.verify(merchants).save(any());
        order.verify(credentials).save(any());
        order.verify(outbox).publish(any());
    }

    @Test
    void approvalRecordsWhichKeycloakClientBelongsToTheMerchant() {
        Merchant pending = MerchantFixtures.stored(MerchantFixtures.pending());
        when(merchants.findById(pending.id())).thenReturn(Optional.of(pending));

        service.approve(ADMIN, pending.id());

        ArgumentCaptor<ApiCredential> credential = ArgumentCaptor.forClass(ApiCredential.class);
        verify(credentials).save(credential.capture());
        assertThat(credential.getValue().merchantId()).isEqualTo(pending.id());
        assertThat(credential.getValue().keycloakClientId()).isEqualTo("merchant-" + pending.id());
        assertThat(credential.getValue().createdAt()).isEqualTo(NOW);
        assertThat(credential.getValue().rotatedAt()).isNull();
    }

    @Test
    void aRetriedApprovalDoesNotRecordASecondClient() {
        Merchant pending = MerchantFixtures.stored(MerchantFixtures.pending());
        when(merchants.findById(pending.id())).thenReturn(Optional.of(pending));
        when(credentials.findActiveByMerchant(pending.id()))
                .thenReturn(Optional.of(ApiCredential.issue(pending.id(), "merchant-" + pending.id(), NOW)));

        service.approve(ADMIN, pending.id());

        verify(credentials, never()).save(any());
    }

    @Test
    void whenKeycloakFailsTheMerchantStaysPendingAndNothingIsPublished() {
        Merchant pending = MerchantFixtures.stored(MerchantFixtures.pending());
        when(merchants.findById(pending.id())).thenReturn(Optional.of(pending));
        when(identity.provisionClient(pending.id()))
                .thenThrow(new IdentityProviderUnavailableException("The identity provider is not reachable right now"));

        assertThatThrownBy(() -> service.approve(ADMIN, pending.id()))
                .isInstanceOfSatisfying(IdentityProviderUnavailableException.class,
                        e -> assertThat(e.status().value()).isEqualTo(503));
        verify(merchants, never()).save(any());
        verifyNoInteractions(credentials, outbox);
    }

    @Test
    void aWrongDecisionNeverReachesKeycloak() {
        Merchant approved = MerchantFixtures.stored(MerchantFixtures.pending().approve("admin-1", NOW));
        when(merchants.findById(approved.id())).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> service.approve(ADMIN, approved.id()))
                .isInstanceOf(InvalidMerchantTransitionException.class);
        verifyNoInteractions(identity, credentials, outbox);
    }

    @Test
    void rejectingAndSuspendingNeverTouchKeycloak() {
        Merchant pending = MerchantFixtures.stored(MerchantFixtures.pending());
        when(merchants.findById(pending.id())).thenReturn(Optional.of(pending));

        service.reject(ADMIN, pending.id(), "Incomplete");

        verifyNoInteractions(identity, credentials);
    }

    @Test
    void rejectingCarriesTheReasonOnTheEvent() {
        Merchant pending = MerchantFixtures.stored(MerchantFixtures.pending());
        when(merchants.findById(pending.id())).thenReturn(Optional.of(pending));

        Merchant rejected = service.reject(ADMIN, pending.id(), "BR number could not be verified");

        assertThat(rejected.status()).isEqualTo(MerchantStatus.REJECTED);
        assertThat(publishedEvent().reason()).isEqualTo("BR number could not be verified");
    }

    @Test
    void suspendingAnApprovedMerchantPublishesTheChange() {
        Merchant approved = MerchantFixtures.stored(MerchantFixtures.pending().approve("admin-1", NOW));
        when(merchants.findById(approved.id())).thenReturn(Optional.of(approved));

        Merchant suspended = service.suspend(ADMIN, approved.id());

        assertThat(suspended.status()).isEqualTo(MerchantStatus.SUSPENDED);
        MerchantLifecycleChanged event = publishedEvent();
        assertThat(event.previousStatus()).isEqualTo(MerchantLifecycleChanged.MerchantStatus.APPROVED);
        assertThat(event.status()).isEqualTo(MerchantLifecycleChanged.MerchantStatus.SUSPENDED);
    }

    @Test
    void aRefusedTransitionStoresAndPublishesNothing() {
        Merchant pending = MerchantFixtures.stored(MerchantFixtures.pending());
        when(merchants.findById(pending.id())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.suspend(ADMIN, pending.id()))
                .isInstanceOf(InvalidMerchantTransitionException.class);
        verify(merchants, never()).save(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void decidingOnAnUnknownMerchantIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(merchants.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(ADMIN, unknown)).isInstanceOf(MerchantNotFoundException.class);
        verifyNoInteractions(outbox);
    }

    @Test
    void aNewApplicationIsCountedOnceItIsStored() {
        service.register(APPLICANT, MerchantFixtures.lankaBooks());

        verify(metrics).statusChanged(null, MerchantStatus.PENDING);
    }

    @Test
    void eachDecisionIsCountedWithTheStatusItReplaced() {
        Merchant pending = MerchantFixtures.stored(MerchantFixtures.pending());
        Merchant approved = MerchantFixtures.stored(MerchantFixtures.pending().approve("admin-1", NOW));
        Merchant other = MerchantFixtures.stored(MerchantFixtures.pending());
        when(merchants.findById(pending.id())).thenReturn(Optional.of(pending));
        when(merchants.findById(approved.id())).thenReturn(Optional.of(approved));
        when(merchants.findById(other.id())).thenReturn(Optional.of(other));

        service.approve(ADMIN, pending.id());
        service.suspend(ADMIN, approved.id());
        service.reject(ADMIN, other.id(), "Incomplete");

        verify(metrics).statusChanged(MerchantStatus.PENDING, MerchantStatus.APPROVED);
        verify(metrics).statusChanged(MerchantStatus.APPROVED, MerchantStatus.SUSPENDED);
        verify(metrics).statusChanged(MerchantStatus.PENDING, MerchantStatus.REJECTED);
    }

    @Test
    void aChangeThatDoesNotHappenIsNeverCounted() {
        Merchant pending = MerchantFixtures.stored(MerchantFixtures.pending());
        when(merchants.findById(pending.id())).thenReturn(Optional.of(pending));
        when(merchants.existsActiveByBrNumber("PV12345")).thenReturn(true);
        when(identity.provisionClient(pending.id()))
                .thenThrow(new IdentityProviderUnavailableException("The identity provider is not reachable right now"));

        assertThatThrownBy(() -> service.register(APPLICANT, MerchantFixtures.lankaBooks()))
                .isInstanceOf(BrNumberAlreadyRegisteredException.class);
        assertThatThrownBy(() -> service.approve(ADMIN, pending.id()))
                .isInstanceOf(IdentityProviderUnavailableException.class);
        assertThatThrownBy(() -> service.suspend(ADMIN, pending.id()))
                .isInstanceOf(InvalidMerchantTransitionException.class);
        verifyNoInteractions(metrics);
    }

    @Test
    void aChangeWhoseTransactionFailsIsNotCounted() {
        Merchant pending = MerchantFixtures.stored(MerchantFixtures.pending());
        when(merchants.findById(pending.id())).thenReturn(Optional.of(pending));
        doThrow(new IllegalStateException("commit failed")).when(outbox).publish(any());

        assertThatThrownBy(() -> service.reject(ADMIN, pending.id(), "Incomplete"))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(metrics);
    }
}
