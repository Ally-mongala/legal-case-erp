package za.co.mongala.legal.backend.service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/** Usage metrics for the admin system screen (FR10). */
public class MetricsService {

    private final DataSource ds;

    public MetricsService(DataSource ds) {
        this.ds = ds;
    }

    public Map<String, Object> metrics() {
        Map<String, Object> m = new LinkedHashMap<>();
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            m.put("totalUsers", scalar(st, "SELECT COUNT(*) FROM users"));
            m.put("lawyers", scalar(st, "SELECT COUNT(*) FROM users WHERE role = 'LAWYER'"));
            m.put("clients", scalar(st, "SELECT COUNT(*) FROM users WHERE role = 'CLIENT'"));
            m.put("revokedUsers", scalar(st, "SELECT COUNT(*) FROM users WHERE active = FALSE"));
            m.put("totalCases", scalar(st, "SELECT COUNT(*) FROM cases"));
            m.put("activeCases", scalar(st, "SELECT COUNT(*) FROM cases WHERE status <> 'CLOSED'"));
            m.put("totalDocuments", scalar(st, "SELECT COUNT(*) FROM documents"));
            m.put("processedDocuments", scalar(st, "SELECT COUNT(*) FROM documents WHERE status = 'PROCESSED'"));
            m.put("failedDocuments", scalar(st, "SELECT COUNT(*) FROM documents WHERE status = 'FAILED'"));
            m.put("totalDeadlines", scalar(st, "SELECT COUNT(*) FROM deadlines"));
            m.put("pendingSyncs", scalar(st, "SELECT COUNT(*) FROM deadlines WHERE synced = FALSE"));
            m.put("totalMessages", scalar(st, "SELECT COUNT(*) FROM messages"));
            m.put("totalRatings", scalar(st, "SELECT COUNT(*) FROM ratings"));
            m.put("trainingRuns", scalar(st, "SELECT COUNT(*) FROM training_runs"));
            m.put("datasets", scalar(st, "SELECT COUNT(*) FROM datasets"));
            m.put("avgConfidence", avgConfidence(st));
        } catch (Exception e) {
            throw new IllegalStateException("Could not compute metrics", e);
        }
        return m;
    }

    private static long scalar(Statement st, String sql) throws Exception {
        try (ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private static double avgConfidence(Statement st) throws Exception {
        try (ResultSet rs = st.executeQuery("SELECT AVG(confidence) FROM extractions")) {
            return rs.next() && rs.getObject(1) != null ? rs.getDouble(1) : 0.0;
        }
    }
}
