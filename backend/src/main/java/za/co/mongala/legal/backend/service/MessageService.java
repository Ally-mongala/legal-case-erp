package za.co.mongala.legal.backend.service;

import za.co.mongala.legal.model.ClientMessage;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Persistence of the firm-client message thread (FR5) with read-state for both sides. */
public class MessageService {

    private final DataSource ds;

    public MessageService(DataSource ds) {
        this.ds = ds;
    }

    public void insert(ClientMessage m) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     INSERT INTO messages(id, case_id, from_name, subject, body, sent_at, from_lawyer,
                                          read_by_client, read_by_lawyer)
                     VALUES (?,?,?,?,?,?,?,?,?)""")) {
            ps.setString(1, m.getId());
            ps.setString(2, m.getCaseId());
            ps.setString(3, m.getFromName());
            ps.setString(4, m.getSubject());
            ps.setString(5, m.getBody());
            ps.setTimestamp(6, Timestamp.valueOf(m.getSentAt()));
            ps.setBoolean(7, m.isFromLawyer());
            ps.setBoolean(8, m.isReadByClient());
            ps.setBoolean(9, m.isReadByLawyer());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not insert message", e);
        }
        // push an instant notification to any connected browser (SSE) so the
        // unread bell refreshes immediately
        EventBus.publish("new-message", m.getCaseId());
    }

    public List<ClientMessage> byCase(String caseId) {
        List<ClientMessage> out = new ArrayList<>();
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM messages WHERE case_id = ? ORDER BY sent_at")) {
            ps.setString(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not query messages", e);
        }
        return out;
    }

    /** Unread messages for a client: lawyer->client messages the client hasn't read. */
    public long unreadCountForClient(String clientId) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     SELECT COUNT(*) FROM messages m
                     JOIN cases c ON c.id = m.case_id
                     WHERE c.client_id = ? AND m.from_lawyer = TRUE AND m.read_by_client = FALSE""")) {
            ps.setString(1, clientId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not count unread messages", e);
        }
    }

    /** Unread client->lawyer messages (alert icon for the lawyer, all firm cases). */
    public long unreadCountForLawyer() {
        try (Connection conn = ds.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM messages WHERE from_lawyer = FALSE AND read_by_lawyer = FALSE")) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not count unread lawyer messages", e);
        }
    }

    public void markCaseRead(String caseId) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE messages SET read_by_client = TRUE WHERE case_id = ? AND from_lawyer = TRUE")) {
            ps.setString(1, caseId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not mark messages read", e);
        }
    }

    public void markCaseReadByLawyer(String caseId) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE messages SET read_by_lawyer = TRUE WHERE case_id = ? AND from_lawyer = FALSE")) {
            ps.setString(1, caseId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not mark lawyer messages read", e);
        }
    }

    private ClientMessage map(ResultSet rs) throws SQLException {
        boolean rb = false, rl = false;
        try { rb = rs.getBoolean("read_by_client"); } catch (SQLException ignored) {}
        try { rl = rs.getBoolean("read_by_lawyer"); } catch (SQLException ignored) {}
        return new ClientMessage(
                rs.getString("id"),
                rs.getString("case_id"),
                rs.getString("from_name"),
                rs.getString("subject"),
                rs.getString("body"),
                rs.getTimestamp("sent_at").toLocalDateTime(),
                rs.getBoolean("from_lawyer"),
                rb, rl);
    }
}
