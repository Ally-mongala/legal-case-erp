package za.co.mongala.legal.backend.calendar;

import za.co.mongala.legal.model.Case;
import za.co.mongala.legal.model.Deadline;

/**
 * Abstraction over the calendar backend (FR4).
 * Two implementations exist:
 *  - LocalCalendarGateway  (default - builds a real, importable .ics calendar so the
 *                           sync is demonstrable with zero setup or credentials)
 *  - GoogleCalendarGateway (real Google Calendar API v3 via OAuth2 refresh token -
 *                           activate by setting the LCE_GOOGLE_* environment variables)
 *
 * The gateway is also responsible for removing an event again, so that "Unsync"
 * does not silently leave an orphaned entry in the user's real calendar.
 */
public interface CalendarGateway {

    record Receipt(String eventId, String calendarId) {}

    /** What the gateway can do, for the status panel in the UI (no secrets in here). */
    record Status(String gateway, boolean remote, boolean configured,
                  String calendarId, String detail, java.util.List<String> missingVariables) {}

    Receipt createEvent(Deadline deadline, Case legalCase) throws Exception;

    /**
     * Removes a previously created event.
     * Throws when the backend refused the removal, so the caller can keep the local
     * record and report the truth instead of pretending the calendar is clean.
     */
    void deleteEvent(String eventId, String calendarId) throws Exception;

    /**
     * Creates and immediately deletes a throw-away event, or reads the calendar
     * metadata, to prove the credentials work. Returns a short human message.
     */
    String probe() throws Exception;

    Status status();

    String description();
}
