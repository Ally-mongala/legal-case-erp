package za.co.mongala.legal.backend.service;

import za.co.mongala.legal.model.Dataset;
import za.co.mongala.legal.model.TrainingRun;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Model retraining runs + training datasets management (FR10 / ETR3). */
public class TrainingService {

    private final DataSource ds;

    public TrainingService(DataSource ds) {
        this.ds = ds;
    }

    /* ---------------- training runs ---------------- */

    public List<TrainingRun> runs() {
        List<TrainingRun> out = new ArrayList<>();
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM training_runs ORDER BY requested_at DESC")) {
            while (rs.next()) {
                Timestamp at = rs.getTimestamp("approved_at");
                out.add(new TrainingRun(rs.getString("id"), rs.getString("model_name"),
                        rs.getString("dataset_id"), TrainingRun.Status.valueOf(rs.getString("status")),
                        rs.getString("requested_by"), rs.getTimestamp("requested_at").toLocalDateTime(),
                        at == null ? null : at.toLocalDateTime(),
                        rs.getObject("accuracy") == null ? null : rs.getDouble("accuracy")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not query training runs", e);
        }
        return out;
    }

    public void approve(String id, String approver, java.time.LocalDateTime at) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE training_runs SET status = 'APPROVED', approved_at = ? WHERE id = ? AND status = 'PENDING'")) {
            ps.setTimestamp(1, Timestamp.valueOf(at));
            ps.setString(2, id);
            int rows = ps.executeUpdate();
            if (rows == 0) throw new IllegalStateException("Run not found or already processed");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not approve training run", e);
        }
    }

    /* ---------------- datasets ---------------- */

    public List<Dataset> datasets() {
        List<Dataset> out = new ArrayList<>();
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM datasets ORDER BY uploaded_at DESC")) {
            while (rs.next()) {
                out.add(new Dataset(rs.getString("id"), rs.getString("name"),
                        rs.getLong("records"), rs.getLong("size_kb"),
                        rs.getString("storage_path"),
                        rs.getTimestamp("uploaded_at").toLocalDateTime()));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not query datasets", e);
        }
        return out;
    }

    public Dataset findById(String id) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM datasets WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new Dataset(rs.getString("id"), rs.getString("name"),
                        rs.getLong("records"), rs.getLong("size_kb"),
                        rs.getString("storage_path"),
                        rs.getTimestamp("uploaded_at").toLocalDateTime());
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not query dataset", e);
        }
    }

    /** Persist a new uploaded dataset file (FR10 - manage training datasets). */
    public void insert(String id, String name, long records, long sizeKb, String storagePath) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO datasets(id, name, records, size_kb, storage_path, uploaded_at) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, name);
            ps.setLong(3, records);
            ps.setLong(4, sizeKb);
            ps.setString(5, storagePath);
            ps.setTimestamp(6, Timestamp.valueOf(java.time.LocalDateTime.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not insert dataset", e);
        }
    }

    /* ---------------- ETR-04: practitioner AI correction harvesting ---------------- */

    public static final String CORRECTIONS_DATASET_ID = "ds-corrections";
    private static final String CORRECTIONS_PATH = "datasets/ds-corrections.jsonl";

    public record AiCorrection(
            String id,
            String summaryId,
            String documentId,
            String caseId,
            String editedBy,
            java.time.LocalDateTime editedAt,
            String origP1,
            String origP2,
            String origP3,
            String newP1,
            String newP2,
            String newP3,
            String datasetId
    ) {}

    /**
     * Records a practitioner edit to an AI-generated summary, appends it as a JSONL training
     * sample on disk, updates the live corrections dataset row, and queues a PENDING
     * retraining run if none is currently awaiting administrator approval.
     */
    public AiCorrection recordSummaryCorrection(za.co.mongala.legal.model.Summary before,
                                                String newP1, String newP2, String newP3,
                                                String editedBy,
                                                java.nio.file.Path uploadDir,
                                                com.fasterxml.jackson.databind.ObjectMapper mapper) {
        String id = "ac" + java.util.UUID.randomUUID().toString().substring(0, 10);
        java.time.LocalDateTime now = java.time.LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO ai_corrections(id, summary_id, document_id, case_id, edited_by, edited_at,
                                                orig_p1, orig_p2, orig_p3, new_p1, new_p2, new_p3, dataset_id)
                     VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)""")) {
            ps.setString(1, id);
            ps.setString(2, before.getId());
            ps.setString(3, before.getDocumentId());
            ps.setString(4, before.getCaseId());
            ps.setString(5, editedBy);
            ps.setTimestamp(6, Timestamp.valueOf(now));
            ps.setString(7, before.getParagraph1());
            ps.setString(8, before.getParagraph2());
            ps.setString(9, before.getParagraph3());
            ps.setString(10, newP1);
            ps.setString(11, newP2);
            ps.setString(12, newP3);
            ps.setString(13, CORRECTIONS_DATASET_ID);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not store AI correction", e);
        }

        // Append JSONL line to datasets/ds-corrections.jsonl and update dataset counters
        try {
            java.nio.file.Files.createDirectories(uploadDir.resolve("datasets"));
            java.nio.file.Path file = uploadDir.resolve(CORRECTIONS_PATH);
            java.util.Map<String, Object> sample = new java.util.LinkedHashMap<>();
            sample.put("id", id);
            sample.put("summaryId", before.getId());
            sample.put("documentId", before.getDocumentId());
            sample.put("caseId", before.getCaseId());
            sample.put("editedBy", editedBy);
            sample.put("editedAt", now.toString());
            sample.put("original", java.util.Map.of(
                    "facts", String.valueOf(before.getParagraph1()),
                    "legalIssues", String.valueOf(before.getParagraph2()),
                    "currentStatus", String.valueOf(before.getParagraph3())));
            sample.put("corrected", java.util.Map.of(
                    "facts", String.valueOf(newP1),
                    "legalIssues", String.valueOf(newP2),
                    "currentStatus", String.valueOf(newP3)));
            String line = mapper.writeValueAsString(sample) + "\n";
            java.nio.file.Files.writeString(file, line, java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);

            long records = java.nio.file.Files.readAllLines(file, java.nio.charset.StandardCharsets.UTF_8).size();
            long kb = Math.max(1L, java.nio.file.Files.size(file) / 1024L);

            try (Connection c = ds.getConnection()) {
                if (findById(CORRECTIONS_DATASET_ID) == null) {
                    insert(CORRECTIONS_DATASET_ID, "Practitioner summary corrections (live JSONL)", records, kb, CORRECTIONS_PATH);
                } else {
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE datasets SET records = ?, size_kb = ?, uploaded_at = ? WHERE id = ?")) {
                        ps.setLong(1, records);
                        ps.setLong(2, kb);
                        ps.setTimestamp(3, Timestamp.valueOf(now));
                        ps.setString(4, CORRECTIONS_DATASET_ID);
                        ps.executeUpdate();
                    }
                }
                // Ensure a PENDING retraining run references the live corrections dataset
                boolean hasPending;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT COUNT(*) FROM training_runs WHERE dataset_id = ? AND status = 'PENDING'")) {
                    ps.setString(1, CORRECTIONS_DATASET_ID);
                    try (ResultSet rs = ps.executeQuery()) {
                        hasPending = rs.next() && rs.getLong(1) > 0;
                    }
                }
                if (!hasPending) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO training_runs(id, model_name, dataset_id, status, requested_by, requested_at, accuracy) VALUES (?,?,?,?,?,?,?)")) {
                        ps.setString(1, "tr" + java.util.UUID.randomUUID().toString().substring(0, 8));
                        ps.setString(2, "legal-summarizer-feedback-v2");
                        ps.setString(3, CORRECTIONS_DATASET_ID);
                        ps.setString(4, "PENDING");
                        ps.setString(5, editedBy);
                        ps.setTimestamp(6, Timestamp.valueOf(now));
                        ps.setNull(7, Types.DOUBLE);
                        ps.executeUpdate();
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not update corrections dataset", e);
        }

        return new AiCorrection(id, before.getId(), before.getDocumentId(), before.getCaseId(),
                editedBy, now, before.getParagraph1(), before.getParagraph2(), before.getParagraph3(),
                newP1, newP2, newP3, CORRECTIONS_DATASET_ID);
    }

    public List<AiCorrection> listCorrections() {
        List<AiCorrection> out = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM ai_corrections ORDER BY edited_at DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new AiCorrection(
                        rs.getString("id"),
                        rs.getString("summary_id"),
                        rs.getString("document_id"),
                        rs.getString("case_id"),
                        rs.getString("edited_by"),
                        rs.getTimestamp("edited_at").toLocalDateTime(),
                        rs.getString("orig_p1"),
                        rs.getString("orig_p2"),
                        rs.getString("orig_p3"),
                        rs.getString("new_p1"),
                        rs.getString("new_p2"),
                        rs.getString("new_p3"),
                        rs.getString("dataset_id")
                ));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not list AI corrections", e);
        }
        return out;
    }
}
