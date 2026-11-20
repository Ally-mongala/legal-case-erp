package za.co.mongala.legal.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import za.co.mongala.legal.backend.security.EncryptionUtil;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Encrypted backup and restore-verification service (NFR-02).
 *
 * Exports every relational table into a structured JSON snapshot, GZIP-compresses it,
 * encrypts it with AES-256-GCM (EncryptionUtil), writes it to disk, and immediately
 * runs a full decrypt + gunzip + JSON-parse verification pass so the firm has
 * measured backup and recovery-verification timings.
 */
public class BackupService {

    private static final List<String> TABLES = List.of(
            "users", "cases", "documents", "extractions", "summaries",
            "deadlines", "messages", "ratings", "billing_entries",
            "trust_ledger", "ai_corrections", "training_runs", "datasets", "audit_log"
    );

    public record BackupRecord(
            String id,
            String filename,
            String createdBy,
            LocalDateTime createdAt,
            long sizeBytes,
            long totalRows,
            double backupMs,
            double restoreVerifyMs,
            boolean verified
    ) {}

    private final DataSource ds;
    private final EncryptionUtil encryption;
    private final Path backupDir;
    private final ObjectMapper mapper;

    public BackupService(DataSource ds, EncryptionUtil encryption, Path uploadDir, ObjectMapper mapper) {
        this.ds = ds;
        this.encryption = encryption;
        this.backupDir = uploadDir.getParent() != null
                ? uploadDir.getParent().resolve("backups")
                : uploadDir.resolve("backups");
        this.mapper = mapper;
        try {
            Files.createDirectories(this.backupDir);
        } catch (Exception e) {
            throw new IllegalStateException("Could not create backup directory", e);
        }
    }

    public BackupRecord createEncryptedBackup(String createdBy) {
        long t0 = System.nanoTime();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        String id = "bk" + UUID.randomUUID().toString().substring(0, 10);
        String stamp = now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String filename = "legalcase-backup-" + stamp + "-" + id + ".lcebak.enc";

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("format", "LegalCaseERP-EncryptedBackup-1.0");
        snapshot.put("createdAt", now.toString());
        snapshot.put("createdBy", createdBy);

        Map<String, List<Map<String, Object>>> tableDump = new LinkedHashMap<>();
        long totalRows = 0;

        try (Connection c = ds.getConnection()) {
            for (String table : TABLES) {
                List<Map<String, Object>> rows = dumpTable(c, table);
                tableDump.put(table, rows);
                totalRows += rows.size();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Database snapshot failed", e);
        }
        snapshot.put("tables", tableDump);

        byte[] encryptedPayload;
        try {
            byte[] jsonBytes = mapper.writeValueAsBytes(snapshot);
            ByteArrayOutputStream gzOut = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(gzOut)) {
                gz.write(jsonBytes);
            }
            String cipherB64 = encryption.encrypt(gzOut.toByteArray());
            encryptedPayload = cipherB64.getBytes(StandardCharsets.UTF_8);
            Files.write(backupDir.resolve(filename), encryptedPayload);
        } catch (Exception e) {
            throw new IllegalStateException("Could not encrypt and write backup", e);
        }
        double backupMs = Math.round(((System.nanoTime() - t0) / 1_000_000.0) * 100.0) / 100.0;

        // Immediate restore dry-run verification: read from disk -> AES-GCM decrypt -> GZIP decompress -> parse JSON
        long r0 = System.nanoTime();
        boolean verified = verifyBackupFile(filename, totalRows);
        double restoreMs = Math.round(((System.nanoTime() - r0) / 1_000_000.0) * 100.0) / 100.0;

        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO system_backups(id, filename, created_by, created_at, size_bytes,
                                                total_rows, backup_ms, restore_verify_ms, verified)
                     VALUES (?,?,?,?,?,?,?,?,?)""")) {
            ps.setString(1, id);
            ps.setString(2, filename);
            ps.setString(3, createdBy);
            ps.setTimestamp(4, Timestamp.valueOf(now));
            ps.setLong(5, encryptedPayload.length);
            ps.setLong(6, totalRows);
            ps.setDouble(7, backupMs);
            ps.setDouble(8, restoreMs);
            ps.setBoolean(9, verified);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not record backup metadata", e);
        }

        return new BackupRecord(id, filename, createdBy, now, encryptedPayload.length,
                totalRows, backupMs, restoreMs, verified);
    }

    @SuppressWarnings("unchecked")
    public boolean verifyBackupFile(String filename, long expectedMinRows) {
        try {
            Path p = safePath(filename);
            if (!Files.exists(p)) return false;
            String cipherB64 = Files.readString(p, StandardCharsets.UTF_8);
            byte[] compressed = encryption.decrypt(cipherB64);
            byte[] jsonBytes;
            try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
                jsonBytes = gis.readAllBytes();
            }
            Map<String, Object> parsed = mapper.readValue(jsonBytes, Map.class);
            Map<String, List<?>> tables = (Map<String, List<?>>) parsed.get("tables");
            if (tables == null) return false;
            long count = tables.values().stream().mapToLong(List::size).sum();
            return count >= expectedMinRows;
        } catch (Exception e) {
            return false;
        }
    }

    public byte[] readBackupBytes(String filename) {
        try {
            Path p = safePath(filename);
            if (!Files.exists(p)) return null;
            return Files.readAllBytes(p);
        } catch (Exception e) {
            return null;
        }
    }

    public List<BackupRecord> listBackups() {
        List<BackupRecord> out = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM system_backups ORDER BY created_at DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new BackupRecord(
                        rs.getString("id"),
                        rs.getString("filename"),
                        rs.getString("created_by"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getLong("size_bytes"),
                        rs.getLong("total_rows"),
                        rs.getDouble("backup_ms"),
                        rs.getDouble("restore_verify_ms"),
                        rs.getBoolean("verified")
                ));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not list backups", e);
        }
        return out;
    }

    private Path safePath(String filename) {
        String clean = filename == null ? "" : filename.replaceAll("[^a-zA-Z0-9._-]", "");
        return backupDir.resolve(clean).normalize();
    }

    private List<Map<String, Object>> dumpTable(Connection c, String table) throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM " + table)) {
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= cols; i++) {
                    Object val = rs.getObject(i);
                    row.put(meta.getColumnLabel(i).toLowerCase(Locale.ROOT),
                            val == null ? null : String.valueOf(val));
                }
                out.add(row);
            }
        }
        return out;
    }
}
