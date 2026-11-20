package za.co.mongala.legal.backend.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import za.co.mongala.legal.model.Case;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Legal time-tracking, disbursements and PDF fee-note / tax-invoice service.
 * Generates South African legal invoices (15% VAT) directly using Apache PDFBox.
 */
public class BillingService {

    public static final double SA_VAT_RATE = 0.15;

    public record BillingEntry(
            String id,
            String caseId,
            LocalDate entryDate,
            String entryType,     // "TIME" or "DISBURSEMENT"
            String description,
            double hours,
            double hourlyRate,
            double amount,
            String recordedBy,
            LocalDateTime createdAt
    ) {}

    public record CaseStatement(
            String caseId,
            List<BillingEntry> entries,
            double totalHours,
            double feesSubtotal,
            double disbursementsSubtotal,
            double subtotalExVat,
            double vatAmount,
            double totalInclVat
    ) {}

    public record FirmMatterBillingRow(
            String caseId,
            String reference,
            String title,
            String clientName,
            String status,
            double aiExtractedClaimAmount,
            double effectiveClaimAmount,
            String claimSource,          // "AI_EXTRACTED" | "CASE_REGISTER" | "NONE"
            double totalHours,
            double subtotalExVat,
            double vatAmount,
            double totalInclVat,
            double settledFromTrust,
            double directPayments,
            double totalSettled,
            double outstandingBalance,
            double availableTrustBalance,
            double feeToClaimPct,
            boolean highFeeRatioWarning,
            String paymentStatus         // "UNBILLED" | "UNPAID" | "PARTIALLY_PAID" | "PAID"
    ) {}

    public record FirmBillingOverview(
            List<FirmMatterBillingRow> matters,
            double firmHours,
            double firmBilledExVat,
            double firmVat,
            double firmBilledInclVat,
            double firmTotalSettled,
            double firmOutstanding,
            double firmTrustHeld,
            int paidMatters,
            int unpaidOrPartialMatters,
            int highFeeAlerts
    ) {}

    private final DataSource ds;

    public BillingService(DataSource ds) {
        this.ds = ds;
    }

    public BillingEntry addEntry(String caseId, LocalDate entryDate, String entryType,
                                 String description, double hours, double hourlyRate,
                                 double explicitAmount, String recordedBy) {
        String id = "be" + UUID.randomUUID().toString().substring(0, 10);
        String type = "DISBURSEMENT".equalsIgnoreCase(entryType) ? "DISBURSEMENT" : "TIME";
        double cleanHours = "TIME".equals(type) ? Math.max(0.1, hours) : 0.0;
        double cleanRate = "TIME".equals(type) ? Math.max(0.0, hourlyRate) : 0.0;
        double amt = "TIME".equals(type)
                ? Math.round(cleanHours * cleanRate * 100.0) / 100.0
                : Math.max(0.0, Math.round(explicitAmount * 100.0) / 100.0);
        LocalDate date = entryDate == null ? LocalDate.now() : entryDate;
        LocalDateTime now = LocalDateTime.now();

        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO billing_entries(id, case_id, entry_date, entry_type, description,
                                                 hours, hourly_rate, amount, recorded_by, created_at)
                     VALUES (?,?,?,?,?,?,?,?,?,?)""")) {
            ps.setString(1, id);
            ps.setString(2, caseId);
            ps.setDate(3, Date.valueOf(date));
            ps.setString(4, type);
            ps.setString(5, description == null || description.isBlank() ? "Legal attendance" : description.trim());
            ps.setDouble(6, cleanHours);
            ps.setDouble(7, cleanRate);
            ps.setDouble(8, amt);
            ps.setString(9, recordedBy == null ? "Attorney" : recordedBy);
            ps.setTimestamp(10, Timestamp.valueOf(now));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not insert billing entry", e);
        }
        return findById(id);
    }

    public BillingEntry findById(String id) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM billing_entries WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read billing entry", e);
        }
    }

    public boolean delete(String id) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM billing_entries WHERE id = ?")) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not delete billing entry", e);
        }
    }

    public CaseStatement statementForCase(String caseId) {
        List<BillingEntry> list = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM billing_entries WHERE case_id = ? ORDER BY entry_date ASC, created_at ASC")) {
            ps.setString(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not query billing entries", e);
        }

        double hours = 0.0, fees = 0.0, disb = 0.0;
        for (BillingEntry b : list) {
            if ("TIME".equals(b.entryType())) {
                hours += b.hours();
                fees += b.amount();
            } else {
                disb += b.amount();
            }
        }
        double sub = round2(fees + disb);
        double vat = round2(sub * SA_VAT_RATE);
        double total = round2(sub + vat);
        return new CaseStatement(caseId, list, round2(hours), round2(fees), round2(disb), sub, vat, total);
    }

    /** Lists all recorded billing entries across the firm (most recent first). */
    public List<BillingEntry> allEntries() {
        List<BillingEntry> list = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM billing_entries ORDER BY entry_date DESC, created_at DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) {
            throw new IllegalStateException("Could not query firm billing entries", e);
        }
        return list;
    }

    /** Builds a single matter's billing, trust-settlement & AI claim-quantum row. */
    public FirmMatterBillingRow matterRow(Case c, CaseService caseService, TrustAccountingService trust,
                                          DocumentService documents, ExtractionService extractions) {
        CaseStatement st = statementForCase(c.getId());
        double aiClaim = extractAiClaimAmount(c.getId(), documents, extractions);
        double effectiveClaim = aiClaim > 0 ? aiClaim : Math.max(0.0, c.getAmountInvolved());
        String claimSource = aiClaim > 0 ? "AI_EXTRACTED" : (c.getAmountInvolved() > 0 ? "CASE_REGISTER" : "NONE");

        double settledTrust = trust.transfersToBusinessForCase(c.getId());
        double directPaid = caseService.getDirectPayments(c.getId());
        double totalSettled = round2(settledTrust + directPaid);
        double outstanding = Math.max(0.0, round2(st.totalInclVat() - totalSettled));
        double trustAvail = trust.statementForCase(c.getId()).trustBalance();

        double ratioPct = effectiveClaim > 0
                ? Math.round((st.totalInclVat() / effectiveClaim) * 1000.0) / 10.0
                : 0.0;
        boolean highFeeWarning = effectiveClaim > 0 && ratioPct > 25.0;

        String status;
        if (st.totalInclVat() <= 0.009) status = "UNBILLED";
        else if (outstanding <= 0.01) status = "PAID";
        else if (totalSettled > 0.009) status = "PARTIALLY_PAID";
        else status = "UNPAID";

        return new FirmMatterBillingRow(
                c.getId(), c.getReference(), c.getTitle(), c.getClientName(),
                c.getStatus() == null ? "OPEN" : c.getStatus().name(),
                aiClaim, effectiveClaim, claimSource,
                st.totalHours(), st.subtotalExVat(), st.vatAmount(), st.totalInclVat(),
                settledTrust, directPaid, totalSettled, outstanding, trustAvail,
                ratioPct, highFeeWarning, status
        );
    }

    /** Aggregates firm-wide billing, Section 86 trust balances, settlements & AI claim-to-fee ratios. */
    public FirmBillingOverview firmOverview(CaseService caseService, TrustAccountingService trust,
                                            DocumentService documents, ExtractionService extractions) {
        List<Case> allCases = caseService.search(null);
        List<FirmMatterBillingRow> rows = new ArrayList<>();
        double hrs = 0, ex = 0, vat = 0, incl = 0, settled = 0, out = 0, trustHeld = 0;
        int paid = 0, unpaid = 0, alerts = 0;

        for (Case c : allCases) {
            FirmMatterBillingRow r = matterRow(c, caseService, trust, documents, extractions);
            rows.add(r);
            hrs += r.totalHours();
            ex += r.subtotalExVat();
            vat += r.vatAmount();
            incl += r.totalInclVat();
            settled += r.totalSettled();
            out += r.outstandingBalance();
            trustHeld += r.availableTrustBalance();
            if ("PAID".equals(r.paymentStatus())) paid++;
            else if ("UNPAID".equals(r.paymentStatus()) || "PARTIALLY_PAID".equals(r.paymentStatus())) unpaid++;
            if (r.highFeeRatioWarning()) alerts++;
        }

        return new FirmBillingOverview(
                rows, round2(hrs), round2(ex), round2(vat), round2(incl),
                round2(settled), round2(out), round2(trustHeld), paid, unpaid, alerts
        );
    }

    private static double extractAiClaimAmount(String caseId, DocumentService documents, ExtractionService extractions) {
        for (var d : documents.byCase(caseId)) {
            if (d.getProcessingStatus() != za.co.mongala.legal.model.LegalDocument.ProcessingStatus.PROCESSED) continue;
            for (var e : extractions.byDocument(d.getId())) {
                if ("Claim Amount".equalsIgnoreCase(e.getFieldName())) {
                    double parsed = parseZar(e.getFieldValue());
                    if (parsed > 0) return parsed;
                }
            }
        }
        return 0.0;
    }

    private static double parseZar(String raw) {
        if (raw == null) return 0.0;
        String digits = raw.replaceAll("[^0-9.]", "");
        if (digits.isBlank()) return 0.0;
        try { return Double.parseDouble(digits); } catch (Exception e) { return 0.0; }
    }

    /** Generates a clean A4 PDF Fee Note & Tax Invoice using Apache PDFBox. */
    public byte[] generateInvoicePdf(Case legalCase, CaseStatement st) {
        return generateInvoicePdf(legalCase, st, 0.0);
    }

    /** Generates a clean A4 PDF Fee Note & Tax Invoice including any settled/trust credits. */
    public byte[] generateInvoicePdf(Case legalCase, CaseStatement st, double settledAmount) {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = 795f;
                float left = 48f;
                float right = 547f;

                // Top banner bar
                cs.setNonStrokingColor(22, 35, 60);
                cs.addRect(0, 765, PDRectangle.A4.getWidth(), 80);
                cs.fill();

                cs.setNonStrokingColor(255, 255, 255);
                writeText(cs, PDType1Font.HELVETICA_BOLD, 16, left, 805, "MONGALA ATTORNEYS INC.");
                writeText(cs, PDType1Font.HELVETICA, 9.5f, left, 787,
                        "Legal Practitioners  |  Randburg, Gauteng, South Africa  |  VAT Reg: 4190284712");
                writeText(cs, PDType1Font.HELVETICA_BOLD, 13, 405, 805, "FEE NOTE / INVOICE");
                writeText(cs, PDType1Font.HELVETICA, 9.5f, 405, 787,
                        "Date: " + LocalDate.now() + "   Ref: INV-" + legalCase.getReference());

                cs.setNonStrokingColor(20, 25, 35);
                y = 735f;
                writeText(cs, PDType1Font.HELVETICA_BOLD, 10.5f, left, y, "BILL TO: " + safe(legalCase.getClientName()));
                y -= 15f;
                writeText(cs, PDType1Font.HELVETICA, 9.5f, left, y,
                        "Matter: " + safe(legalCase.getReference()) + " - " + truncate(safe(legalCase.getTitle()), 72));
                y -= 14f;
                writeText(cs, PDType1Font.HELVETICA, 9.5f, left, y,
                        "Court: " + safe(legalCase.getCourt()) + "   |   Practice Area: " + safe(legalCase.getMatterType()));

                y -= 24f;
                // Table header
                cs.setNonStrokingColor(232, 240, 251);
                cs.addRect(left, y - 5, right - left, 18);
                cs.fill();
                cs.setNonStrokingColor(22, 35, 60);
                writeText(cs, PDType1Font.HELVETICA_BOLD, 9f, left + 4, y, "DATE");
                writeText(cs, PDType1Font.HELVETICA_BOLD, 9f, left + 68, y, "TYPE");
                writeText(cs, PDType1Font.HELVETICA_BOLD, 9f, left + 138, y, "DESCRIPTION");
                writeText(cs, PDType1Font.HELVETICA_BOLD, 9f, left + 345, y, "HRS / RATE");
                writeText(cs, PDType1Font.HELVETICA_BOLD, 9f, left + 430, y, "AMOUNT (ZAR)");

                cs.setNonStrokingColor(20, 25, 35);
                y -= 20f;
                if (st.entries().isEmpty()) {
                    writeText(cs, PDType1Font.HELVETICA_OBLIQUE, 9.5f, left + 4, y,
                            "No billable attendances or disbursements recorded on this matter yet.");
                    y -= 18f;
                } else {
                    for (BillingEntry b : st.entries()) {
                        if (y < 160f) break; // keep clean single-page layout for summary below
                        writeText(cs, PDType1Font.HELVETICA, 9f, left + 4, y, String.valueOf(b.entryDate()));
                        writeText(cs, PDType1Font.HELVETICA, 8.5f, left + 68, y, b.entryType());
                        writeText(cs, PDType1Font.HELVETICA, 9f, left + 138, y, truncate(safe(b.description()), 38));
                        String unit = "TIME".equals(b.entryType())
                                ? String.format(Locale.US, "%.1fh @ R%.0f", b.hours(), b.hourlyRate())
                                : "Disbursement";
                        writeText(cs, PDType1Font.HELVETICA, 8.5f, left + 345, y, unit);
                        writeText(cs, PDType1Font.HELVETICA_BOLD, 9f, left + 430, y, money(b.amount()));
                        y -= 16f;
                    }
                }

                // Divider line
                y -= 8f;
                cs.setStrokingColor(190, 200, 215);
                cs.moveTo(left, y);
                cs.lineTo(right, y);
                cs.stroke();

                // Totals block
                y -= 20f;
                writeText(cs, PDType1Font.HELVETICA, 9.5f, 350, y, "Professional fees (" + st.totalHours() + " hrs):");
                writeText(cs, PDType1Font.HELVETICA, 9.5f, 478, y, money(st.feesSubtotal()));
                y -= 15f;
                writeText(cs, PDType1Font.HELVETICA, 9.5f, 350, y, "Disbursements:");
                writeText(cs, PDType1Font.HELVETICA, 9.5f, 478, y, money(st.disbursementsSubtotal()));
                y -= 15f;
                writeText(cs, PDType1Font.HELVETICA, 9.5f, 350, y, "Subtotal (excl. VAT):");
                writeText(cs, PDType1Font.HELVETICA, 9.5f, 478, y, money(st.subtotalExVat()));
                y -= 15f;
                writeText(cs, PDType1Font.HELVETICA, 9.5f, 350, y, "VAT @ 15%:");
                writeText(cs, PDType1Font.HELVETICA, 9.5f, 478, y, money(st.vatAmount()));
                y -= 20f;

                cs.setNonStrokingColor(22, 35, 60);
                cs.addRect(345, y - 6, right - 345, 22);
                cs.fill();
                cs.setNonStrokingColor(255, 255, 255);
                writeText(cs, PDType1Font.HELVETICA_BOLD, 10.5f, 352, y, "TOTAL DUE (ZAR):");
                writeText(cs, PDType1Font.HELVETICA_BOLD, 10.5f, 468, y, money(st.totalInclVat()));

                if (settledAmount > 0.009) {
                    y -= 24f;
                    cs.setNonStrokingColor(21, 100, 48);
                    writeText(cs, PDType1Font.HELVETICA, 9.5f, 350, y, "Less: Settled / Trust credit:");
                    writeText(cs, PDType1Font.HELVETICA_BOLD, 9.5f, 468, y, "-" + money(settledAmount));
                    y -= 16f;
                    double bal = Math.max(0.0, round2(st.totalInclVat() - settledAmount));
                    cs.setNonStrokingColor(20, 25, 35);
                    writeText(cs, PDType1Font.HELVETICA_BOLD, 10f, 350, y,
                            bal <= 0.01 ? "STATUS: PAID IN FULL" : "BALANCE OUTSTANDING:");
                    writeText(cs, PDType1Font.HELVETICA_BOLD, 10f, 468, y, money(bal));
                }

                // Footer trust notice
                cs.setNonStrokingColor(90, 100, 115);
                writeText(cs, PDType1Font.HELVETICA, 8.5f, left, 82,
                        "Trust Account (Section 86(2) Legal Practice Act 28 of 2014): Mongala Attorneys Trust, First National Bank, Acc 62891044512, Branch 250655.");
                writeText(cs, PDType1Font.HELVETICA, 8.5f, left, 68,
                        "Payment reference: " + safe(legalCase.getReference()) + "  |  Generated by LegalCase ERP.");
            }

            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not generate PDF invoice", e);
        }
    }

    private static void writeText(PDPageContentStream cs, PDType1Font font, float size,
                                  float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        // Strip characters outside WinAnsiEncoding so PDFBox Type1 fonts never throw
        String asciiSafe = text == null ? "" : text.replaceAll("[^\\x20-\\x7E]", " ");
        cs.showText(asciiSafe);
        cs.endText();
    }

    private static String money(double v) {
        return String.format(Locale.US, "R %,.2f", v);
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private BillingEntry map(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("created_at");
        return new BillingEntry(
                rs.getString("id"),
                rs.getString("case_id"),
                rs.getDate("entry_date").toLocalDate(),
                rs.getString("entry_type"),
                rs.getString("description"),
                rs.getDouble("hours"),
                rs.getDouble("hourly_rate"),
                rs.getDouble("amount"),
                rs.getString("recorded_by"),
                ts == null ? LocalDateTime.now() : ts.toLocalDateTime()
        );
    }
}
