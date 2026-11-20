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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Section 86 Legal Practice Act 28 of 2014 client trust-account ledger.
 *
 * Enforces the core Legal Practice Council (LPC) accounting rule:
 * client trust funds are held separately from firm business monies, and a
 * transfer or refund may NEVER exceed the available trust credit on that matter.
 */
public class TrustAccountingService {

    public record TrustEntry(
            String id,
            String caseId,
            LocalDate txDate,
            String txType,        // "DEPOSIT" | "TRANSFER_TO_BUSINESS" | "REFUND"
            String description,
            String reference,
            double amount,
            double balanceAfter,
            String recordedBy,
            LocalDateTime createdAt
    ) {}

    public record TrustStatement(
            String caseId,
            List<TrustEntry> entries,
            double totalDeposits,
            double totalTransfersOut,
            double trustBalance
    ) {}

    private final DataSource ds;

    public TrustAccountingService(DataSource ds) {
        this.ds = ds;
    }

    public synchronized TrustEntry recordTransaction(String caseId, LocalDate txDate, String txType,
                                                     String description, String reference,
                                                     double amount, String recordedBy) {
        double cleanAmt = round2(amount);
        if (cleanAmt <= 0) {
            throw new IllegalArgumentException("Transaction amount must be greater than zero.");
        }
        String type = normaliseType(txType);
        TrustStatement current = statementForCase(caseId);
        double newBalance;

        if ("DEPOSIT".equals(type)) {
            newBalance = round2(current.trustBalance() + cleanAmt);
        } else {
            if (cleanAmt > current.trustBalance() + 0.001) {
                throw new IllegalArgumentException(String.format(Locale.US,
                        "Section 86 trust-account rule: %s of R %,.2f exceeds available client trust balance of R %,.2f. Trust accounts may never go into debit.",
                        type.replace("_", " "), cleanAmt, current.trustBalance()));
            }
            newBalance = round2(current.trustBalance() - cleanAmt);
        }

        String id = "tr" + UUID.randomUUID().toString().substring(0, 10);
        LocalDate date = txDate == null ? LocalDate.now() : txDate;
        LocalDateTime now = LocalDateTime.now();
        String ref = (reference == null || reference.isBlank())
                ? ("TR-" + date.toString().replace("-", "") + "-" + id.substring(2, 6).toUpperCase(Locale.ROOT))
                : reference.trim();

        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO trust_ledger(id, case_id, tx_date, tx_type, description, reference,
                                              amount, balance_after, recorded_by, created_at)
                     VALUES (?,?,?,?,?,?,?,?,?,?)""")) {
            ps.setString(1, id);
            ps.setString(2, caseId);
            ps.setDate(3, Date.valueOf(date));
            ps.setString(4, type);
            ps.setString(5, description == null || description.isBlank() ? "Trust account transaction" : description.trim());
            ps.setString(6, ref);
            ps.setDouble(7, cleanAmt);
            ps.setDouble(8, newBalance);
            ps.setString(9, recordedBy == null ? "Attorney" : recordedBy);
            ps.setTimestamp(10, Timestamp.valueOf(now));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not record trust transaction", e);
        }

        return new TrustEntry(id, caseId, date, type,
                description == null || description.isBlank() ? "Trust account transaction" : description.trim(),
                ref, cleanAmt, newBalance, recordedBy == null ? "Attorney" : recordedBy, now);
    }

    public TrustStatement statementForCase(String caseId) {
        List<TrustEntry> list = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM trust_ledger WHERE case_id = ? ORDER BY tx_date ASC, created_at ASC")) {
            ps.setString(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("created_at");
                    list.add(new TrustEntry(
                            rs.getString("id"),
                            rs.getString("case_id"),
                            rs.getDate("tx_date").toLocalDate(),
                            rs.getString("tx_type"),
                            rs.getString("description"),
                            rs.getString("reference"),
                            rs.getDouble("amount"),
                            rs.getDouble("balance_after"),
                            rs.getString("recorded_by"),
                            ts == null ? LocalDateTime.now() : ts.toLocalDateTime()
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not query trust ledger", e);
        }

        double deposits = 0.0, outs = 0.0;
        for (TrustEntry e : list) {
            if ("DEPOSIT".equals(e.txType())) deposits += e.amount();
            else outs += e.amount();
        }
        double bal = Math.max(0.0, round2(deposits - outs));
        return new TrustStatement(caseId, list, round2(deposits), round2(outs), bal);
    }

    /** Total transferred from client trust specifically to settle firm fee notes / disbursements. */
    public double transfersToBusinessForCase(String caseId) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(SUM(amount), 0) FROM trust_ledger WHERE case_id = ? AND tx_type = 'TRANSFER_TO_BUSINESS'")) {
            ps.setString(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? round2(rs.getDouble(1)) : 0.0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not query trust fee transfers", e);
        }
    }

    /** Lists every Section 86 trust transaction across the firm for audit & Excel reporting. */
    public List<TrustEntry> allEntries() {
        List<TrustEntry> list = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM trust_ledger ORDER BY tx_date DESC, created_at DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Timestamp ts = rs.getTimestamp("created_at");
                list.add(new TrustEntry(
                        rs.getString("id"),
                        rs.getString("case_id"),
                        rs.getDate("tx_date").toLocalDate(),
                        rs.getString("tx_type"),
                        rs.getString("description"),
                        rs.getString("reference"),
                        rs.getDouble("amount"),
                        rs.getDouble("balance_after"),
                        rs.getString("recorded_by"),
                        ts == null ? LocalDateTime.now() : ts.toLocalDateTime()
                ));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not list trust entries", e);
        }
        return list;
    }

    public byte[] generateTrustStatementPdf(Case legalCase, TrustStatement st) {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setNonStrokingColor(22, 35, 60);
                cs.addRect(0, 765, PDRectangle.A4.getWidth(), 80);
                cs.fill();

                cs.setNonStrokingColor(255, 255, 255);
                write(cs, PDType1Font.HELVETICA_BOLD, 15f, 48, 805, "MONGALA ATTORNEYS TRUST ACCOUNT");
                write(cs, PDType1Font.HELVETICA, 9.5f, 48, 787,
                        "Section 86(2) Legal Practice Act 28 of 2014  |  LPC Audited Client Trust Ledger");
                write(cs, PDType1Font.HELVETICA_BOLD, 12f, 405, 805, "TRUST STATEMENT");
                write(cs, PDType1Font.HELVETICA, 9.5f, 405, 787, "Date: " + LocalDate.now());

                cs.setNonStrokingColor(20, 25, 35);
                write(cs, PDType1Font.HELVETICA_BOLD, 10.5f, 48, 735,
                        "CLIENT: " + safe(legalCase.getClientName()) + "   |   MATTER: " + safe(legalCase.getReference()));
                write(cs, PDType1Font.HELVETICA, 9.5f, 48, 719, "Title: " + safe(legalCase.getTitle()));

                float y = 688f;
                cs.setNonStrokingColor(232, 240, 251);
                cs.addRect(48, y - 5, 499, 18);
                cs.fill();
                cs.setNonStrokingColor(22, 35, 60);
                write(cs, PDType1Font.HELVETICA_BOLD, 8.5f, 52, y, "DATE");
                write(cs, PDType1Font.HELVETICA_BOLD, 8.5f, 112, y, "TYPE");
                write(cs, PDType1Font.HELVETICA_BOLD, 8.5f, 215, y, "DESCRIPTION");
                write(cs, PDType1Font.HELVETICA_BOLD, 8.5f, 395, y, "AMOUNT");
                write(cs, PDType1Font.HELVETICA_BOLD, 8.5f, 472, y, "BALANCE");

                cs.setNonStrokingColor(20, 25, 35);
                y -= 20f;
                for (TrustEntry e : st.entries()) {
                    if (y < 140f) break;
                    String sign = "DEPOSIT".equals(e.txType()) ? "+" : "-";
                    write(cs, PDType1Font.HELVETICA, 8.5f, 52, y, String.valueOf(e.txDate()));
                    write(cs, PDType1Font.HELVETICA, 8f, 112, y, e.txType().replace("_", " "));
                    write(cs, PDType1Font.HELVETICA, 8.5f, 215, y, truncate(safe(e.description()), 34));
                    write(cs, PDType1Font.HELVETICA, 8.5f, 395, y, sign + money(e.amount()));
                    write(cs, PDType1Font.HELVETICA_BOLD, 8.5f, 472, y, money(e.balanceAfter()));
                    y -= 16f;
                }

                y -= 14f;
                cs.setNonStrokingColor(230, 244, 234);
                cs.addRect(320, y - 6, 227, 24);
                cs.fill();
                cs.setNonStrokingColor(21, 100, 48);
                write(cs, PDType1Font.HELVETICA_BOLD, 10f, 328, y + 2,
                        "AVAILABLE TRUST CREDIT: " + money(st.trustBalance()));
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not generate trust statement PDF", e);
        }
    }

    private static String normaliseType(String raw) {
        if (raw == null) return "DEPOSIT";
        String u = raw.trim().toUpperCase(Locale.ROOT);
        if (u.contains("TRANSFER") || u.contains("BUSINESS") || u.contains("FEE")) return "TRANSFER_TO_BUSINESS";
        if (u.contains("REFUND") || u.contains("WITHDRAW")) return "REFUND";
        return "DEPOSIT";
    }

    private static void write(PDPageContentStream cs, PDType1Font font, float size, float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText((text == null ? "" : text).replaceAll("[^\\x20-\\x7E]", " "));
        cs.endText();
    }

    private static String money(double v) { return String.format(Locale.US, "R %,.2f", v); }
    private static String safe(String s) { return s == null ? "" : s; }
    private static String truncate(String s, int m) { return s.length() <= m ? s : s.substring(0, m - 3) + "..."; }
    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
