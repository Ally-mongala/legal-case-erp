package za.co.mongala.legal.backend.service;

import za.co.mongala.legal.model.Case;
import za.co.mongala.legal.model.Deadline;
import za.co.mongala.legal.model.User;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Email reminders for upcoming deadlines (closes the "timely reminders" requirement).
 *
 * A deadline is due for a reminder when it is not yet past and falls inside its own
 * reminder window (Deadline.reminderDays, or 7 days if that is 0). One reminder is
 * queued per deadline and recipient, so running the job twice never sends duplicates.
 * Recipients are the client on the case and the firm address (LCE_FIRM_EMAIL), if set.
 *
 * Reminders are queued in the reminders table. They are sent only when SMTP is configured
 * (see MailSender). Without SMTP they stay QUEUED so the lawyer can see them.
 */
public class ReminderService {

    public static final int DEFAULT_WINDOW_DAYS = 7;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    public record Reminder(String id, String deadlineId, String caseReference, String recipient,
                           String subject, String body, String status, String error,
                           LocalDateTime createdAt, LocalDateTime sentAt) {}

    public record RunResult(int queued, int sent, int failed, int waiting, boolean mailConfigured) {}

    private final DataSource ds;
    private final UserService users;
    private final CaseService cases;
    private final DeadlineService deadlines;
    private final MailSender mail;
    private final AuditService audit;
    private final String firmEmail;

    public ReminderService(DataSource ds, UserService users, CaseService cases, DeadlineService deadlines,
                           MailSender mail, AuditService audit, String firmEmail) {
        this.ds = ds;
        this.users = users;
        this.cases = cases;
        this.deadlines = deadlines;
        this.mail = mail;
        this.audit = audit;
        this.firmEmail = firmEmail;
    }

    /** Queues new reminders, then sends any queued ones if SMTP is configured. */
    public synchronized RunResult runOnce() {
        int queued = 0;
        for (Deadline d : deadlines.all()) {
            long left = d.daysLeft();
            int window = d.getReminderDays() > 0 ? d.getReminderDays() : DEFAULT_WINDOW_DAYS;
            if (left < 0 || left > window) continue;
            Case c = cases.findById(d.getCaseId());
            if (c == null) continue;
            for (String recipient : recipientsFor(c)) {
                if (queueIfNew(d, c, recipient, left)) queued++;
            }
        }

        int sent = 0;
        int failed = 0;
        if (mail.isConfigured()) {
            for (Reminder r : byStatus("QUEUED")) {
                try {
                    mail.send(r.recipient(), r.subject(), r.body());
                    setStatus(r.id(), "SENT", null);
                    sent++;
                } catch (Exception e) {
                    setStatus(r.id(), "FAILED", truncate(e.getMessage()));
                    failed++;
                }
            }
        }
        int waiting = byStatus("QUEUED").size();
        audit.info("System", "REMINDERS_RUN",
                "Queued " + queued + ", sent " + sent + ", failed " + failed + ", waiting " + waiting);
        return new RunResult(queued, sent, failed, waiting, mail.isConfigured());
    }

    /** The client on the case, plus the firm address if configured. */
    List<String> recipientsFor(Case c) {
        Set<String> out = new LinkedHashSet<>();
        for (User u : users.listAll()) {
            if (u.getId().equals(c.getClientId()) && u.isActive() && u.getEmail() != null) out.add(u.getEmail());
        }
        if (firmEmail != null && !firmEmail.isBlank()) out.add(firmEmail.trim());
        return new ArrayList<>(out);
    }

    private boolean queueIfNew(Deadline d, Case c, String recipient, long daysLeft) {
        if (exists(d.getId(), recipient)) return false;
        String dateText = d.getDate().format(DATE_FMT);
        String when = daysLeft == 0 ? "today" : daysLeft == 1 ? "tomorrow" : "in " + daysLeft + " days";
        String subject = "Reminder: " + d.getTitle() + " on " + dateText + " (" + c.getReference() + ")";
        String body = "Hello,\n\n"
                + "This is a reminder that \"" + d.getTitle() + "\" for case " + c.getReference()
                + " is due on " + dateText + " (" + when + ").\n\n"
                + "Please contact your attorney if you have any questions.\n\n"
                + "LegalCase ERP";
        insert(d, c, recipient, subject, body);
        return true;
    }

    private boolean exists(String deadlineId, String recipient) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM reminders WHERE deadline_id = ? AND recipient = ?")) {
            ps.setString(1, deadlineId);
            ps.setString(2, recipient);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getLong(1) > 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Reminder lookup failed", e);
        }
    }

    private void insert(Deadline d, Case c, String recipient, String subject, String body) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO reminders (id, deadline_id, case_reference, recipient, subject, body, status, error, created_at)"
                             + " VALUES (?, ?, ?, ?, ?, ?, 'QUEUED', NULL, ?)")) {
            ps.setString(1, "r" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
            ps.setString(2, d.getId());
            ps.setString(3, c.getReference());
            ps.setString(4, recipient);
            ps.setString(5, truncate(subject, 300));
            ps.setString(6, truncate(body, 2000));
            ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not queue reminder", e);
        }
    }

    private void setStatus(String id, String status, String error) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE reminders SET status = ?, error = ?, sent_at = ? WHERE id = ?")) {
            ps.setString(1, status);
            ps.setString(2, error);
            ps.setTimestamp(3, "SENT".equals(status) ? Timestamp.valueOf(LocalDateTime.now()) : null);
            ps.setString(4, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not update reminder", e);
        }
    }

    public boolean mailConfigured() {
        return mail.isConfigured();
    }

    /** Newest first, for the lawyer's reminders list. */
    public List<Reminder> recent(int limit) {
        List<Reminder> out = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM reminders ORDER BY created_at DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not list reminders", e);
        }
        return out;
    }

    private List<Reminder> byStatus(String status) {
        List<Reminder> out = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM reminders WHERE status = ? ORDER BY created_at")) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read reminders", e);
        }
        return out;
    }

    private static Reminder map(ResultSet rs) throws SQLException {
        Timestamp sent = rs.getTimestamp("sent_at");
        return new Reminder(rs.getString("id"), rs.getString("deadline_id"), rs.getString("case_reference"),
                rs.getString("recipient"), rs.getString("subject"), rs.getString("body"),
                rs.getString("status"), rs.getString("error"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                sent == null ? null : sent.toLocalDateTime());
    }

    private static String truncate(String s) { return truncate(s, 500); }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
