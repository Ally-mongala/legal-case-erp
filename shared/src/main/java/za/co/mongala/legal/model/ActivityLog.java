package za.co.mongala.legal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * One entry of the tamper-evident audit trail (FR10). Every sensitive action -
 * login, upload, AI processing, summary approval, calendar sync - is recorded
 * here by the backend and shown on the lawyer's Activity screen.
 */
public class ActivityLog {
    public enum Level { INFO, WARNING, SUCCESS }

    private final LocalDateTime timestamp;
    private final String userName;
    private final String action;
    private final String details;
    private final Level level;

    @JsonCreator
    public ActivityLog(@JsonProperty("timestamp") LocalDateTime timestamp,
                       @JsonProperty("userName") String userName,
                       @JsonProperty("action") String action,
                       @JsonProperty("details") String details,
                       @JsonProperty("level") Level level) {
        this.timestamp = timestamp;
        this.userName = userName;
        this.action = action;
        this.details = details;
        this.level = level;
    }

    public LocalDateTime getTimestamp() { return timestamp; }
    public String getUserName() { return userName; }
    public String getAction() { return action; }
    public String getDetails() { return details; }
    public Level getLevel() { return level; }
}
