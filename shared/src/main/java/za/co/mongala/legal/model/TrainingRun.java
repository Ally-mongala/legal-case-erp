package za.co.mongala.legal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * A model retraining run (FR10). Admins review pending runs and approve them
 * (ETR3: retraining pipeline with human in the loop).
 */
public class TrainingRun {
    public enum Status { PENDING, APPROVED, RUNNING, COMPLETED, REJECTED }

    private final String id;
    private final String modelName;
    private final String datasetId;
    private final Status status;
    private final String requestedBy;
    private final LocalDateTime requestedAt;
    private LocalDateTime approvedAt;
    private Double accuracy;

    @JsonCreator
    public TrainingRun(@JsonProperty("id") String id,
                       @JsonProperty("modelName") String modelName,
                       @JsonProperty("datasetId") String datasetId,
                       @JsonProperty("status") Status status,
                       @JsonProperty("requestedBy") String requestedBy,
                       @JsonProperty("requestedAt") LocalDateTime requestedAt,
                       @JsonProperty("approvedAt") LocalDateTime approvedAt,
                       @JsonProperty("accuracy") Double accuracy) {
        this.id = id;
        this.modelName = modelName;
        this.datasetId = datasetId;
        this.status = status;
        this.requestedBy = requestedBy;
        this.requestedAt = requestedAt;
        this.approvedAt = approvedAt;
        this.accuracy = accuracy;
    }

    public String getId() { return id; }
    public String getModelName() { return modelName; }
    public String getDatasetId() { return datasetId; }
    public Status getStatus() { return status; }
    public String getRequestedBy() { return requestedBy; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public Double getAccuracy() { return accuracy; }
}
