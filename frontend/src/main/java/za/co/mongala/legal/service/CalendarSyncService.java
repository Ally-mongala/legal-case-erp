package za.co.mongala.legal.service;

import za.co.mongala.legal.model.Deadline;

import java.util.List;

/**
 * CalendarSyncService - DEMO mode: simulates Google Calendar integration (FR4 / US5).
 * In CONNECTED mode the UI uses {@link za.co.mongala.legal.client.RemoteCalendarSync},
 * and the backend performs the real sync (Google Calendar API or local .ics gateway).
 */
public class CalendarSyncService implements ICalendarSyncService {

    /** Marks the given deadlines as synced and returns a human-readable receipt. */
    public String sync(Deadline deadline) {
        deadline.setSynced(true);
        return "Google Calendar event created: \"" + deadline.getTitle() + "\" ("
                + deadline.getDate() + ") with reminder " + deadline.getReminderDays() + " days before.";
    }

    public String syncAll(List<Deadline> deadlines) {
        long count = deadlines.stream().filter(d -> !d.isSynced()).count();
        deadlines.forEach(d -> d.setSynced(true));
        if (count == 0) {
            return "Everything is already in sync - no new Google Calendar events were created.";
        }
        return count + " deadline(s) pushed to Google Calendar with configured reminders.";
    }
}
