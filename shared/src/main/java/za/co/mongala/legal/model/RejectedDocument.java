package za.co.mongala.legal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * A document that was BLOCKED by AI validation, with the reason - used for
 * the admin "rejection reasons" report.
 */
public class RejectedDocument {
    private final String id;
    private final String fileName;
    private final String docType;
    private final String caseReference;
    private final String uploadedBy;
    private final LocalDateTime uploadedAt;
    private final String rejectionReason;

    @JsonCreator
    public RejectedDocument(@JsonProperty("id") String id,
                            @JsonProperty("fileName") String fileName,
                            @JsonProperty("docType") String docType,
                            @JsonProperty("caseReference") String caseReference,
                            @JsonProperty("uploadedBy") String uploadedBy,
                            @JsonProperty("uploadedAt") LocalDateTime uploadedAt,
                            @JsonProperty("rejectionReason") String rejectionReason) {
        this.id = id;
        this.fileName = fileName;
        this.docType = docType;
        this.caseReference = caseReference;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
        this.rejectionReason = rejectionReason;
    }

    public String getId() { return id; }
    public String getFileName() { return fileName; }
    public String getDocType() { return docType; }
    public String getCaseReference() { return caseReference; }
    public String getUploadedBy() { return uploadedBy; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public String getRejectionReason() { return rejectionReason; }
}
