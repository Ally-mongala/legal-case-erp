package za.co.mongala.legal.backend.service;

import za.co.mongala.legal.model.Case;
import za.co.mongala.legal.model.Deadline;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Persistence of deadlines / calendar events (`deadlines` table, FR4). */
public class DeadlineService {

    private final DataSource ds;

    public DeadlineService(DataSource ds) {
        this.ds = ds;
    }

    public void insert(Deadline d) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     INSERT INTO deadlines(id, case_id, title, deadline_date, dl_type, reminder_days, synced)
                     VALUES (?,?,?,?,?,?,?)""")) {
            ps.setString(1, d.getId());
            ps.setString(2, d.getCaseId());
            ps.setString(3, d.getTitle());
            ps.setDate(4, Date.valueOf(d.getDate()));
            ps.setString(5, d.getType().name());
            ps.setInt(6, d.getReminderDays());
            ps.setBoolean(7, d.isSynced());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not insert deadline", e);
        }
        // push a live update so open dashboards/calendars refresh instantly
        EventBus.publish("deadline-updated", d.getCaseId());
    }

    public List<Deadline> all() {
        List<Deadline> out = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM deadlines ORDER BY deadline_date")) {
            while (rs.next()) out.add(map(rs));
        } catch (SQLException e) {
            throw new IllegalStateException("Could not query deadlines", e);
        }
        return out;
    }

    public List<Deadline> byCase(String caseId) {
        List<Deadline> out = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM deadlines WHERE case_id = ? ORDER BY deadline_date")) {
            ps.setString(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not query deadlines", e);
        }
        return out;
    }

    public Deadline findById(String id) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM deadlines WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not query deadline", e);
        }
    }

    public void markSynced(String id) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE deadlines SET synced = TRUE WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not mark deadline synced", e);
        }
    }

    public List<Deadline> pending() {
        List<Deadline> out = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM deadlines WHERE synced = FALSE ORDER BY deadline_date")) {
            while (rs.next()) out.add(map(rs));
        } catch (SQLException e) {
            throw new IllegalStateException("Could not query pending deadlines", e);
        }
        return out;
    }

    /** Fills the caseReference by joining the cases table. */
    public Deadline map(ResultSet rs) throws SQLException {
        String caseId = rs.getString("case_id");
        Case c = null;
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT reference FROM cases WHERE id = ?")) {
            ps.setString(1, caseId);
            try (ResultSet r2 = ps.executeQuery()) {
                if (r2.next()) c = new Case(caseId, r2.getString("reference"), "", "", "", "", null,
                        "", "", "", null, 0);
            }
        }
        return new Deadline(
                rs.getString("id"),
                caseId,
                c == null ? "?" : c.getReference(),
                rs.getString("title"),
                rs.getDate("deadline_date").toLocalDate(),
                Deadline.DeadlineType.valueOf(rs.getString("dl_type")),
                rs.getInt("reminder_days"),
                rs.getBoolean("synced"));
    }
}
