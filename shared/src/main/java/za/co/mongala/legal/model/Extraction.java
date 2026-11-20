package za.co.mongala.legal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One AI-extracted field (FR2 / ETR5). `confidence` is the model's own score,
 * `spanText` is the exact sentence fragment the model used (explainability, ETR2),
 * and `pageNumber` points back to the source page. The owning document id is
 * assigned by the backend when the extraction is persisted.
 */
public class Extraction {
    private String documentId;
    private final String fieldName;
    private final String fieldValue;
    private final double confidence;      // 0..1
    private final String spanText;
    private final int pageNumber;

    @JsonCreator
    public Extraction(@JsonProperty("fieldName") String fieldName,
                      @JsonProperty("fieldValue") String fieldValue,
                      @JsonProperty("confidence") double confidence,
                      @JsonProperty("spanText") String spanText,
                      @JsonProperty("pageNumber") int pageNumber) {
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
        this.confidence = confidence;
        this.spanText = spanText;
        this.pageNumber = pageNumber;
    }

    @JsonIgnore
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getFieldName() { return fieldName; }
    public String getFieldValue() { return fieldValue; }
    public double getConfidence() { return confidence; }
    public String getSpanText() { return spanText; }
    public int getPageNumber() { return pageNumber; }

    public String getConfidencePercent() { return String.format("%.0f%%", confidence * 100); }
}
