package za.co.mongala.legal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * A deadline / calendar event for a case (FR4, US5). `synced` indicates whether
 * the event has been pushed to Google Calendar. `reminderDays` is the configurable
 * advance-notice window (e.g. remind me 3 days before).
 */
public class Deadline {
    public enum DeadlineType {
        HEARING("Hearing"), FILING("Filing"), MEETING("Meeting"), OTHER("Other");

        private final String displayName;

        DeadlineType(String displayName) { this.displayName = displayName; }

        public String getDisplayName() { return displayName; }
    }

    private final String id;
    private final String caseId;
    private final String caseReference;
    private final String title;
    private final LocalDate date;
    private final DeadlineType type;
    private int reminderDays;
    private boolean synced;

    @JsonCreator
    public Deadline(@JsonProperty("id") String id,
                    @JsonProperty("caseId") String caseId,
                    @JsonProperty("caseReference") String caseReference,
                    @JsonProperty("title") String title,
                    @JsonProperty("date") LocalDate date,
                    @JsonProperty("type") DeadlineType type,
                    @JsonProperty("reminderDays") int reminderDays,
                    @JsonProperty("synced") boolean synced) {
        this.id = id;
        this.caseId = caseId;
        this.caseReference = caseReference;
        this.title = title;
        this.date = date;
        this.type = type;
        this.reminderDays = reminderDays;
        this.synced = synced;
    }

    public String getId() { return id; }
    public String getCaseId() { return caseId; }
    public String getCaseReference() { return caseReference; }
    public String getTitle() { return title; }
    public LocalDate getDate() { return date; }
    public DeadlineType getType() { return type; }
    public int getReminderDays() { return reminderDays; }
    public void setReminderDays(int d) { this.reminderDays = d; }
    public boolean isSynced() { return synced; }
    public void setSynced(boolean s) { this.synced = s; }

    /** Days from today until the deadline (negative = overdue). */
    public long daysLeft() {
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), date);
    }
}
