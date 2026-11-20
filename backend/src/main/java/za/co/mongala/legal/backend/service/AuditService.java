package za.co.mongala.legal.backend.service;

import za.co.mongala.legal.model.ActivityLog;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-side audit service with a tamper-evident hash chain (FR10 / FR11):
 * every row stores the SHA-256 of (its own content + the previous row's hash),
 * so any retroactive modification breaks the chain and is detectable.
 */
public class AuditService {

    private final DataSource ds;

    public AuditService(DataSource ds) {
        this.ds = ds;
    }

    public void log(String userName, String action, String details, ActivityLog.Level level) {
        // Truncate to microseconds BEFORE hashing: SQL TIMESTAMP columns in both H2 and
        // PostgreSQL store microsecond precision, so a nanosecond-precision timestamp
        // would be silently rounded on write and the stored hash could never be
        // recomputed from the persisted row - defeating the tamper-evidence guarantee
        // of FR-09. Verified by AuditChainIntegrationTest (TC-I04).
        LocalDateTime now = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        String userForRow = userName == null ? "System" : userName;
        String detailsForRow = details == null ? "" : details;
        String prevHash = lastHash();
        // The payload must be built from exactly the values written to the row so that
        // the chain can be re-verified by reading the table back.
        String payload = now + "|" + userForRow + "|" + action + "|" + detailsForRow + "|" + level + "|" + prevHash;
        String hash = sha256(payload);
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO audit_log(ts, user_name, action, details, level, prev_hash, hash) VALUES (?,?,?,?,?,?,?)")) {
            ps.setTimestamp(1, Timestamp.valueOf(now));
            ps.setString(2, userForRow);
            ps.setString(3, action);
            ps.setString(4, detailsForRow);
            ps.setString(5, level.name());
            ps.setString(6, prevHash);
            ps.setString(7, hash);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not write audit entry", e);
        }
    }

    private String lastHash() {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT hash FROM audit_log ORDER BY id DESC LIMIT 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getString(1) != null ? rs.getString(1) : "";
            }
        } catch (SQLException e) {
            return "";
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public void info(String user, String action, String details) { log(user, action, details, ActivityLog.Level.INFO); }
    public void success(String user, String action, String details) { log(user, action, details, ActivityLog.Level.SUCCESS); }
    public void warning(String user, String action, String details) { log(user, action, details, ActivityLog.Level.WARNING); }

    public List<ActivityLog> recent(int limit) {
        List<ActivityLog> out = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT ts, user_name, action, details, level FROM audit_log ORDER BY ts DESC, id DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ActivityLog(
                            rs.getTimestamp("ts").toLocalDateTime(),
                            rs.getString("user_name"),
                            rs.getString("action"),
                            rs.getString("details"),
                            ActivityLog.Level.valueOf(rs.getString("level"))));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read audit log", e);
        }
        return out;
    }

    public List<ActivityLog> all() { return recent(10_000); }

    public record ChainVerificationResult(
            boolean intact,
            int totalRows,
            Integer brokenAtId,
            String headHash,
            double elapsedMs,
            LocalDateTime verifiedAt
    ) {}

    /**
     * Re-computes every row's SHA-256 hash and previous-hash link across the entire
     * audit_log table to prove tamper-evidence live.
     */
    public ChainVerificationResult verifyChain() {
        long t0 = System.nanoTime();
        int count = 0;
        String expectedPrev = "";
        Integer brokenId = null;

        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, ts, user_name, action, details, level, prev_hash, hash FROM audit_log ORDER BY id ASC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                count++;
                int id = rs.getInt("id");
                String ts = String.valueOf(rs.getTimestamp("ts").toLocalDateTime());
                String user = rs.getString("user_name");
                String action = rs.getString("action");
                String details = rs.getString("details");
                String level = rs.getString("level");
                String prevHash = rs.getString("prev_hash") == null ? "" : rs.getString("prev_hash");
                String storedHash = rs.getString("hash") == null ? "" : rs.getString("hash");

                if (!expectedPrev.equals(prevHash)) {
                    brokenId = id;
                    break;
                }
                String payload = ts + "|" + user + "|" + action + "|" + details + "|" + level + "|" + prevHash;
                if (!sha256(payload).equals(storedHash)) {
                    brokenId = id;
                    break;
                }
                expectedPrev = storedHash;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not verify audit chain", e);
        }

        double ms = Math.round(((System.nanoTime() - t0) / 1_000_000.0) * 100.0) / 100.0;
        return new ChainVerificationResult(
                brokenId == null,
                count,
                brokenId,
                expectedPrev,
                ms,
                LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
        );
    }
}
