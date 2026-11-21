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
import lk.swiftdrop.events.common.Types.ActorRole;
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
 * Generates a modern, clean, and elegant SwiftDrop Delivery Receipt PDF using OpenPDF.
 *
 * <p>Designed with a modern design system:
 * <ul>
 *   <li>Crisp brand header with top accent bar and status badge</li>
 *   <li>Unified tracking card with prominent tracking number &amp; sharp barcode</li>
 *   <li>Clean 2-column collection &amp; delivery destination route card</li>
 *   <li>Itemized package specifications and clear payment breakdown</li>
 *   <li>Clean borderless timeline table with friendly status labels</li>
 *   <li>Professional electronic receipt footer</li>
 * </ul>
 */
@Component
public class ReceiptPdfGenerator {

    private static final DateTimeFormatter DATE_TIME_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm 'UTC'").withZone(ZoneId.of("UTC"));

    // Modern Palette
    private static final Color BRAND_PRIMARY = new Color(37, 99, 235);     // #2563EB Vibrant Blue
    private static final Color BRAND_NAVY = new Color(15, 23, 42);          // #0F172A Slate 900
    private static final Color TEXT_MAIN = new Color(30, 41, 59);          // #1E293B Slate 800
    private static final Color TEXT_MUTED = new Color(100, 116, 139);      // #64748B Slate 500
    private static final Color TEXT_LIGHT = new Color(148, 163, 184);      // #94A3B8 Slate 400
    private static final Color CARD_BG = new Color(248, 250, 252);         // #F8FAFC Slate 50
    private static final Color CARD_BORDER = new Color(226, 232, 240);     // #E2E8F0 Slate 200
    private static final Color DIVIDER_LINE = new Color(241, 245, 249);    // #F1F5F9 Slate 100

    // Status Colors (Delivered = Green, Active = Blue, Cancelled = Rose)
    private static final Color STATUS_GREEN_BG = new Color(236, 253, 245);  // #ECFDF5
    private static final Color STATUS_GREEN_TXT = new Color(4, 120, 87);    // #047857
    private static final Color STATUS_BLUE_BG = new Color(239, 246, 255);   // #EFF6FF
    private static final Color STATUS_BLUE_TXT = new Color(29, 78, 216);    // #1D4ED8
    private static final Color STATUS_AMBER_BG = new Color(254, 243, 199);  // #FEF3C7
    private static final Color STATUS_AMBER_TXT = new Color(180, 83, 9);    // #B45309
    private static final Color STATUS_GRAY_BG = new Color(241, 245, 249);   // #F1F5F9
    private static final Color STATUS_GRAY_TXT = new Color(51, 65, 85);     // #334155

    // Typography
    private static final Font FONT_BRAND = new Font(Font.HELVETICA, 20, Font.BOLD, BRAND_NAVY);
    private static final Font FONT_BRAND_SUB = new Font(Font.HELVETICA, 7, Font.BOLD, BRAND_PRIMARY);
    private static final Font FONT_DOC_TITLE = new Font(Font.HELVETICA, 8, Font.BOLD, TEXT_MUTED);
    private static final Font FONT_CARD_TITLE = new Font(Font.HELVETICA, 8, Font.BOLD, BRAND_PRIMARY);
    private static final Font FONT_SECTION_HEADER = new Font(Font.HELVETICA, 9, Font.BOLD, BRAND_NAVY);

    private static final Font FONT_TN_LABEL = new Font(Font.HELVETICA, 7, Font.BOLD, TEXT_MUTED);
    private static final Font FONT_TN_VALUE = new Font(Font.HELVETICA, 13, Font.BOLD, BRAND_NAVY);

    private static final Font FONT_NAME = new Font(Font.HELVETICA, 9, Font.BOLD, TEXT_MAIN);
    private static final Font FONT_REGULAR = new Font(Font.HELVETICA, 8, Font.NORMAL, TEXT_MAIN);
    private static final Font FONT_MUTED = new Font(Font.HELVETICA, 7, Font.NORMAL, TEXT_MUTED);
    private static final Font FONT_BOLD = new Font(Font.HELVETICA, 8, Font.BOLD, TEXT_MAIN);

    private static final Font FONT_AMOUNT = new Font(Font.HELVETICA, 13, Font.BOLD, BRAND_PRIMARY);
    private static final Font FONT_PAID_BADGE = new Font(Font.HELVETICA, 8, Font.BOLD, STATUS_GREEN_TXT);

    private static final Font FONT_TH = new Font(Font.HELVETICA, 7, Font.BOLD, TEXT_MUTED);
    private static final Font FONT_TD = new Font(Font.HELVETICA, 7, Font.NORMAL, TEXT_MAIN);
    private static final Font FONT_TD_BOLD = new Font(Font.HELVETICA, 7, Font.BOLD, TEXT_MAIN);

    private static final Font FONT_FOOTER_MAIN = new Font(Font.HELVETICA, 8, Font.BOLD, TEXT_MAIN);
    private static final Font FONT_FOOTER_SUB = new Font(Font.HELVETICA, 7, Font.NORMAL, TEXT_MUTED);
    private static final Font FONT_FOOTER_NOTE = new Font(Font.HELVETICA, 6, Font.ITALIC, TEXT_LIGHT);

    public byte[] generate(Parcel parcel) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // A4 page with clean 32pt margins
        Document document = new Document(PageSize.A4, 32, 32, 28, 28);

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();

            // 1. Sleek top brand accent line
            document.add(createTopAccentBar());
            document.add(createSpacing(6f));

            // 2. Header (Logo + Document Metadata + Status Pill)
            document.add(createHeader(parcel));
            document.add(createSpacing(10f));

            // 3. Tracking Number & Barcode Modern Hero Card
            document.add(createTrackingHeroCard(parcel, writer));
            document.add(createSpacing(10f));

            // 4. Addresses (Collection / Sender vs Delivery / Recipient)
            document.add(createAddressesCard(parcel));
            document.add(createSpacing(10f));

            // 5. Package Specifications & Payment Breakdown
            document.add(createPackageAndPaymentCard(parcel));
            document.add(createSpacing(10f));

            // 6. Delivery Timeline & Lifecycle History
            document.add(createTimelineSection(parcel));
            document.add(createSpacing(12f));

            // 7. Modern Minimalist Footer
            document.add(createFooter());

            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to render PDF delivery receipt", e);
        }

        return out.toByteArray();
    }

    private Paragraph createSpacing(float height) {
        Paragraph p = new Paragraph(" ", new Font(Font.HELVETICA, 1));
        p.setSpacingBefore(height);
        p.setLeading(height);
        return p;
    }

    private PdfPTable createTopAccentBar() {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(BRAND_PRIMARY);
        cell.setFixedHeight(3f);
        cell.setBorder(Rectangle.NO_BORDER);
        table.addCell(cell);
        return table;
    }

    private PdfPTable createHeader(Parcel parcel) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        try {
            table.setWidths(new float[]{55f, 45f});
        } catch (DocumentException ignored) {}

        // Left: SwiftDrop Brand & Subtitle
        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        left.setPadding(0);

        Paragraph brand = new Paragraph("SwiftDrop", FONT_BRAND);
        brand.setLeading(20f);
        Paragraph tagline = new Paragraph("EXPRESS LOGISTICS & DELIVERY RECEIPT", FONT_BRAND_SUB);
        tagline.setSpacingBefore(3f);
        tagline.setLeading(9f);

        left.addElement(brand);
        left.addElement(tagline);
        table.addCell(left);

        // Right: Receipt No, Date, Status Badge
        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setPadding(0);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);

        PdfPTable metaTable = new PdfPTable(1);
        metaTable.setWidthPercentage(100);

        // Status badge pill
        PdfPCell statusCell = createStatusBadgeCell(parcel.status());
        metaTable.addCell(statusCell);

        // Receipt # and Issued Date
        PdfPCell metaCell = new PdfPCell();
        metaCell.setBorder(Rectangle.NO_BORDER);
        metaCell.setPaddingTop(4f);
        metaCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        String shortId = parcel.id().toString().substring(0, 8).toUpperCase();
        Paragraph receiptNo = new Paragraph("Receipt #: REC-" + shortId, FONT_DOC_TITLE);
        receiptNo.setAlignment(Element.ALIGN_RIGHT);
        receiptNo.setLeading(10f);

        Paragraph date = new Paragraph("Issued: " + DATE_TIME_FMT.format(parcel.updatedAt()), FONT_MUTED);
        date.setAlignment(Element.ALIGN_RIGHT);
        date.setLeading(10f);

        metaCell.addElement(receiptNo);
        metaCell.addElement(date);
        metaTable.addCell(metaCell);

        right.addElement(metaTable);
        table.addCell(right);

        return table;
    }

    private PdfPCell createStatusBadgeCell(ParcelStatus status) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Color bg = STATUS_GRAY_BG;
        Color txtColor = STATUS_GRAY_TXT;
        String label = "CONFIRMED";

        switch (status) {
            case DELIVERED -> {
                bg = STATUS_GREEN_BG;
                txtColor = STATUS_GREEN_TXT;
                label = "DELIVERED";
            }
            case OUT_FOR_DELIVERY -> {
                bg = STATUS_AMBER_BG;
                txtColor = STATUS_AMBER_TXT;
                label = "OUT FOR DELIVERY";
            }
            case IN_TRANSIT, PICKED_UP -> {
                bg = STATUS_BLUE_BG;
                txtColor = STATUS_BLUE_TXT;
                label = "IN TRANSIT";
            }
            case CANCELLED -> {
                bg = new Color(254, 242, 242);
                txtColor = new Color(185, 28, 28);
                label = "CANCELLED";
            }
            default -> {
                bg = STATUS_GRAY_BG;
                txtColor = STATUS_GRAY_TXT;
                label = "BOOKING CONFIRMED";
            }
        }

        PdfPTable pill = new PdfPTable(1);
        pill.setHorizontalAlignment(Element.ALIGN_RIGHT);
        pill.setTotalWidth(120f);
        pill.setLockedWidth(true);

        PdfPCell pillCell = new PdfPCell();
        pillCell.setBackgroundColor(bg);
        pillCell.setBorder(Rectangle.BOX);
        pillCell.setBorderColor(new Color(txtColor.getRed(), txtColor.getGreen(), txtColor.getBlue(), 60));
        pillCell.setBorderWidth(0.5f);
        pillCell.setPaddingTop(3f);
        pillCell.setPaddingBottom(3f);
        pillCell.setPaddingLeft(8f);
        pillCell.setPaddingRight(8f);
        pillCell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph p = new Paragraph(label, new Font(Font.HELVETICA, 7.5f, Font.BOLD, txtColor));
        p.setAlignment(Element.ALIGN_CENTER);
        pillCell.addElement(p);
        pill.addCell(pillCell);

        cell.addElement(pill);
        return cell;
    }

    private PdfPTable createTrackingHeroCard(Parcel parcel, PdfWriter writer) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        try {
            table.setWidths(new float[]{62f, 38f});
        } catch (DocumentException ignored) {}

        // Left Cell: Tracking Number & Metadata
        PdfPCell left = new PdfPCell();
        left.setBackgroundColor(CARD_BG);
        left.setBorderColor(CARD_BORDER);
        left.setBorderWidth(0.75f);
        left.setPadding(9f);

        Paragraph lbl = new Paragraph("TRACKING NUMBER", FONT_TN_LABEL);
        lbl.setLeading(8f);
        left.addElement(lbl);

        Paragraph tn = new Paragraph(parcel.trackingNumber().value(), FONT_TN_VALUE);
        tn.setSpacingBefore(2f);
        tn.setLeading(15f);
        left.addElement(tn);

        BookingDetails d = parcel.details();
        String sizeDesc = switch (d.sizeClass()) {
            case S -> "Small (Envelope/Pouch)";
            case M -> "Medium (Standard Box)";
            case L -> "Large (Carton)";
            case XL -> "Extra Large";
        };

        String svcDesc = switch (d.serviceLevel()) {
            case EXPRESS -> "Express Delivery (Same/Next Day)";
            case STANDARD -> "Standard Delivery (1-2 Days)";
        };

        Paragraph meta = new Paragraph(svcDesc + "  \u2022  " + d.weightKg() + " kg  \u2022  " + sizeDesc, FONT_MUTED);
        meta.setSpacingBefore(4f);
        meta.setLeading(9f);
        left.addElement(meta);

        table.addCell(left);

        // Right Cell: Clean Compact Barcode
        PdfPCell right = new PdfPCell();
        right.setBackgroundColor(CARD_BG);
        right.setBorderColor(CARD_BORDER);
        right.setBorderWidth(0.75f);
        right.setPadding(8f);
        right.setHorizontalAlignment(Element.ALIGN_CENTER);
        right.setVerticalAlignment(Element.ALIGN_MIDDLE);

        try {
            PdfContentByte cb = writer.getDirectContent();
            Barcode128 barcode = new Barcode128();
            barcode.setCode(parcel.trackingNumber().value());
            barcode.setCodeType(Barcode128.CODE128);
            barcode.setBarHeight(18f);
            barcode.setX(0.9f);
            barcode.setSize(7f);
            barcode.setBaseline(8f);
            com.lowagie.text.Image barcodeImage = barcode.createImageWithBarcode(cb, Color.BLACK, Color.BLACK);
            barcodeImage.setAlignment(Element.ALIGN_CENTER);
            right.addElement(barcodeImage);

            Paragraph trackHint = new Paragraph("Track live at https://swiftdrop.lk", FONT_MUTED);
            trackHint.setAlignment(Element.ALIGN_CENTER);
            trackHint.setSpacingBefore(2f);
            trackHint.setLeading(7f);
            right.addElement(trackHint);
        } catch (Exception ignored) {
            Paragraph fallback = new Paragraph(parcel.trackingNumber().value(), FONT_TN_VALUE);
            fallback.setAlignment(Element.ALIGN_CENTER);
            right.addElement(fallback);
        }

        table.addCell(right);
        return table;
    }

    private PdfPTable createAddressesCard(Parcel parcel) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        try {
            table.setWidths(new float[]{50f, 50f});
        } catch (DocumentException ignored) {}

        BookingDetails details = parcel.details();

        // Left: Collection / Sender
        PdfPCell pickupCell = new PdfPCell();
        pickupCell.setBackgroundColor(CARD_BG);
        pickupCell.setBorderColor(CARD_BORDER);
        pickupCell.setBorderWidth(0.75f);
        pickupCell.setPadding(9f);

        Paragraph pickupTitle = new Paragraph("COLLECTION / SENDER", FONT_CARD_TITLE);
        pickupTitle.setLeading(9f);
        pickupCell.addElement(pickupTitle);

        Address pickup = details.pickup();
        Paragraph pickupL1 = new Paragraph(formatTitleCase(pickup.line1()), FONT_NAME);
        pickupL1.setSpacingBefore(3f);
        pickupL1.setLeading(11f);
        pickupCell.addElement(pickupL1);

        if (pickup.line2() != null && !pickup.line2().isBlank()) {
            Paragraph p2 = new Paragraph(formatTitleCase(pickup.line2()), FONT_REGULAR);
            p2.setLeading(10f);
            pickupCell.addElement(p2);
        }

        String pickupCity = formatTitleCase(pickup.city()) + (pickup.postalCode() != null && !pickup.postalCode().isBlank() ? " " + pickup.postalCode() : "");
        Paragraph pCity = new Paragraph(pickupCity + ", " + formatTitleCase(pickup.district()), FONT_REGULAR);
        pCity.setLeading(10f);
        pickupCell.addElement(pCity);

        table.addCell(pickupCell);

        // Right: Delivery Destination / Recipient
        PdfPCell dropoffCell = new PdfPCell();
        dropoffCell.setBackgroundColor(CARD_BG);
        dropoffCell.setBorderColor(CARD_BORDER);
        dropoffCell.setBorderWidth(0.75f);
        dropoffCell.setPadding(9f);

        Paragraph dropoffTitle = new Paragraph("DELIVERY DESTINATION / RECIPIENT", FONT_CARD_TITLE);
        dropoffTitle.setLeading(9f);
        dropoffCell.addElement(dropoffTitle);

        Paragraph recName = new Paragraph(formatTitleCase(details.recipient().name()), FONT_NAME);
        recName.setSpacingBefore(3f);
        recName.setLeading(11f);
        dropoffCell.addElement(recName);

        Address dropoff = details.dropoff();
        Paragraph dropoffL1 = new Paragraph(formatTitleCase(dropoff.line1()), FONT_REGULAR);
        dropoffL1.setLeading(10f);
        dropoffCell.addElement(dropoffL1);

        if (dropoff.line2() != null && !dropoff.line2().isBlank()) {
            Paragraph d2 = new Paragraph(formatTitleCase(dropoff.line2()), FONT_REGULAR);
            d2.setLeading(10f);
            dropoffCell.addElement(d2);
        }

        String dropoffCity = formatTitleCase(dropoff.city()) + (dropoff.postalCode() != null && !dropoff.postalCode().isBlank() ? " " + dropoff.postalCode() : "");
        Paragraph dCity = new Paragraph(dropoffCity + ", " + formatTitleCase(dropoff.district()), FONT_REGULAR);
        dCity.setLeading(10f);
        dropoffCell.addElement(dCity);

        Paragraph contact = new Paragraph("Phone: " + details.recipient().phone(), FONT_MUTED);
        contact.setSpacingBefore(2f);
        contact.setLeading(9f);
        if (details.recipient().email() != null && !details.recipient().email().isBlank()) {
            contact.add(new Chunk("  \u2022  " + details.recipient().email(), FONT_MUTED));
        }
        dropoffCell.addElement(contact);

        table.addCell(dropoffCell);
        return table;
    }

    private PdfPTable createPackageAndPaymentCard(Parcel parcel) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        try {
            table.setWidths(new float[]{50f, 50f});
        } catch (DocumentException ignored) {}

        BookingDetails details = parcel.details();

        // Left: Package Details
        PdfPCell pkgCell = new PdfPCell();
        pkgCell.setBackgroundColor(CARD_BG);
        pkgCell.setBorderColor(CARD_BORDER);
        pkgCell.setBorderWidth(0.75f);
        pkgCell.setPadding(9f);

        Paragraph pkgTitle = new Paragraph("PACKAGE SPECIFICATIONS", FONT_CARD_TITLE);
        pkgTitle.setLeading(9f);
        pkgCell.addElement(pkgTitle);

        PdfPTable grid = new PdfPTable(2);
        grid.setWidthPercentage(100);
        try {
            grid.setWidths(new float[]{40f, 60f});
        } catch (DocumentException ignored) {}

        String sizeLabel = switch (details.sizeClass()) {
            case S -> "Small (Class S)";
            case M -> "Medium (Class M)";
            case L -> "Large (Class L)";
            case XL -> "Extra Large (Class XL)";
        };

        String serviceLabel = switch (details.serviceLevel()) {
            case EXPRESS -> "Express Delivery";
            case STANDARD -> "Standard Delivery";
        };

        addKeyValRow(grid, "Weight", details.weightKg() + " kg");
        addKeyValRow(grid, "Package Size", sizeLabel);
        addKeyValRow(grid, "Service Level", serviceLabel);
        addKeyValRow(grid, "Booking Date", DATE_TIME_FMT.format(parcel.createdAt()));

        pkgCell.addElement(grid);

        if (details.notes() != null && !details.notes().isBlank()) {
            Paragraph notePara = new Paragraph("Note: " + details.notes(), FONT_MUTED);
            notePara.setSpacingBefore(3f);
            notePara.setLeading(8f);
            pkgCell.addElement(notePara);
        }

        table.addCell(pkgCell);

        // Right: Payment Summary
        PdfPCell feeCell = new PdfPCell();
        feeCell.setBackgroundColor(CARD_BG);
        feeCell.setBorderColor(CARD_BORDER);
        feeCell.setBorderWidth(0.75f);
        feeCell.setPadding(9f);

        Paragraph feeTitle = new Paragraph("PAYMENT BREAKDOWN", FONT_CARD_TITLE);
        feeTitle.setLeading(9f);
        feeCell.addElement(feeTitle);

        PdfPTable feeGrid = new PdfPTable(2);
        feeGrid.setWidthPercentage(100);
        try {
            feeGrid.setWidths(new float[]{55f, 45f});
        } catch (DocumentException ignored) {}

        addKeyValRow(feeGrid, "Delivery Charge", details.fee().currency() + " " + details.fee().amount().toPlainString());
        addKeyValRow(feeGrid, "Applicable Taxes", "Included");

        feeCell.addElement(feeGrid);

        // Total Paid row with divider
        Paragraph totalPara = new Paragraph();
        totalPara.setSpacingBefore(4f);
        totalPara.setLeading(14f);
        totalPara.add(new Chunk("Total Paid:  ", FONT_NAME));
        totalPara.add(new Chunk(details.fee().currency() + " " + details.fee().amount().toPlainString(), FONT_AMOUNT));
        feeCell.addElement(totalPara);

        Paragraph paidStatus = new Paragraph("Payment Status: Fully Settled", FONT_PAID_BADGE);
        paidStatus.setSpacingBefore(2f);
        paidStatus.setLeading(8f);
        feeCell.addElement(paidStatus);

        table.addCell(feeCell);
        return table;
    }

    private void addKeyValRow(PdfPTable table, String key, String val) {
        PdfPCell kCell = new PdfPCell(new Phrase(key, FONT_MUTED));
        kCell.setBorder(Rectangle.NO_BORDER);
        kCell.setPaddingTop(1.5f);
        kCell.setPaddingBottom(1.5f);
        kCell.setPaddingLeft(0);
        table.addCell(kCell);

        PdfPCell vCell = new PdfPCell(new Phrase(val, FONT_BOLD));
        vCell.setBorder(Rectangle.NO_BORDER);
        vCell.setPaddingTop(1.5f);
        vCell.setPaddingBottom(1.5f);
        vCell.setPaddingRight(0);
        table.addCell(vCell);
    }

    private PdfPTable createTimelineSection(Parcel parcel) {
        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        try {
            table.setWidths(new float[]{25f, 25f, 50f});
        } catch (DocumentException ignored) {}

        // Section header spanning all 3 columns
        PdfPCell titleCell = new PdfPCell();
        titleCell.setColspan(3);
        titleCell.setBorder(Rectangle.BOTTOM);
        titleCell.setBorderColor(CARD_BORDER);
        titleCell.setBorderWidth(1f);
        titleCell.setPaddingBottom(4f);
        titleCell.setPaddingLeft(0);
        titleCell.addElement(new Paragraph("DELIVERY LIFECYCLE & STATUS TIMELINE", FONT_SECTION_HEADER));
        table.addCell(titleCell);

        // Column headers
        table.addCell(createThCell("Timestamp (UTC)"));
        table.addCell(createThCell("Status"));
        table.addCell(createThCell("Remarks / Activity"));

        List<StatusChange> history = parcel.history();
        if (history != null && !history.isEmpty()) {
            boolean alternate = false;
            for (StatusChange sc : history) {
                Color rowBg = alternate ? new Color(250, 250, 250) : Color.WHITE;
                table.addCell(createTdCell(DATE_TIME_FMT.format(sc.occurredAt()), rowBg, false));
                table.addCell(createTdCell(formatFriendlyStatus(sc.toStatus()), rowBg, true));
                String remark = formatFriendlyRemark(sc);
                table.addCell(createTdCell(remark, rowBg, false));
                alternate = !alternate;
            }
        } else {
            PdfPCell emptyCell = new PdfPCell(new Phrase("No lifecycle events recorded yet.", FONT_MUTED));
            emptyCell.setColspan(3);
            emptyCell.setBorder(Rectangle.BOTTOM);
            emptyCell.setBorderColor(DIVIDER_LINE);
            emptyCell.setPadding(6f);
            table.addCell(emptyCell);
        }

        // Delivery proof banner if delivered
        if (parcel.status() == ParcelStatus.DELIVERED) {
            PdfPCell proofCell = new PdfPCell();
            proofCell.setColspan(3);
            proofCell.setBackgroundColor(STATUS_GREEN_BG);
            proofCell.setBorder(Rectangle.BOX);
            proofCell.setBorderColor(new Color(STATUS_GREEN_TXT.getRed(), STATUS_GREEN_TXT.getGreen(), STATUS_GREEN_TXT.getBlue(), 80));
            proofCell.setBorderWidth(0.5f);
            proofCell.setPadding(6f);

            Paragraph proofText = new Paragraph("Verified Delivery: Successfully delivered to recipient.",
                    new Font(Font.HELVETICA, 8, Font.BOLD, STATUS_GREEN_TXT));
            proofCell.addElement(proofText);
            table.addCell(proofCell);
        }

        return table;
    }

    private PdfPCell createThCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FONT_TH));
        cell.setBackgroundColor(new Color(241, 245, 249));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(CARD_BORDER);
        cell.setBorderWidth(0.75f);
        cell.setPadding(4f);
        return cell;
    }

    private PdfPCell createTdCell(String text, Color bg, boolean bold) {
        PdfPCell cell = new PdfPCell(new Phrase(text, bold ? FONT_TD_BOLD : FONT_TD));
        cell.setBackgroundColor(bg);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(DIVIDER_LINE);
        cell.setBorderWidth(0.5f);
        cell.setPadding(4.5f);
        return cell;
    }

    private String formatFriendlyStatus(ParcelStatus status) {
        return switch (status) {
            case CREATED -> "Booking Confirmed";
            case PICKED_UP -> "Picked Up";
            case IN_TRANSIT -> "In Transit";
            case OUT_FOR_DELIVERY -> "Out for Delivery";
            case DELIVERED -> "Delivered";
            case FAILED -> "Delivery Attempted";
            case CANCELLED -> "Cancelled";
            default -> status.name();
        };
    }

    private String formatFriendlyRemark(StatusChange sc) {
        if (sc.note() != null && !sc.note().isBlank()) {
            return sc.note();
        }
        if (sc.actorRole() == ActorRole.CUSTOMER) {
            return "Delivery booking created by sender";
        }
        if (sc.actorRole() == ActorRole.COURIER) {
            return "Status update from assigned courier";
        }
        return "Processed by SwiftDrop operations";
    }

    private String formatTitleCase(String text) {
        if (text == null || text.isBlank()) return "";
        String[] words = text.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (word.length() > 0) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    sb.append(word.substring(1).toLowerCase());
                }
            }
            if (i < words.length - 1) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    private Paragraph createFooter() {
        Paragraph footer = new Paragraph();
        footer.setAlignment(Element.ALIGN_CENTER);

        footer.add(new Chunk("Thank you for choosing SwiftDrop!\n", FONT_FOOTER_MAIN));
        footer.add(new Chunk("For tracking and assistance, visit https://swiftdrop.lk or email support@swiftdrop.lk\n", FONT_FOOTER_SUB));
        footer.add(new Chunk("This is an electronically generated delivery receipt and does not require a physical signature.", FONT_FOOTER_NOTE));
        return footer;
    }
}
