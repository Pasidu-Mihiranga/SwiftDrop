package lk.swiftdrop.parcel.application.receipt;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.Barcode128;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lk.swiftdrop.events.common.Types.ParcelStatus;
import lk.swiftdrop.parcel.domain.model.Address;
import lk.swiftdrop.parcel.domain.model.BookingDetails;
import lk.swiftdrop.parcel.domain.model.Parcel;
import lk.swiftdrop.parcel.domain.model.StatusChange;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates an official, publication-quality SwiftDrop Delivery Receipt PDF using OpenPDF.
 *
 * <p>Contains:
 * <ul>
 *   <li>Brand header, receipt reference, tracking number barcode</li>
 *   <li>Pickup (sender) &amp; Dropoff (recipient) address details</li>
 *   <li>Parcel package specifications (weight, size class, service level)</li>
 *   <li>Itemized fee breakdown</li>
 *   <li>Complete status history timeline &amp; delivery proof summary</li>
 * </ul>
 */
@Component
public class ReceiptPdfGenerator {

    private static final DateTimeFormatter DATE_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneId.of("UTC"));

    // Color Palette
    private static final Color BRAND_NAVY = new Color(30, 58, 138);       // #1E3A8A
    private static final Color BRAND_BLUE = new Color(37, 99, 235);       // #2563EB
    private static final Color TEXT_DARK = new Color(15, 23, 42);         // #0F172A
    private static final Color TEXT_MUTED = new Color(100, 116, 139);     // #64748B
    private static final Color BG_LIGHT = new Color(248, 250, 252);       // #F8FAFC
    private static final Color BORDER_GRAY = new Color(226, 232, 240);    // #E2E8F0
    private static final Color BG_STATUS_DELIVERED = new Color(236, 253, 245); // #ECFDF5
    private static final Color TXT_STATUS_DELIVERED = new Color(6, 95, 70);    // #065F46

    // Fonts
    private static final Font FONT_HEADER_TITLE = new Font(Font.HELVETICA, 20, Font.BOLD, BRAND_NAVY);
    private static final Font FONT_HEADER_SUB = new Font(Font.HELVETICA, 9, Font.NORMAL, TEXT_MUTED);
    private static final Font FONT_SECTION_TITLE = new Font(Font.HELVETICA, 11, Font.BOLD, BRAND_NAVY);
    private static final Font FONT_BODY_BOLD = new Font(Font.HELVETICA, 9, Font.BOLD, TEXT_DARK);
    private static final Font FONT_BODY = new Font(Font.HELVETICA, 9, Font.NORMAL, TEXT_DARK);
    private static final Font FONT_BODY_MUTED = new Font(Font.HELVETICA, 8, Font.NORMAL, TEXT_MUTED);
    private static final Font FONT_STATUS_BADGE = new Font(Font.HELVETICA, 10, Font.BOLD, TXT_STATUS_DELIVERED);
    private static final Font FONT_FOOTER = new Font(Font.HELVETICA, 8, Font.ITALIC, TEXT_MUTED);

    public byte[] generate(Parcel parcel) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();

            // 1. Header (Brand + Receipt Title + Metadata)
            document.add(createHeaderTable(parcel, writer));

            document.add(new Paragraph(" "));

            // 2. Barcode
            document.add(createBarcodeTable(parcel, writer));

            document.add(new Paragraph(" "));

            // 3. Addresses Section (Pickup / Sender vs Dropoff / Recipient)
            document.add(createAddressesTable(parcel));

            document.add(new Paragraph(" "));

            // 4. Parcel Specifications & Fee Breakdown
            document.add(createDetailsAndFeeTable(parcel));

            document.add(new Paragraph(" "));

            // 5. Status History Timeline & Proof Summary
            document.add(createStatusHistoryTable(parcel));

            document.add(new Paragraph(" "));

            // 6. Footer Note
            document.add(createFooter());

            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to render PDF delivery receipt", e);
        }

        return out.toByteArray();
    }

    private PdfPTable createHeaderTable(Parcel parcel, PdfWriter writer) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        try {
            table.setWidths(new float[]{60f, 40f});
        } catch (DocumentException ignored) {
        }

        // Left cell: Logo / Title
        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        Paragraph brand = new Paragraph("SwiftDrop", FONT_HEADER_TITLE);
        Paragraph tagline = new Paragraph("Express Courier & Parcel Delivery Management", FONT_HEADER_SUB);
        Paragraph docType = new Paragraph("OFFICIAL DELIVERY RECEIPT", new Font(Font.HELVETICA, 12, Font.BOLD, BRAND_BLUE));
        docType.setSpacingBefore(4);
        left.addElement(brand);
        left.addElement(tagline);
        left.addElement(docType);
        table.addCell(left);

        // Right cell: Receipt No & Issue Date
        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Paragraph date = new Paragraph("Issued: " + DATE_TIME_FMT.format(parcel.updatedAt()), FONT_BODY_MUTED);
        date.setAlignment(Element.ALIGN_RIGHT);
        Paragraph receiptId = new Paragraph("Receipt #: REC-" + parcel.id().toString().substring(0, 8).toUpperCase(), FONT_BODY_BOLD);
        receiptId.setAlignment(Element.ALIGN_RIGHT);
        Paragraph statusBadge = new Paragraph("Status: " + parcel.status().name(), FONT_STATUS_BADGE);
        statusBadge.setAlignment(Element.ALIGN_RIGHT);

        right.addElement(date);
        right.addElement(receiptId);
        right.addElement(statusBadge);
        table.addCell(right);

        return table;
    }

    private PdfPTable createBarcodeTable(Parcel parcel, PdfWriter writer) {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);

        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(BG_LIGHT);
        cell.setBorderColor(BORDER_GRAY);
        cell.setPadding(8);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        try {
            PdfContentByte cb = writer.getDirectContent();
            Barcode128 barcode = new Barcode128();
            barcode.setCode(parcel.trackingNumber().value());
            barcode.setCodeType(Barcode128.CODE128);
            barcode.setBarHeight(30f);
            barcode.setX(1.2f);
            com.lowagie.text.Image barcodeImage = barcode.createImageWithBarcode(cb, Color.BLACK, Color.BLACK);
            barcodeImage.setAlignment(Element.ALIGN_CENTER);
            cell.addElement(barcodeImage);
        } catch (Exception ignored) {
            Paragraph trackingFallback = new Paragraph("Tracking Number: " + parcel.trackingNumber().value(), FONT_BODY_BOLD);
            trackingFallback.setAlignment(Element.ALIGN_CENTER);
            cell.addElement(trackingFallback);
        }

        table.addCell(cell);
        return table;
    }

    private PdfPTable createAddressesTable(Parcel parcel) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        try {
            table.setWidths(new float[]{50f, 50f});
        } catch (DocumentException ignored) {
        }

        BookingDetails details = parcel.details();

        // Pickup / Sender
        PdfPCell pickupCell = new PdfPCell();
        pickupCell.setBackgroundColor(BG_LIGHT);
        pickupCell.setBorderColor(BORDER_GRAY);
        pickupCell.setPadding(8);

        Paragraph pickupTitle = new Paragraph("PICKUP / SENDER", FONT_SECTION_TITLE);
        pickupCell.addElement(pickupTitle);
        Address pickup = details.pickup();
        pickupCell.addElement(new Paragraph(pickup.line1(), FONT_BODY));
        if (pickup.line2() != null && !pickup.line2().isBlank()) {
            pickupCell.addElement(new Paragraph(pickup.line2(), FONT_BODY));
        }
        pickupCell.addElement(new Paragraph(pickup.city() + " " + (pickup.postalCode() != null ? pickup.postalCode() : ""), FONT_BODY));
        if (details.pickupZone() != null) {
            pickupCell.addElement(new Paragraph("Zone: " + details.pickupZone(), FONT_BODY_MUTED));
        }
        table.addCell(pickupCell);

        // Dropoff / Recipient
        PdfPCell dropoffCell = new PdfPCell();
        dropoffCell.setBackgroundColor(BG_LIGHT);
        dropoffCell.setBorderColor(BORDER_GRAY);
        dropoffCell.setPadding(8);

        Paragraph dropoffTitle = new Paragraph("DROPOFF / RECIPIENT", FONT_SECTION_TITLE);
        dropoffCell.addElement(dropoffTitle);
        dropoffCell.addElement(new Paragraph("Name: " + details.recipient().name(), FONT_BODY_BOLD));
        Address dropoff = details.dropoff();
        dropoffCell.addElement(new Paragraph(dropoff.line1(), FONT_BODY));
        if (dropoff.line2() != null && !dropoff.line2().isBlank()) {
            dropoffCell.addElement(new Paragraph(dropoff.line2(), FONT_BODY));
        }
        dropoffCell.addElement(new Paragraph(dropoff.city() + " " + (dropoff.postalCode() != null ? dropoff.postalCode() : ""), FONT_BODY));
        dropoffCell.addElement(new Paragraph("Phone: " + details.recipient().phone(), FONT_BODY));
        if (details.recipient().email() != null && !details.recipient().email().isBlank()) {
            dropoffCell.addElement(new Paragraph("Email: " + details.recipient().email(), FONT_BODY_MUTED));
        }
        if (details.dropoffZone() != null) {
            dropoffCell.addElement(new Paragraph("Zone: " + details.dropoffZone(), FONT_BODY_MUTED));
        }
        table.addCell(dropoffCell);

        return table;
    }

    private PdfPTable createDetailsAndFeeTable(Parcel parcel) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        try {
            table.setWidths(new float[]{55f, 45f});
        } catch (DocumentException ignored) {
        }

        BookingDetails details = parcel.details();

        // Left: Package Details
        PdfPCell pkgCell = new PdfPCell();
        pkgCell.setBorderColor(BORDER_GRAY);
        pkgCell.setPadding(8);
        pkgCell.addElement(new Paragraph("PARCEL SPECIFICATIONS", FONT_SECTION_TITLE));

        Paragraph weight = new Paragraph("Weight: " + details.weightKg() + " kg", FONT_BODY);
        Paragraph size = new Paragraph("Size Class: " + details.sizeClass(), FONT_BODY);
        Paragraph svc = new Paragraph("Service Level: " + details.serviceLevel(), FONT_BODY);
        Paragraph created = new Paragraph("Booked At: " + DATE_TIME_FMT.format(parcel.createdAt()), FONT_BODY);
        pkgCell.addElement(weight);
        pkgCell.addElement(size);
        pkgCell.addElement(svc);
        pkgCell.addElement(created);

        if (details.notes() != null && !details.notes().isBlank()) {
            pkgCell.addElement(new Paragraph("Special Instructions: " + details.notes(), FONT_BODY_MUTED));
        }
        table.addCell(pkgCell);

        // Right: Fee Breakdown
        PdfPCell feeCell = new PdfPCell();
        feeCell.setBackgroundColor(BG_LIGHT);
        feeCell.setBorderColor(BORDER_GRAY);
        feeCell.setPadding(8);
        feeCell.addElement(new Paragraph("PAYMENT BREAKDOWN", FONT_SECTION_TITLE));

        Paragraph baseFee = new Paragraph("Delivery Charge: " + details.fee().currency() + " " + details.fee().amount().toPlainString(), FONT_BODY);
        Paragraph tax = new Paragraph("Applicable Taxes: Included", FONT_BODY_MUTED);
        Paragraph total = new Paragraph("Total Paid: " + details.fee().currency() + " " + details.fee().amount().toPlainString(),
                new Font(Font.HELVETICA, 11, Font.BOLD, BRAND_NAVY));
        total.setSpacingBefore(6);

        feeCell.addElement(baseFee);
        feeCell.addElement(tax);
        feeCell.addElement(total);

        if (details.quoteId() != null) {
            feeCell.addElement(new Paragraph("Quote ID: " + details.quoteId().toString().substring(0, 8), FONT_BODY_MUTED));
        }
        table.addCell(feeCell);

        return table;
    }

    private PdfPTable createStatusHistoryTable(Parcel parcel) {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        try {
            table.setWidths(new float[]{25f, 25f, 20f, 30f});
        } catch (DocumentException ignored) {
        }

        // Section header spanning all 4 columns
        PdfPCell titleCell = new PdfPCell(new Phrase("DELIVERY LIFECYCLE & STATUS TIMELINE", FONT_SECTION_TITLE));
        titleCell.setColspan(4);
        titleCell.setBorderColor(BORDER_GRAY);
        titleCell.setBackgroundColor(BG_LIGHT);
        titleCell.setPadding(6);
        table.addCell(titleCell);

        // Column headers
        table.addCell(createThCell("Timestamp (UTC)"));
        table.addCell(createThCell("Status"));
        table.addCell(createThCell("Updated By"));
        table.addCell(createThCell("Remarks / Notes"));

        List<StatusChange> history = parcel.history();
        if (history != null && !history.isEmpty()) {
            for (StatusChange sc : history) {
                table.addCell(createTdCell(DATE_TIME_FMT.format(sc.occurredAt())));
                table.addCell(createTdCell(sc.toStatus().name()));
                table.addCell(createTdCell(sc.actorRole() != null ? sc.actorRole().name() : "SYSTEM"));
                String remark = sc.note() != null && !sc.note().isBlank() ? sc.note() : "-";
                table.addCell(createTdCell(remark));
            }
        } else {
            PdfPCell emptyCell = new PdfPCell(new Phrase("No lifecycle history recorded.", FONT_BODY_MUTED));
            emptyCell.setColspan(4);
            emptyCell.setPadding(6);
            table.addCell(emptyCell);
        }

        // Delivery proof summary row if delivered
        if (parcel.status() == ParcelStatus.DELIVERED) {
            PdfPCell proofCell = new PdfPCell();
            proofCell.setColspan(4);
            proofCell.setBackgroundColor(BG_STATUS_DELIVERED);
            proofCell.setBorderColor(BORDER_GRAY);
            proofCell.setPadding(6);

            Paragraph proofText = new Paragraph("✔ Delivery Proof Verified: Parcel delivered successfully to recipient.",
                    new Font(Font.HELVETICA, 9, Font.BOLD, TXT_STATUS_DELIVERED));
            proofCell.addElement(proofText);
            table.addCell(proofCell);
        }

        return table;
    }

    private PdfPCell createThCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FONT_BODY_BOLD));
        cell.setBackgroundColor(new Color(241, 245, 249));
        cell.setBorderColor(BORDER_GRAY);
        cell.setPadding(5);
        return cell;
    }

    private PdfPCell createTdCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FONT_BODY));
        cell.setBorderColor(BORDER_GRAY);
        cell.setPadding(5);
        return cell;
    }

    private Paragraph createFooter() {
        Paragraph footer = new Paragraph();
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.add(new Chunk("Thank you for shipping with SwiftDrop!\n", FONT_BODY_BOLD));
        footer.add(new Chunk("For inquiries or assistance, visit www.swiftdrop.lk or contact support@swiftdrop.lk\n", FONT_FOOTER));
        footer.add(new Chunk("This is an electronically generated delivery receipt and does not require a physical signature.", FONT_FOOTER));
        return footer;
    }
}
