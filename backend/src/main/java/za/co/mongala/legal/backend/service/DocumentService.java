package za.co.mongala.legal.backend.service;

import za.co.mongala.legal.model.LegalDocument;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Persistence for uploaded legal PDFs (`documents` table, FR1). */
public class DocumentService {

    private final DataSource ds;

    public DocumentService(DataSource ds) {
        this.ds = ds;
    }

    public void insert(LegalDocument doc) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     INSERT INTO documents(id, case_id, filename, storage_path, uploaded_by,
                                           uploaded_at, size_kb, status, doc_type, consent_granted, rejection_reason)
                     VALUES (?,?,?,?,?,?,?,?,?,?,?)""")) {
            ps.setString(1, doc.getId());
            ps.setString(2, doc.getCaseId());
            ps.setString(3, doc.getFileName());
            ps.setString(4, doc.getStoragePath());
            ps.setString(5, doc.getUploadedBy());
            ps.setTimestamp(6, Timestamp.valueOf(doc.getUploadedAt()));
            ps.setLong(7, doc.getSizeKb());
            ps.setString(8, doc.getProcessingStatus().name());
            ps.setString(9, doc.getDocType());
            ps.setBoolean(10, doc.isConsentGranted());
            ps.setString(11, doc.getRejectionReason());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not insert document", e);
        }
    }

    public LegalDocument findById(String id) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM documents WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not query document", e);
        }
    }

    public List<LegalDocument> byCase(String caseId) {
        List<LegalDocument> out = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM documents WHERE case_id = ? ORDER BY uploaded_at DESC")) {
            ps.setString(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not query documents", e);
        }
        return out;
    }

    /** All documents BLOCKED by AI validation, joined with their case reference (rejection report). */
    public List<za.co.mongala.legal.model.RejectedDocument> rejectedDocuments() {
        List<za.co.mongala.legal.model.RejectedDocument> out = new ArrayList<>();
        String sql = "SELECT d.id, d.filename, d.doc_type, d.uploaded_by, d.uploaded_at, d.rejection_reason, "
                + "c.reference AS case_reference FROM documents d LEFT JOIN cases c ON c.id = d.case_id "
                + "WHERE d.status = 'REJECTED' ORDER BY d.uploaded_at DESC";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String docType = rs.getString("doc_type");
                out.add(new za.co.mongala.legal.model.RejectedDocument(
                        rs.getString("id"),
                        rs.getString("filename"),
                        docType == null ? "PDF" : docType,
                        rs.getString("case_reference"),
                        rs.getString("uploaded_by"),
                        rs.getTimestamp("uploaded_at").toLocalDateTime(),
                        rs.getString("rejection_reason")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not query rejected documents", e);
        }
        return out;
    }

    public void setFullText(String id, String fullText) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE documents SET full_text = ? WHERE id = ?")) {
            ps.setString(1, fullText);
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not store document text", e);
        }
    }

    public String getFullText(String id) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT full_text FROM documents WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("full_text") : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read document text", e);
        }
    }

    /** Deletes the document row and all dependent data (extractions, summaries, deadlines created from it). */
    public void delete(String id) {
        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM extractions WHERE document_id = ?")) { ps.setString(1, id); ps.executeUpdate(); }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM summaries WHERE document_id = ?")) { ps.setString(1, id); ps.executeUpdate(); }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM documents WHERE id = ?")) { ps.setString(1, id); ps.executeUpdate(); }
            conn.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not delete document", e);
        }
    }

    public void setStatus(String id, LegalDocument.ProcessingStatus status) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE documents SET status = ?, rejection_reason = NULL WHERE id = ?")) {
            ps.setString(1, status.name());
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not update document status", e);
        }
    }

    /** Blocks a document: sets REJECTED status with a visible reason (AI validation). */
    public void setRejected(String id, String reason) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE documents SET status = 'REJECTED', rejection_reason = ? WHERE id = ?")) {
            ps.setString(1, reason);
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not reject document", e);
        }
    }

    private LegalDocument map(ResultSet rs) throws SQLException {
        String docType = "PDF";
        boolean consent = true;
        String reason = null;
        try { docType = rs.getString("doc_type"); } catch (SQLException ignored) {}
        try { consent = rs.getBoolean("consent_granted"); } catch (SQLException ignored) {}
        try { reason = rs.getString("rejection_reason"); } catch (SQLException ignored) {}
        return new LegalDocument(
                rs.getString("id"),
                rs.getString("case_id"),
                rs.getString("filename"),
                rs.getString("storage_path"),
                rs.getString("uploaded_by"),
                rs.getTimestamp("uploaded_at").toLocalDateTime(),
                rs.getLong("size_kb"),
                LegalDocument.ProcessingStatus.valueOf(rs.getString("status")),
                docType, consent, reason);
    }
}
