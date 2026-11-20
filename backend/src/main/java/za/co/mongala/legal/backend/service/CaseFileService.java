package za.co.mongala.legal.backend.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import za.co.mongala.legal.backend.service.BillingService.CaseStatement;
import za.co.mongala.legal.backend.service.TrustAccountingService.TrustStatement;
import za.co.mongala.legal.model.Case;
import za.co.mongala.legal.model.Deadline;
import za.co.mongala.legal.model.Summary;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One-page A4 case file: summary, upcoming deadlines and billing on a single sheet.
 * Used for printing, or for handing a file to a client or a court clerk.
 */
public class CaseFileService {

    private static final float MARGIN = 40f;
    private static final float WIDTH = PDRectangle.A4.getWidth() - 2 * MARGIN;
    private static final float BOTTOM = 56f;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.ENGLISH);

    public byte[] render(Case c, Summary approved, List<Deadline> upcoming, CaseStatement st,
                         TrustStatement trust, LocalDateTime generatedAt) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                Writer w = new Writer(cs);
                float top = PDRectangle.A4.getHeight();

                // header band
                cs.setNonStrokingColor(0x16, 0x23, 0x3C);
                cs.addRect(0, top - 52, PDRectangle.A4.getWidth(), 52);
                cs.fill();
                w.at(MARGIN, top - 33).text("LegalCase ERP  |  Case file", PDType1Font.HELVETICA_BOLD, 15, 0xFFFFFF);
                w.at(MARGIN, top - 46).text("Confidential. Prepared for the attorney and the client named below.",
                        PDType1Font.HELVETICA, 8, 0xC9D3E8);

                // case identity
                w.at(MARGIN, top - 80).text(c.getReference() + "   " + safe(c.getTitle()),
                        PDType1Font.HELVETICA_BOLD, 13, 0x16233C);
                String status = c.getStatus() == null ? "Not recorded" : c.getStatus().getDisplayName();
                String opened = c.getOpenedDate() == null ? "Not recorded" : c.getOpenedDate().format(DATE);
                w.at(MARGIN, top - 98).text("Client: " + safe(c.getClientName())
                                + "     Opposing party: " + safe(c.getOpposingParty()),
                        PDType1Font.HELVETICA, 9, 0x24304A);
                w.at(MARGIN, top - 111).text("Matter: " + safe(c.getMatterType())
                                + "     Court: " + safe(c.getCourt())
                                + "     Status: " + status + "     Opened: " + opened,
                        PDType1Font.HELVETICA, 9, 0x24304A);
                w.at(MARGIN, top - 124).text("Amount involved: " + money(c.getAmountInvolved()),
                        PDType1Font.HELVETICA, 9, 0x24304A);

                float y = top - 150;
                y = section(cs, w, y, "Summary (approved by attorney)");
                if (approved == null) {
                    y = w.paragraph(y, "No approved summary yet.", 9, WIDTH);
                } else {
                    String text = String.join("\n\n",
                            nz(approved.getParagraph1()), nz(approved.getParagraph2()), nz(approved.getParagraph3()));
                    y = w.paragraph(y, text.trim(), 9, WIDTH);
                }

                y -= 8;
                y = section(cs, w, y, "Upcoming deadlines");
                if (upcoming == null || upcoming.isEmpty()) {
                    y = w.paragraph(y, "No upcoming deadlines.", 9, WIDTH);
                } else {
                    for (Deadline d : upcoming.stream().limit(8).toList()) {
                        long days = d.daysLeft();
                        String line = d.getDate().format(DATE) + "   " + safe(d.getTitle())
                                + "   (" + (days == 0 ? "today" : "in " + days + " days") + ")";
                        y = w.line(y, line, PDType1Font.HELVETICA, 9, 0x24304A);
                    }
                }

                y -= 8;
                y = section(cs, w, y, "Billing and trust");
                y = w.line(y, "Hours recorded: " + String.format(Locale.US, "%.2f", st.totalHours())
                        + "     Fees: " + money(st.feesSubtotal())
                        + "     Disbursements: " + money(st.disbursementsSubtotal()), PDType1Font.HELVETICA, 9, 0x24304A);
                y = w.line(y, "Subtotal excluding VAT: " + money(st.subtotalExVat())
                        + "     VAT (15%): " + money(st.vatAmount())
                        + "     Total including VAT: " + money(st.totalInclVat()), PDType1Font.HELVETICA_BOLD, 9, 0x16233C);
                y = w.line(y, "Trust credit available: " + money(trust.trustBalance())
                        + "     Trust deposits: " + money(trust.totalDeposits())
                        + "     Transfers out: " + money(trust.totalTransfersOut()), PDType1Font.HELVETICA, 9, 0x24304A);

                cs.setStrokingColor(0xC9, 0xA2, 0x27);
                cs.setLineWidth(0.6f);
                cs.moveTo(MARGIN, 44);
                cs.lineTo(PDRectangle.A4.getWidth() - MARGIN, 44);
                cs.stroke();
                w.at(MARGIN, 32).text("Generated " + generatedAt.format(STAMP)
                        + "  |  One page summary. The full record is kept in LegalCase ERP.",
                        PDType1Font.HELVETICA, 7.5f, 0x66738C);
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private float section(PDPageContentStream cs, Writer w, float y, String title) throws IOException {
        w.at(MARGIN, y).text(title.toUpperCase(Locale.ROOT), PDType1Font.HELVETICA_BOLD, 9.5f, 0x16233C);
        cs.setStrokingColor(0xDC, 0xE3, 0xEE);
        cs.setLineWidth(0.5f);
        cs.moveTo(MARGIN, y - 4);
        cs.lineTo(PDRectangle.A4.getWidth() - MARGIN, y - 4);
        cs.stroke();
        return y - 18;
    }

    private static String money(double v) {
        return "R " + String.format(Locale.US, "%,.2f", v);
    }

    private static String nz(String s) { return s == null ? "" : s; }

    /** PDF base-14 fonts only cover Latin-1 text, so anything else becomes "?". */
    static String safe(String s) {
        if (s == null || s.isBlank()) return "Not recorded";
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch == '\n' || ch == '\r' || ch == '\t') b.append(' ');
            else if (ch < 256 && ch >= 32) b.append(ch);
            else b.append('?');
        }
        return b.toString().trim();
    }

    /** Small text helper: writes lines and wraps long text to the given width. */
    private static final class Writer {
        private final PDPageContentStream cs;
        private float x;
        private float y;

        Writer(PDPageContentStream cs) { this.cs = cs; }

        Writer at(float x, float y) {
            this.x = x;
            this.y = y;
            return this;
        }

        Writer text(String s, PDType1Font font, float size, int rgb) throws IOException {
            cs.beginText();
            cs.setNonStrokingColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
            cs.setFont(font, size);
            cs.newLineAtOffset(x, y);
            cs.showText(safe(s));
            cs.endText();
            return this;
        }

        float line(float y, String s, PDType1Font font, float size, int rgb) throws IOException {
            String fitted = fit(s, font, size, WIDTH);
            at(MARGIN, y).text(fitted, font, size, rgb);
            return y - (size + 4);
        }

        float paragraph(float y, String text, float size, float width) throws IOException {
            PDType1Font font = PDType1Font.HELVETICA;
            float leading = size + 3.5f;
            for (String para : text.split("\n\n")) {
                for (String wrapped : wrap(safe(para), font, size, width)) {
                    if (y < BOTTOM) return y;
                    at(MARGIN, y).text(wrapped, font, size, 0x24304A);
                    y -= leading;
                }
                y -= 4;
            }
            return y;
        }

        private static List<String> wrap(String s, PDType1Font font, float size, float width) throws IOException {
            List<String> lines = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            for (String word : s.split(" ")) {
                String tryLine = cur.length() == 0 ? word : cur + " " + word;
                if (font.getStringWidth(tryLine) / 1000f * size > width && cur.length() > 0) {
                    lines.add(cur.toString());
                    cur = new StringBuilder(word);
                } else {
                    cur = new StringBuilder(tryLine);
                }
            }
            if (cur.length() > 0) lines.add(cur.toString());
            return lines;
        }

        private static String fit(String s, PDType1Font font, float size, float width) throws IOException {
            String t = s;
            while (font.getStringWidth(t) / 1000f * size > width && t.length() > 4) {
                t = t.substring(0, t.length() - 2);
            }
            return t.equals(s) ? s : t + "...";
        }
    }
}
