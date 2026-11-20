package za.co.mongala.legal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * A client's rating of their lawyer (FR7). Stored server-side; the client
 * portal has a "Rate your lawyer" tab.
 */
public class Rating {
    private final String id;
    private final String caseId;
    private final String clientId;
    private final String lawyerName;
    private final int score;          // 1..5
    private final String comment;
    private final LocalDateTime createdAt;

    @JsonCreator
    public Rating(@JsonProperty("id") String id,
                  @JsonProperty("caseId") String caseId,
                  @JsonProperty("clientId") String clientId,
                  @JsonProperty("lawyerName") String lawyerName,
                  @JsonProperty("score") int score,
                  @JsonProperty("comment") String comment,
                  @JsonProperty("createdAt") LocalDateTime createdAt) {
        this.id = id;
        this.caseId = caseId;
        this.clientId = clientId;
        this.lawyerName = lawyerName;
        this.score = score;
        this.comment = comment;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getCaseId() { return caseId; }
    public String getClientId() { return clientId; }
    public String getLawyerName() { return lawyerName; }
    public int getScore() { return score; }
    public String getComment() { return comment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
