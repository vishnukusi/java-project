package com.sentinelcv.entity;

import com.sentinelcv.enums.ModelFormat;
import java.time.LocalDateTime;

/**
 * Represents a registered ML model in the system.
 */
public class MlModel {

    private Long id;
    private String name;
    private ModelFormat format;
    private String path;
    private long fileSizeBytes;
    private String sha256Hash;
    private String metadataJson;
    private String inputInfo;
    private String outputInfo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public MlModel() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public MlModel(String name, ModelFormat format, String path) {
        this();
        this.name = name;
        this.format = format;
        this.path = path;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ModelFormat getFormat() { return format; }
    public void setFormat(ModelFormat format) { this.format = format; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public String getSha256Hash() { return sha256Hash; }
    public void setSha256Hash(String sha256Hash) { this.sha256Hash = sha256Hash; }

    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }

    public String getInputInfo() { return inputInfo; }
    public void setInputInfo(String inputInfo) { this.inputInfo = inputInfo; }

    public String getOutputInfo() { return outputInfo; }
    public void setOutputInfo(String outputInfo) { this.outputInfo = outputInfo; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "MlModel{id=" + id + ", name='" + name + "', format=" + format + "}";
    }
}
