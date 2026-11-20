package za.co.mongala.legal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * A three-paragraph AI summary (FR3). `status` implements the human-in-the-loop
 * workflow (FR6 / ETR3): the lawyer edits the draft and only an APPROVED summary
 * is published to the client portal. `version` supports model/summary versioning (ETR4).
 */
public class Summary {
    public enum SummaryStatus { DRAFT, APPROVED }

    private final String id;
    private final String documentId;
    private final String caseId;
    private String paragraph1;
    private String paragraph2;
    private String paragraph3;
    private int version;
    private SummaryStatus status;
    private final String createdBy;
    private final LocalDateTime createdAt;
    private LocalDateTime approvedAt;

    @JsonCreator
    public Summary(@JsonProperty("id") String id,
                   @JsonProperty("documentId") String documentId,
                   @JsonProperty("caseId") String caseId,
                   @JsonProperty("paragraph1") String paragraph1,
                   @JsonProperty("paragraph2") String paragraph2,
                   @JsonProperty("paragraph3") String paragraph3,
                   @JsonProperty("version") int version,
                   @JsonProperty("status") SummaryStatus status,
                   @JsonProperty("createdBy") String createdBy,
                   @JsonProperty("createdAt") LocalDateTime createdAt,
                   @JsonProperty("approvedAt") LocalDateTime approvedAt) {
        this.id = id;
        this.documentId = documentId;
        this.caseId = caseId;
        this.paragraph1 = paragraph1;
        this.paragraph2 = paragraph2;
        this.paragraph3 = paragraph3;
        this.version = version;
        this.status = status;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.approvedAt = approvedAt;
    }

    public String getId() { return id; }
    public String getDocumentId() { return documentId; }
    public String getCaseId() { return caseId; }
    public String getParagraph1() { return paragraph1; }
    public String getParagraph2() { return paragraph2; }
    public String getParagraph3() { return paragraph3; }
    public void setParagraph1(String p) { this.paragraph1 = p; }
    public void setParagraph2(String p) { this.paragraph2 = p; }
    public void setParagraph3(String p) { this.paragraph3 = p; }
    public int getVersion() { return version; }
    public void bumpVersion() { version++; }
    public SummaryStatus getStatus() { return status; }
    public void approve(LocalDateTime at) { this.status = SummaryStatus.APPROVED; this.approvedAt = at; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public String getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
