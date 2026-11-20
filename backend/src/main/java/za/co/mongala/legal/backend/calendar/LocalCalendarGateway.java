package za.co.mongala.legal.backend.calendar;

import za.co.mongala.legal.model.Case;
import za.co.mongala.legal.model.Deadline;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Default calendar implementation: writes a valid iCalendar (.ics) feed that can be
 * imported into Google Calendar, Outlook or Apple Calendar, so the sync is genuinely
 * functional without any OAuth credentials.
 *
 * The file is APPENDED to (one VEVENT per deadline, de-duplicated by UID) instead of
 * being overwritten, so "sync all" produces a calendar with every synced deadline in
 * it - not just the last one.
 */
public class LocalCalendarGateway implements CalendarGateway {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final Path icsPath;

    public LocalCalendarGateway(Path dataDir) {
        this.icsPath = dataDir.resolve("calendar.ics");
    }

    @Override
    public synchronized Receipt createEvent(Deadline d, Case c) throws Exception {
        String eventId = "local-" + shortId();
        String calendarId = "local-legal-case-erp";
        String uid = eventId + "@legal-case-erp";

        List<String> bodies = readEventBlocks();
        bodies.removeIf(b -> b.contains("UID:" + uid));
        bodies.add(eventBlock(uid, d));
        writeCalendar(bodies);

        return new Receipt(eventId, calendarId);
    }

    @Override
    public synchronized void deleteEvent(String eventId, String calendarId) throws Exception {
        if (eventId == null || eventId.isBlank()) return;
        String uid = eventId + "@legal-case-erp";
        List<String> bodies = readEventBlocks();
        boolean removed = bodies.removeIf(b -> b.contains("UID:" + uid));
        if (removed) writeCalendar(bodies);
    }

    /** Nothing remote to test: report what the local backend does instead. */
    @Override
    public String probe() {
        return "Local calendar is active - no Google credentials needed. "
                + "Every synced deadline is written to data/calendar.ics and can be imported "
                + "into Google, Outlook or Apple Calendar (or downloaded from this screen).";
    }

    @Override
    public Status status() {
        return new Status("local", false, true, "local-legal-case-erp",
                "Local calendar (.ics export) - set the LCE_GOOGLE_* variables to push events "
                        + "into a real Google Calendar.", List.of());
    }

    @Override
    public String description() {
        return "Local calendar (.ics export - importable into Google Calendar)";
    }

    /* ------------------------------------------------------------------ */

    private String eventBlock(String uid, Deadline d) {
        String summary = (d.getTitle() + " - " + d.getCaseReference())
                .replace("\r", " ").replace("\n", " ").replace(",", " ").replace(";", " ");
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
                .format(java.time.LocalDateTime.now());
        return "BEGIN:VEVENT\r\n"
                + "UID:" + uid + "\r\n"
                + "DTSTAMP:" + stamp + "\r\n"
                + "DTSTART;VALUE=DATE:" + d.getDate().format(DATE) + "\r\n"
                + "SUMMARY:" + summary + "\r\n"
                + "DESCRIPTION:Deadline auto-created by LegalCase ERP from AI extraction. "
                + "Reminder " + d.getReminderDays() + " days before.\r\n"
                + "BEGIN:VALARM\r\n"
                + "ACTION:DISPLAY\r\n"
                + "TRIGGER:-PT" + Math.max(0, d.getReminderDays()) + "D\r\n"
                + "DESCRIPTION:Reminder\r\n"
                + "END:VALARM\r\n"
                + "END:VEVENT";
    }

    /** Returns the VEVENT blocks currently in the file (empty when it does not exist yet). */
    private List<String> readEventBlocks() {
        List<String> blocks = new ArrayList<>();
        try {
            if (!Files.exists(icsPath)) return blocks;
            String content = Files.readString(icsPath, StandardCharsets.UTF_8);
            int from = 0;
            while (true) {
                int start = content.indexOf("BEGIN:VEVENT", from);
                if (start < 0) break;
                int end = content.indexOf("END:VEVENT", start);
                if (end < 0) break;
                end += "END:VEVENT".length();
                blocks.add(content.substring(start, end));
                from = end;
            }
        } catch (Exception ignored) {
            // an unreadable file is treated as an empty calendar
        }
        return blocks;
    }

    private void writeCalendar(List<String> eventBlocks) throws Exception {
        StringBuilder sb = new StringBuilder()
                .append("BEGIN:VCALENDAR\r\n")
                .append("VERSION:2.0\r\n")
                .append("PRODID:-//LegalCase ERP//SA//EN\r\n")
                .append("CALSCALE:GREGORIAN\r\n");
        for (String block : eventBlocks) sb.append(block).append("\r\n");
        sb.append("END:VCALENDAR\r\n");
        Files.writeString(icsPath, sb.toString(), StandardCharsets.UTF_8);
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
