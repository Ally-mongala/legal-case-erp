package za.co.mongala.legal.backend.service;

import za.co.mongala.legal.model.Summary;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Persistence + human-in-the-loop workflow of AI summaries (FR3/FR6/ETR3/ETR4). */
public class SummaryService {

    private final DataSource ds;

    public SummaryService(DataSource ds) {
        this.ds = ds;
    }

    public void insert(Summary s) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     INSERT INTO summaries(id, document_id, case_id, paragraph1, paragraph2, paragraph3,
                                           version, status, created_by, created_at, approved_at)
                     VALUES (?,?,?,?,?,?,?,?,?,?,?)""")) {
            ps.setString(1, s.getId());
            ps.setString(2, s.getDocumentId());
            ps.setString(3, s.getCaseId());
            ps.setString(4, s.getParagraph1());
            ps.setString(5, s.getParagraph2());
            ps.setString(6, s.getParagraph3());
            ps.setInt(7, s.getVersion());
            ps.setString(8, s.getStatus().name());
            ps.setString(9, s.getCreatedBy());
            ps.setTimestamp(10, Timestamp.valueOf(s.getCreatedAt()));
            ps.setTimestamp(11, s.getApprovedAt() == null ? null : Timestamp.valueOf(s.getApprovedAt()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not insert summary", e);
        }
    }

    public Summary byDocument(String documentId) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM summaries WHERE document_id = ? ORDER BY created_at DESC LIMIT 1")) {
            ps.setString(1, documentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not query summary", e);
        }
    }

    public Summary findById(String id) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM summaries WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not query summary", e);
        }
    }

    public List<Summary> byCase(String caseId) {
        List<Summary> out = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM summaries WHERE case_id = ? ORDER BY created_at DESC")) {
            ps.setString(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not query summaries", e);
        }
        return out;
    }

    /** Save lawyer edits; every save bumps the version (ETR4). */
    public void updateTexts(String id, String p1, String p2, String p3) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE summaries SET paragraph1 = ?, paragraph2 = ?, paragraph3 = ?, version = version + 1 WHERE id = ?")) {
            ps.setString(1, p1);
            ps.setString(2, p2);
            ps.setString(3, p3);
            ps.setString(4, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not update summary", e);
        }
    }

    /** Approve -> publish to the client portal (FR6). */
    public void approve(String id, LocalDateTime at) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE summaries SET status = ?, approved_at = ? WHERE id = ?")) {
            ps.setString(1, Summary.SummaryStatus.APPROVED.name());
            ps.setTimestamp(2, Timestamp.valueOf(at));
            ps.setString(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not approve summary", e);
        }
    }

    public void deleteForDocument(String documentId) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM summaries WHERE document_id = ?")) {
            ps.setString(1, documentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not delete summaries", e);
        }
    }

    private Summary map(ResultSet rs) throws SQLException {
        Timestamp at = rs.getTimestamp("approved_at");
        return new Summary(
                rs.getString("id"),
                rs.getString("document_id"),
                rs.getString("case_id"),
                rs.getString("paragraph1"),
                rs.getString("paragraph2"),
                rs.getString("paragraph3"),
                rs.getInt("version"),
                Summary.SummaryStatus.valueOf(rs.getString("status")),
                rs.getString("created_by"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                at == null ? null : at.toLocalDateTime());
    }
}
