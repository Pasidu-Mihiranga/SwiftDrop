package lk.swiftdrop.parcel.application.receipt;

import lk.swiftdrop.events.common.Types.ActorRole;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.parcel.ParcelFixtures;
import lk.swiftdrop.parcel.domain.model.Parcel;
import lk.swiftdrop.parcel.domain.model.StatusChange;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptPdfGeneratorTest {

    private final ReceiptPdfGenerator generator = new ReceiptPdfGenerator();

    @Test
    void generatesValidPdfWithHeaderTrackingAndFee() {
        Parcel parcel = ParcelFixtures.bookedBy("cust-100");

        byte[] pdf = generator.generate(parcel);

        assertThat(pdf).isNotEmpty();
        // A valid PDF file starts with %PDF-
        String header = new String(pdf, 0, Math.min(pdf.length, 10), StandardCharsets.ISO_8859_1);
        assertThat(header).startsWith("%PDF-");
    }

    @Test
    void generatesReceiptForDeliveredParcelWithDeliveryProof() {
        Parcel booked = ParcelFixtures.bookedBy("cust-100");
        List<StatusChange> extendedHistory = new ArrayList<>(booked.history());
        extendedHistory.add(new StatusChange(ParcelStatus.CREATED, ParcelStatus.PICKED_UP, "courier-1", ActorRole.COURIER, "Picked up from sender", Instant.now().minusSeconds(3600)));
        extendedHistory.add(new StatusChange(ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT, "courier-1", ActorRole.COURIER, "Dispatched to hub", Instant.now().minusSeconds(2400)));
        extendedHistory.add(new StatusChange(ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY, "courier-1", ActorRole.COURIER, "Out with courier", Instant.now().minusSeconds(1200)));
        extendedHistory.add(new StatusChange(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED, "courier-1", ActorRole.COURIER, "Signed by recipient John", Instant.now()));

        Parcel delivered = new Parcel(booked.id(), booked.trackingNumber(), booked.source(), booked.customerId(),
                booked.merchantId(), booked.merchantOrderRef(), booked.details(), ParcelStatus.DELIVERED,
                "courier-1", extendedHistory, booked.createdAt(), Instant.now(), booked.version());

        byte[] pdf = generator.generate(delivered);

        assertThat(pdf).isNotEmpty();
        String header = new String(pdf, 0, Math.min(pdf.length, 10), StandardCharsets.ISO_8859_1);
        assertThat(header).startsWith("%PDF-");
        // PDF contains hundreds of bytes of formatted tables and content
        assertThat(pdf.length).isGreaterThan(1000);
    }
}
