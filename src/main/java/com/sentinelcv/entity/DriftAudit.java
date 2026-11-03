package com.sentinelcv.entity;

import java.time.LocalDateTime;

/**
 * Represents a drift analysis audit comparing two datasets.
 */
public class DriftAudit {

    private Long id;
    private Long referenceDatasetId;
    private Long evaluationDatasetId;
    private String status;
    private boolean driftDetected;
    private double overallDriftScore;
    private String severity;
    private String metricsJson;
    private String summary;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;

    // Transient
    private String referenceDatasetName;
    private String evaluationDatasetName;

    public DriftAudit() {
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getReferenceDatasetId() { return referenceDatasetId; }
    public void setReferenceDatasetId(Long referenceDatasetId) { this.referenceDatasetId = referenceDatasetId; }

    public Long getEvaluationDatasetId() { return evaluationDatasetId; }
    public void setEvaluationDatasetId(Long evaluationDatasetId) { this.evaluationDatasetId = evaluationDatasetId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isDriftDetected() { return driftDetected; }
    public void setDriftDetected(boolean driftDetected) { this.driftDetected = driftDetected; }

    public double getOverallDriftScore() { return overallDriftScore; }
    public void setOverallDriftScore(double overallDriftScore) { this.overallDriftScore = overallDriftScore; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getMetricsJson() { return metricsJson; }
    public void setMetricsJson(String metricsJson) { this.metricsJson = metricsJson; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getReferenceDatasetName() { return referenceDatasetName; }
    public void setReferenceDatasetName(String referenceDatasetName) { this.referenceDatasetName = referenceDatasetName; }

    public String getEvaluationDatasetName() { return evaluationDatasetName; }
    public void setEvaluationDatasetName(String evaluationDatasetName) { this.evaluationDatasetName = evaluationDatasetName; }
}
