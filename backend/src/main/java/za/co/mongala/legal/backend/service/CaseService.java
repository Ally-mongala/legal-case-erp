package za.co.mongala.legal.backend.service;

import za.co.mongala.legal.model.Case;
import za.co.mongala.legal.model.CaseStatus;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** CRUD + search over the `cases` table (the central legal database). */
public class CaseService {

    private final DataSource ds;

    public CaseService(DataSource ds) {
        this.ds = ds;
    }

    public void insert(Case c) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     INSERT INTO cases(id, reference, title, client_id, client_name, opposing_party,
                                       status, matter_type, court, description, opened_date, amount_involved)
                     VALUES (?,?,?,?,?,?,?,?,?,?,?,?)""")) {
            ps.setString(1, c.getId());
            ps.setString(2, c.getReference());
            ps.setString(3, c.getTitle());
            ps.setString(4, c.getClientId());
            ps.setString(5, c.getClientName());
            ps.setString(6, c.getOpposingParty());
            ps.setString(7, c.getStatus().name());
            ps.setString(8, c.getMatterType());
            ps.setString(9, c.getCourt());
            ps.setString(10, c.getDescription());
            ps.setDate(11, Date.valueOf(c.getOpenedDate()));
            ps.setDouble(12, c.getAmountInvolved());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not insert case", e);
        }
    }

    public List<Case> search(String q) {
        List<Case> out = new ArrayList<>();
        String sql = "SELECT * FROM cases";
        boolean filtered = q != null && !q.isBlank();
        if (filtered) {
            sql += " WHERE lower(title) LIKE ? OR lower(reference) LIKE ? OR lower(client_name) LIKE ?"
                    + " OR lower(opposing_party) LIKE ? OR lower(status) LIKE ?";
        }
        sql += " ORDER BY opened_date DESC";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (filtered) {
                String like = "%" + q.toLowerCase() + "%";
                for (int i = 1; i <= 5; i++) ps.setString(i, like);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not search cases", e);
        }
        return out;
    }

    public List<Case> byClient(String clientId) {
        List<Case> out = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM cases WHERE client_id = ? ORDER BY opened_date DESC")) {
            ps.setString(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not query client cases", e);
        }
        return out;
    }

    public Case findById(String id) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM cases WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not query case", e);
        }
    }

    public Case findByReference(String reference) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM cases WHERE reference = ?")) {
            ps.setString(1, reference);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not query case by reference", e);
        }
    }

    /** Update case fields (title, opposing party, description, matter, court, amount) - used by PATCH /api/cases/{id}. */
    public void updateFields(String id, String title, String opposingParty, String matterType,
                             String court, String description, double amountInvolved) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE cases SET title = ?, opposing_party = ?, matter_type = ?, court = ?, description = ?, amount_involved = ? WHERE id = ?")) {
            ps.setString(1, title);
            ps.setString(2, opposingParty);
            ps.setString(3, matterType);
            ps.setString(4, court);
            ps.setString(5, description);
            ps.setDouble(6, amountInvolved);
            ps.setString(7, id);
            int rows = ps.executeUpdate();
            if (rows == 0) throw new IllegalStateException("Case not found");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not update case", e);
        }
    }

    /** Update only the status of a case. */
    public void setStatus(String id, za.co.mongala.legal.model.CaseStatus status) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE cases SET status = ? WHERE id = ?")) {
            ps.setString(1, status.name());
            ps.setString(2, id);
            int rows = ps.executeUpdate();
            if (rows == 0) throw new IllegalStateException("Case not found");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not update case status", e);
        }
    }

    /** Links an existing matter to a newly provisioned or selected Client user account (FR8). */
    public void assignClient(String caseId, String clientId, String clientName) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE cases SET client_id = ?, client_name = ? WHERE id = ?")) {
            ps.setString(1, clientId);
            ps.setString(2, clientName);
            ps.setString(3, caseId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not link client to case", e);
        }
    }

    /** Reads direct (non-trust EFT/card) fee-note payments recorded on a case. */
    public double getDirectPayments(String caseId) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT direct_payments FROM cases WHERE id = ?")) {
            ps.setString(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Math.max(0.0, rs.getDouble(1)) : 0.0;
            }
        } catch (SQLException e) {
            return 0.0;
        }
    }

    /** Increments direct fee-note payments on a case and returns the updated cumulative paid amount. */
    public double recordDirectPayment(String caseId, double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Payment amount must be greater than zero");
        double updated = Math.round((getDirectPayments(caseId) + amount) * 100.0) / 100.0;
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE cases SET direct_payments = ? WHERE id = ?")) {
            ps.setDouble(1, updated);
            ps.setString(2, caseId);
            ps.executeUpdate();
            return updated;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not record payment", e);
        }
    }

    /** Delete a case and its dependent rows (documents, extractions, summaries, deadlines, messages). */
    public void delete(String id) {
        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM documents WHERE case_id = ?")) { ps.setString(1, id); ps.executeUpdate(); }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM summaries WHERE case_id = ?")) { ps.setString(1, id); ps.executeUpdate(); }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM deadlines WHERE case_id = ?")) { ps.setString(1, id); ps.executeUpdate(); }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM messages WHERE case_id = ?")) { ps.setString(1, id); ps.executeUpdate(); }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM ratings WHERE case_id = ?")) { ps.setString(1, id); ps.executeUpdate(); }
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM cases WHERE id = ?")) { ps.setString(1, id); ps.executeUpdate(); }
            conn.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not delete case", e);
        }
    }

    public String nextReference(int year) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT MAX(reference) AS m FROM cases WHERE reference LIKE ?")) {
            ps.setString(1, "C-" + year + "-%");
            try (ResultSet rs = ps.executeQuery()) {
                int n = 0;
                if (rs.next() && rs.getString("m") != null) {
                    String m = rs.getString("m");
                    n = Integer.parseInt(m.substring(m.lastIndexOf('-') + 1));
                }
                return String.format("C-%d-%03d", year, n + 1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not generate reference", e);
        }
    }

    private Case map(ResultSet rs) throws SQLException {
        Date d = rs.getDate("opened_date");
        return new Case(
                rs.getString("id"),
                rs.getString("reference"),
                rs.getString("title"),
                rs.getString("client_id"),
                rs.getString("client_name"),
                rs.getString("opposing_party"),
                CaseStatus.valueOf(rs.getString("status")),
                rs.getString("matter_type"),
                rs.getString("court"),
                rs.getString("description"),
                d == null ? null : d.toLocalDate(),
                rs.getDouble("amount_involved"));
    }
}
