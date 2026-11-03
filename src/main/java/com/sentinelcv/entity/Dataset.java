package com.sentinelcv.entity;

import com.sentinelcv.enums.DatasetFormat;
import java.time.LocalDateTime;

/**
 * Represents a registered dataset in the system.
 */
public class Dataset {

    private Long id;
    private String name;
    private DatasetFormat format;
    private String path;
    private int totalImages;
    private int totalAnnotations;
    private int totalClasses;
    private long fileSizeBytes;
    private String sha256Hash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Dataset() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Dataset(String name, DatasetFormat format, String path) {
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

    public DatasetFormat getFormat() { return format; }
    public void setFormat(DatasetFormat format) { this.format = format; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public int getTotalImages() { return totalImages; }
    public void setTotalImages(int totalImages) { this.totalImages = totalImages; }

    public int getTotalAnnotations() { return totalAnnotations; }
    public void setTotalAnnotations(int totalAnnotations) { this.totalAnnotations = totalAnnotations; }

    public int getTotalClasses() { return totalClasses; }
    public void setTotalClasses(int totalClasses) { this.totalClasses = totalClasses; }

    public long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public String getSha256Hash() { return sha256Hash; }
    public void setSha256Hash(String sha256Hash) { this.sha256Hash = sha256Hash; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "Dataset{id=" + id + ", name='" + name + "', format=" + format + ", images=" + totalImages + "}";
    }
}
