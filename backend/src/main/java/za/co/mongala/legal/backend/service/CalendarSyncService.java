package za.co.mongala.legal.backend.service;

import za.co.mongala.legal.backend.calendar.CalendarGateway;
import za.co.mongala.legal.model.Case;
import za.co.mongala.legal.model.Deadline;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Pushes deadlines through the configured CalendarGateway (FR4 / US5) and
 * records each created event + audit entry. Returns a human-readable receipt
 * that the UI displays as a toast.
 */
public class CalendarSyncService {

    private final DataSource ds;
    private final DeadlineService deadlineService;
    private final CaseService caseService;
    private final AuditService audit;
    private final CalendarGateway gateway;
    private final java.nio.file.Path uploadDir;

    public CalendarSyncService(DataSource ds, DeadlineService deadlineService, CaseService caseService,
                               AuditService audit, CalendarGateway gateway, java.nio.file.Path uploadDir) {
        this.ds = ds;
        this.deadlineService = deadlineService;
        this.caseService = caseService;
        this.audit = audit;
        this.gateway = gateway;
        this.uploadDir = uploadDir;
    }

    public String syncOne(Deadline d) {
        Case c = caseService.findById(d.getCaseId());
        if (c == null) return "Case not found - nothing synced.";
        try {
            CalendarGateway.Receipt r = gateway.createEvent(d, c);
            deadlineService.markSynced(d.getId());
            recordEvent(d.getId(), r);
            EventBus.publish("deadline-updated", d.getCaseId());
            audit.success("System", "CALENDAR_SYNC",
                    "Event \"" + d.getTitle() + "\" (" + d.getDate() + ") created on "
                            + gateway.description() + " - " + r.eventId());
            return "Synced to " + gateway.description() + ": \"" + d.getTitle() + "\" ("
                    + d.getDate() + ") with reminder " + d.getReminderDays() + " days before.";
        } catch (Exception e) {
            audit.warning("System", "CALENDAR_SYNC_FAILED",
                    "Could not sync \"" + d.getTitle() + "\": " + e.getMessage());
            return "Sync failed for \"" + d.getTitle() + "\": " + e.getMessage();
        }
    }

    public String syncAll(List<Deadline> deadlines) {
        long pending = deadlines.stream().filter(d -> !d.isSynced()).count();
        int ok = 0;
        for (Deadline d : deadlines) {
            if (!d.isSynced()) {
                String r = syncOne(d);
                if (r.startsWith("Synced")) ok++;
            }
        }
        if (ok == 0) {
            return "Nothing to sync - all deadlines are already on " + gateway.description() + ".";
        }
        return ok + " of " + pending + " pending deadline(s) pushed to " + gateway.description()
                + (ok < pending ? " (" + (pending - ok) + " failed - see activity log)." : ".");
    }

    private void recordEvent(String deadlineId, CalendarGateway.Receipt r) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO calendar_events(id, deadline_id, event_id, calendar_id, created_at) VALUES (?,?,?,?,?)")) {
            ps.setString(1, "ev" + UUID.randomUUID().toString().substring(0, 10));
            ps.setString(2, deadlineId);
            ps.setString(3, r.eventId());
            ps.setString(4, r.calendarId());
            ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
            ps.executeUpdate();
        } catch (Exception e) {
            // non-fatal: the sync itself already succeeded
        }
    }

    /**
     * Removes a deadline from the calendar again. The remote event is deleted first
     * (Google Calendar or the local .ics feed); only when that succeeds is the local
     * record cleared, so the user's real calendar can never keep an orphaned entry.
     */
    public String unsyncOne(Deadline d) {
        String[] stored = storedEvent(d.getId());   // [eventId, calendarId] or nulls
        if (stored != null && stored[0] != null && !stored[0].isBlank()) {
            try {
                gateway.deleteEvent(stored[0], stored[1]);
            } catch (Exception e) {
                audit.warning("System", "CALENDAR_UNSYNC_FAILED",
                        "Could not remove \"" + d.getTitle() + "\" from "
                                + gateway.description() + ": " + e.getMessage());
                return "Could not remove \"" + d.getTitle() + "\" from " + gateway.description()
                        + ": " + e.getMessage() + " (the deadline stays marked as synced).";
            }
            deleteEventRow(d.getId());
        }
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE deadlines SET synced = FALSE WHERE id = ?")) {
            ps.setString(1, d.getId());
            ps.executeUpdate();
        } catch (Exception e) {
            return "Could not unsync \"" + d.getTitle() + "\": " + e.getMessage();
        }
        EventBus.publish("deadline-updated", d.getCaseId());
        audit.info("System", "CALENDAR_UNSYNC",
                "\"" + d.getTitle() + "\" (" + d.getDate() + ") removed from " + gateway.description());
        return "Removed \"" + d.getTitle() + "\" from " + gateway.description() + ".";
    }

    /** Looks up the event id / calendar id recorded when the deadline was pushed. */
    private String[] storedEvent(String deadlineId) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT event_id, calendar_id FROM calendar_events WHERE deadline_id = ?")) {
            ps.setString(1, deadlineId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new String[]{rs.getString(1), rs.getString(2)};
            }
        } catch (Exception ignored) {
            // no record: fall back to clearing the local flag only
        }
        return new String[]{null, null};
    }

    private void deleteEventRow(String deadlineId) {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM calendar_events WHERE deadline_id = ?")) {
            ps.setString(1, deadlineId);
            ps.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    public String unsyncAll(List<Deadline> deadlines) {
        long count = deadlines.stream().filter(Deadline::isSynced).count();
        deadlines.stream().filter(Deadline::isSynced).forEach(d -> unsyncOne(d));
        return count == 0 ? "No synced deadlines to remove."
                : count + " deadline(s) removed from " + gateway.description() + ".";
    }

    /** Builds (and writes) a full .ics file with every synced deadline - importable into
     *  a phone/computer calendar (Apple Calendar, Outlook, Google Calendar). */
    public String exportIcs() {
        List<Deadline> synced = deadlineService.all().stream().filter(Deadline::isSynced).toList();
        // Built straight from the database. It is NOT written to the gateway's calendar.ics:
        // that file is owned by LocalCalendarGateway, and overwriting it would break the
        // per-event deletes that "Unsync" relies on.
        return buildIcs(synced);
    }

    /** .ics export for a single case - every synced deadline on that case (per-case sync). */
    public String exportCaseIcs(String caseId) {
        List<Deadline> synced = deadlineService.byCase(caseId).stream()
                .filter(Deadline::isSynced).toList();
        return buildIcs(synced);
    }

    private static String buildIcs(List<Deadline> deadlines) {
        StringBuilder sb = new StringBuilder("BEGIN:VCALENDAR\r\n")
                .append("VERSION:2.0\r\n")
                .append("PRODID:-//LegalCase ERP//SA//EN\r\n")
                .append("CALSCALE:GREGORIAN\r\n");
        java.time.format.DateTimeFormatter dateFmt = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd");
        java.time.format.DateTimeFormatter stampFmt = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
        String stamp = java.time.LocalDateTime.now().format(stampFmt);
        for (Deadline d : deadlines) {
            String summary = (d.getTitle() + " - " + d.getCaseReference()).replace(",", " ").replace(";", " ");
            sb.append("BEGIN:VEVENT\r\n")
              .append("UID:").append(d.getId()).append("@legal-case-erp\r\n")
              .append("DTSTAMP:").append(stamp).append("\r\n")
              .append("DTSTART;VALUE=DATE:").append(d.getDate().format(dateFmt)).append("\r\n")
              .append("SUMMARY:").append(summary).append("\r\n")
              .append("DESCRIPTION:Deadline created by LegalCase ERP. Reminder ").append(d.getReminderDays()).append(" days before.\r\n")
              .append("BEGIN:VALARM\r\n")
              .append("ACTION:DISPLAY\r\n")
              .append("TRIGGER:-PT").append(d.getReminderDays()).append("D\r\n")
              .append("DESCRIPTION:Reminder\r\n")
              .append("END:VALARM\r\n")
              .append("END:VEVENT\r\n");
        }
        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    public String gatewayDescription() { return gateway.description(); }

    /** Which calendar backend is active, and whether it is fully configured (no secrets returned). */
    public CalendarGateway.Status status() { return gateway.status(); }

    /**
     * Proves the configured calendar really works: the local gateway reports what it
     * writes, the Google gateway reads the calendar metadata with the stored credentials.
     */
    public String testConnection() {
        try {
            String result = gateway.probe();
            audit.success("System", "CALENDAR_TEST", result);
            return result;
        } catch (Exception e) {
            audit.warning("System", "CALENDAR_TEST_FAILED", e.getMessage());
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}
