package za.co.mongala.legal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * A legal document (PDF or Word) stored against a case (FR1). Mirrors the
 * `documents` table. `docType` records PDF/WORD, `consentGranted` records the
 * POPIA client-consent control (FR11).
 */
public class LegalDocument {
    public enum ProcessingStatus { PROCESSING, PROCESSED, FAILED, REJECTED }

    private final String id;
    private final String caseId;
    private final String fileName;
    private final String storagePath;   // encrypted at rest in production (FR11)
    private final String uploadedBy;
    private final LocalDateTime uploadedAt;
    private final long sizeKb;
    private ProcessingStatus processingStatus;
    private final String docType;              // "PDF" | "WORD"
    private final boolean consentGranted;      // POPIA client consent (FR11)
    private final String rejectionReason;      // set when the document is BLOCKED (irrelevant/incorrect)

    @JsonCreator
    public LegalDocument(@JsonProperty("id") String id,
                         @JsonProperty("caseId") String caseId,
                         @JsonProperty("fileName") String fileName,
                         @JsonProperty("storagePath") String storagePath,
                         @JsonProperty("uploadedBy") String uploadedBy,
                         @JsonProperty("uploadedAt") LocalDateTime uploadedAt,
                         @JsonProperty("sizeKb") long sizeKb,
                         @JsonProperty("processingStatus") ProcessingStatus processingStatus,
                         @JsonProperty("docType") String docType,
                         @JsonProperty("consentGranted") boolean consentGranted,
                         @JsonProperty("rejectionReason") String rejectionReason) {
        this.id = id;
        this.caseId = caseId;
        this.fileName = fileName;
        this.storagePath = storagePath;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
        this.sizeKb = sizeKb;
        this.processingStatus = processingStatus;
        this.docType = docType == null ? "PDF" : docType;
        this.consentGranted = consentGranted;
        this.rejectionReason = rejectionReason;
    }

    public LegalDocument(String id, String caseId, String fileName, String storagePath,
                         String uploadedBy, LocalDateTime uploadedAt, long sizeKb,
                         ProcessingStatus processingStatus) {
        this(id, caseId, fileName, storagePath, uploadedBy, uploadedAt, sizeKb, processingStatus,
                fileName.toLowerCase().endsWith(".doc") || fileName.toLowerCase().endsWith(".docx") ? "WORD" : "PDF",
                true, null);
    }

    public LegalDocument(String id, String caseId, String fileName, String storagePath,
                         String uploadedBy, LocalDateTime uploadedAt, long sizeKb,
                         ProcessingStatus processingStatus, String docType, boolean consentGranted) {
        this(id, caseId, fileName, storagePath, uploadedBy, uploadedAt, sizeKb, processingStatus,
                docType, consentGranted, null);
    }

    public String getId() { return id; }
    public String getCaseId() { return caseId; }
    public String getFileName() { return fileName; }
    public String getStoragePath() { return storagePath; }
    public String getUploadedBy() { return uploadedBy; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public long getSizeKb() { return sizeKb; }
    public ProcessingStatus getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(ProcessingStatus s) { this.processingStatus = s; }
    public String getDocType() { return docType; }
    public boolean isConsentGranted() { return consentGranted; }
    public String getRejectionReason() { return rejectionReason; }
}
