package lk.swiftdrop.merchant.application.command;

import lk.swiftdrop.common.security.AuthenticatedUser;
import lk.swiftdrop.merchant.MerchantFixtures;
import lk.swiftdrop.merchant.application.exception.ApiCredentialNotFoundException;
import lk.swiftdrop.merchant.application.exception.IdentityProviderUnavailableException;
import lk.swiftdrop.merchant.application.model.RotatedCredential;
import lk.swiftdrop.merchant.application.port.out.ApiCredentialRepository;
import lk.swiftdrop.merchant.application.port.out.IdentityProvisioner;
import lk.swiftdrop.merchant.application.port.out.MerchantMetrics;
import lk.swiftdrop.merchant.application.port.out.MerchantRepository;
import lk.swiftdrop.merchant.domain.exception.MerchantNotApprovedException;
import lk.swiftdrop.merchant.domain.exception.MerchantNotFoundException;
import lk.swiftdrop.merchant.domain.model.ApiCredential;
import lk.swiftdrop.merchant.domain.model.Merchant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static lk.swiftdrop.merchant.MerchantFixtures.NOW;
import static lk.swiftdrop.merchant.MerchantFixtures.OWNER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class MerchantCredentialServiceTest {

    private static final Instant ROTATED_AT = NOW.plusSeconds(3600);
    private static final Clock CLOCK = Clock.fixed(ROTATED_AT, ZoneOffset.UTC);
    private static final String NEW_SECRET = "new-secret-9f8e7d6c";

    @Mock
    private MerchantRepository merchants;
    @Mock
    private ApiCredentialRepository credentials;
    @Mock
    private IdentityProvisioner identity;
    @Mock
    private MerchantMetrics metrics;

    private MerchantCredentialService service;
    private Merchant approved;
    private ApiCredential credential;

    @BeforeEach
    void setUp() {
        service = new MerchantCredentialService(merchants, credentials, identity, metrics,
                TransactionOperations.withoutTransaction(), CLOCK);
        approved = MerchantFixtures.stored(MerchantFixtures.pending().approve("admin-1", NOW));
        credential = ApiCredential.issue(approved.id(), "merchant-" + approved.id(), NOW);
    }

    /** The owner signed in after approval: MERCHANT role and merchant_id from Keycloak. */
    private AuthenticatedUser owner() {
        return merchantUser(OWNER, approved.id().toString());
    }

    private static AuthenticatedUser merchantUser(String userId, String merchantId) {
        return new AuthenticatedUser(userId, Optional.of(userId + "@example.com"), Optional.of("Owner"),
                Set.of("MERCHANT"), Optional.ofNullable(merchantId));
    }

    private void stored(Merchant merchant, ApiCredential active) {
        when(merchants.findById(merchant.id())).thenReturn(Optional.of(merchant));
        if (active != null) {
            when(credentials.findActiveByMerchant(merchant.id())).thenReturn(Optional.of(active));
        }
    }

    @Test
    void theOwnerGetsANewSecretOnceAndOnlyTheTimeIsStored() {
        stored(approved, credential);
        when(identity.regenerateClientSecret(credential.keycloakClientId())).thenReturn(NEW_SECRET);

        RotatedCredential rotated = service.rotate(owner());

        assertThat(rotated.clientId()).isEqualTo("merchant-" + approved.id());
        assertThat(rotated.clientSecret()).isEqualTo(NEW_SECRET);
        ArgumentCaptor<ApiCredential> saved = ArgumentCaptor.forClass(ApiCredential.class);
        verify(credentials).save(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo(credential.id());
        assertThat(saved.getValue().rotatedAt()).isEqualTo(ROTATED_AT);
        assertThat(saved.getValue().toString()).doesNotContain(NEW_SECRET);
    }

    @Test
    void aRotationIsCountedOnlyOnceKeycloakHasIssuedTheSecret() {
        stored(approved, credential);
        when(identity.regenerateClientSecret(credential.keycloakClientId())).thenReturn(NEW_SECRET);

        service.rotate(owner());

        verify(metrics).credentialRotated();
    }

    @Test
    void aFailedRotationIsNotCounted() {
        stored(approved, credential);
        when(identity.regenerateClientSecret(any()))
                .thenThrow(new IdentityProviderUnavailableException("The identity provider is not reachable right now"));

        assertThatThrownBy(() -> service.rotate(owner())).isInstanceOf(IdentityProviderUnavailableException.class);
        verifyNoInteractions(metrics);
    }

    @Test
    void theRecordIsWrittenBeforeKeycloakSoAKeycloakFailureRollsItBack() {
        stored(approved, credential);
        when(identity.regenerateClientSecret(any())).thenReturn(NEW_SECRET);

        service.rotate(owner());

        // Keycloak last: if it fails, the transaction undoes the write and the old secret is untouched.
        InOrder order = inOrder(credentials, identity);
        order.verify(credentials).save(any());
        order.verify(identity).regenerateClientSecret(any());
    }

    @Test
    void aKeycloakFailureReachesTheCallerAs503() {
        stored(approved, credential);
        when(identity.regenerateClientSecret(any()))
                .thenThrow(new IdentityProviderUnavailableException("The identity provider is not reachable right now"));

        assertThatThrownBy(() -> service.rotate(owner()))
                .isInstanceOfSatisfying(IdentityProviderUnavailableException.class,
                        e -> assertThat(e.status().value()).isEqualTo(503));
    }

    @Test
    void theSecretIsNeverLogged(CapturedOutput output) {
        stored(approved, credential);
        when(identity.regenerateClientSecret(any())).thenReturn(NEW_SECRET);

        RotatedCredential rotated = service.rotate(owner());

        assertThat(output.getAll()).contains("rotated for merchant " + approved.id()).doesNotContain(NEW_SECRET);
        assertThat(rotated.toString()).doesNotContain(NEW_SECRET);
    }

    @Test
    void aCallerWithoutAMerchantIdIsTreatedAsHavingNoMerchant() {
        assertThatThrownBy(() -> service.rotate(merchantUser(OWNER, null)))
                .isInstanceOf(MerchantNotFoundException.class);
        assertThatThrownBy(() -> service.rotate(merchantUser(OWNER, "not-a-uuid")))
                .isInstanceOf(MerchantNotFoundException.class);
        verifyNoInteractions(merchants, credentials, identity);
    }

    @Test
    void aTokenNamingSomeoneElsesMerchantGets404AndNothingChanges() {
        when(merchants.findById(approved.id())).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> service.rotate(merchantUser("another-user", approved.id().toString())))
                .isInstanceOf(MerchantNotFoundException.class);
        verifyNoInteractions(credentials, identity);
    }

    @Test
    void anUnknownMerchantGets404() {
        when(merchants.findById(approved.id())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate(owner())).isInstanceOf(MerchantNotFoundException.class);
        verifyNoInteractions(credentials, identity);
    }

    @Test
    void aSuspendedMerchantCannotRotate() {
        Merchant suspended = MerchantFixtures.stored(approved.suspend("admin-1", NOW));
        stored(suspended, null);

        assertThatThrownBy(() -> service.rotate(owner()))
                .isInstanceOfSatisfying(MerchantNotApprovedException.class,
                        e -> assertThat(e.status().value()).isEqualTo(422));
        verifyNoInteractions(credentials, identity);
    }

    @Test
    void aPendingMerchantCannotRotate() {
        Merchant pending = MerchantFixtures.stored(MerchantFixtures.pending());
        stored(pending, null);

        assertThatThrownBy(() -> service.rotate(merchantUser(OWNER, pending.id().toString())))
                .isInstanceOf(MerchantNotApprovedException.class);
        verifyNoInteractions(credentials, identity);
    }

    @Test
    void withoutACredentialInUseThereIsNothingToRotate() {
        stored(approved, null);
        when(credentials.findActiveByMerchant(approved.id())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate(owner())).isInstanceOf(ApiCredentialNotFoundException.class);
        verify(credentials, never()).save(any());
        verifyNoInteractions(identity);
    }
}
