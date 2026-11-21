package lk.swiftdrop.merchant.application.command;

import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.events.common.Types.ActorRole;
import lk.swiftdrop.events.merchant.MerchantLifecycleChanged;
import lk.swiftdrop.merchant.MerchantFixtures;
import lk.swiftdrop.merchant.application.port.out.MerchantRepository;
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
    private OutboxWriter outbox;

    private MerchantOnboardingService service;

    @BeforeEach
    void setUp() {
        service = new MerchantOnboardingService(merchants, outbox, TransactionOperations.withoutTransaction(), CLOCK);
        // the stored copy comes back with a version, as the JPA adapter returns it
        lenient().when(merchants.save(any())).thenAnswer(call -> MerchantFixtures.stored(call.getArgument(0)));
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
}
