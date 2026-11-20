package za.co.mongala.legal.backend.service;

import za.co.mongala.legal.model.Extraction;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Persistence of AI-extracted fields (`extractions` table, FR2). */
public class ExtractionService {

    private final DataSource ds;

    public ExtractionService(DataSource ds) {
        this.ds = ds;
    }

    public void insertAll(String documentId, List<Extraction> extractions) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     INSERT INTO extractions(document_id, field_name, field_value, confidence, span_text, page_number)
                     VALUES (?,?,?,?,?,?)""")) {
            for (Extraction e : extractions) {
                ps.setString(1, documentId);
                ps.setString(2, e.getFieldName());
                ps.setString(3, e.getFieldValue());
                ps.setDouble(4, e.getConfidence());
                ps.setString(5, e.getSpanText());
                ps.setInt(6, e.getPageNumber());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not insert extractions", e);
        }
    }

    public List<Extraction> byDocument(String documentId) {
        List<Extraction> out = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM extractions WHERE document_id = ? ORDER BY id")) {
            ps.setString(1, documentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Extraction ex = new Extraction(
                            rs.getString("field_name"),
                            rs.getString("field_value"),
                            rs.getDouble("confidence"),
                            rs.getString("span_text"),
                            rs.getInt("page_number"));
                    ex.setDocumentId(documentId);
                    out.add(ex);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not query extractions", e);
        }
        return out;
    }

    public List<Extraction> all() {
        List<Extraction> out = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM extractions ORDER BY id DESC LIMIT 2000");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new Extraction(
                        rs.getString("field_name"),
                        rs.getString("field_value"),
                        rs.getDouble("confidence"),
                        rs.getString("span_text"),
                        rs.getInt("page_number")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not query extractions", e);
        }
        return out;
    }

    public void deleteForDocument(String documentId) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM extractions WHERE document_id = ?")) {
            ps.setString(1, documentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not delete extractions", e);
        }
    }
}
