package lk.swiftdrop.parcel.application.command;

import lk.swiftdrop.common.web.error.BusinessRuleViolationException;
import lk.swiftdrop.common.web.error.ResourceNotFoundException;
import lk.swiftdrop.parcel.ParcelFixtures;
import lk.swiftdrop.parcel.application.port.out.DeliveryProofStore;
import lk.swiftdrop.parcel.application.port.out.DeliveryProofStore.StoredProof;
import lk.swiftdrop.parcel.application.port.out.ParcelRepository;
import lk.swiftdrop.parcel.application.port.out.ProofStorage;
import lk.swiftdrop.parcel.application.port.out.ProofStorage.PresignedUrl;
import lk.swiftdrop.parcel.domain.model.Parcel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryProofUseCaseTest {

    private static final Instant LATER = ParcelFixtures.NOW.plusSeconds(3600);

    @Mock
    private ParcelRepository parcels;
    @Mock
    private DeliveryProofStore proofs;
    @Mock
    private ProofStorage storage;

    private DeliveryProofUseCase useCase;
    private Parcel assigned;

    @BeforeEach
    void setUp() {
        useCase = new DeliveryProofUseCase(parcels, proofs, storage, Clock.fixed(LATER, ZoneOffset.UTC));
        assigned = ParcelFixtures.bookedBy("cust-1").assignCourier("courier-1", ParcelFixtures.NOW);
        lenient().when(parcels.findById(assigned.id())).thenReturn(Optional.of(assigned));
    }

    @Test
    void theAssignedCourierGetsAPresignedUploadUnderARandomKey() {
        when(storage.presignUpload(anyString(), eq("image/jpeg"), eq(2_000_000L)))
                .thenReturn(new PresignedUrl("https://s3.local/upload", LATER.plusSeconds(600)));

        DeliveryProofUseCase.ProofUpload upload = useCase.requestUpload(ParcelFixtures.user("courier-1", "COURIER", null),
                assigned.id(), "image/jpeg", 2_000_000L);

        ArgumentCaptor<StoredProof> stored = ArgumentCaptor.forClass(StoredProof.class);
        verify(proofs).save(stored.capture());
        assertThat(stored.getValue().id()).isEqualTo(upload.proofId());
        assertThat(stored.getValue().parcelId()).isEqualTo(assigned.id());
        assertThat(stored.getValue().objectKey())
                .isEqualTo("proofs/" + assigned.id() + "/" + upload.proofId() + ".jpg")
                .doesNotContain("Saman").doesNotContain(assigned.trackingNumber().value());
        assertThat(upload.uploadUrl()).isEqualTo("https://s3.local/upload");
        assertThat(upload.expiresAt()).isEqualTo(LATER.plusSeconds(600));
    }

    @Test
    void onlyJpegAndPngUpToFiveMegabytes() {
        var courier = ParcelFixtures.user("courier-1", "COURIER", null);

        assertThatThrownBy(() -> useCase.requestUpload(courier, assigned.id(), "application/pdf", 1000))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessageContaining("JPEG or PNG");
        assertThatThrownBy(() -> useCase.requestUpload(courier, assigned.id(), "image/png",
                DeliveryProofUseCase.MAX_BYTES + 1)).isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> useCase.requestUpload(courier, assigned.id(), "image/png", 0))
                .isInstanceOf(BusinessRuleViolationException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void anotherCourierCannotUploadForTheParcel() {
        assertThatThrownBy(() -> useCase.requestUpload(ParcelFixtures.user("courier-2", "COURIER", null), assigned.id(),
                "image/png", 1000)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(proofs, storage);
    }

    @Test
    void anyoneWhoMaySeeTheParcelGetsAViewLinkForItsOwnProofsOnly() {
        UUID proofId = UUID.randomUUID();
        when(proofs.findById(proofId)).thenReturn(Optional.of(
                new StoredProof(proofId, assigned.id(), "proofs/a.png", "image/png", 10, LATER, LATER)));
        when(storage.presignDownload("proofs/a.png")).thenReturn(new PresignedUrl("https://s3.local/a", LATER));

        assertThat(useCase.viewLink(ParcelFixtures.customer("cust-1"), assigned.id(), proofId).url())
                .isEqualTo("https://s3.local/a");
        assertThatThrownBy(() -> useCase.viewLink(ParcelFixtures.customer("cust-2"), assigned.id(), proofId))
                .isInstanceOf(ResourceNotFoundException.class);

        UUID foreign = UUID.randomUUID();
        when(proofs.findById(foreign)).thenReturn(Optional.of(
                new StoredProof(foreign, UUID.randomUUID(), "proofs/b.png", "image/png", 10, LATER, null)));
        assertThatThrownBy(() -> useCase.viewLink(ParcelFixtures.customer("cust-1"), assigned.id(), foreign))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(storage).presignDownload(any());
        verify(storage, org.mockito.Mockito.never()).presignUpload(anyString(), anyString(), anyLong());
    }
}
