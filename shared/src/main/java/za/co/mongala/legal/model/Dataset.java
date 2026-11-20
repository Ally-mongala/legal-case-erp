package za.co.mongala.legal.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * A training dataset managed by admins (FR10 / ETR3 - managing training datasets).
 * `storagePath` points at the stored dataset file so admins can upload new
 * datasets and download existing ones.
 */
public class Dataset {
    private final String id;
    private final String name;
    private final long records;
    private final long sizeKb;
    private final String storagePath;
    private final LocalDateTime uploadedAt;

    @JsonCreator
    public Dataset(@JsonProperty("id") String id,
                   @JsonProperty("name") String name,
                   @JsonProperty("records") long records,
                   @JsonProperty("sizeKb") long sizeKb,
                   @JsonProperty("storagePath") String storagePath,
                   @JsonProperty("uploadedAt") LocalDateTime uploadedAt) {
        this.id = id;
        this.name = name;
        this.records = records;
        this.sizeKb = sizeKb;
        this.storagePath = storagePath;
        this.uploadedAt = uploadedAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public long getRecords() { return records; }
    public long getSizeKb() { return sizeKb; }
    public String getStoragePath() { return storagePath; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
}
