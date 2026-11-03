package com.sentinelcv.entity;

import com.sentinelcv.enums.AssessmentDecision;
import com.sentinelcv.enums.AuditStatus;
import java.time.LocalDateTime;

/**
 * Represents an audit performed on a model.
 */
public class ModelAudit {

    private Long id;
    private Long modelId;
    private AuditStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private int totalFindings;
    private int criticalFindings;
    private int highFindings;
    private int mediumFindings;
    private int lowFindings;
    private int infoFindings;
    private String behavioralFingerprint;
    private AssessmentDecision assessmentDecision;
    private String summary;
    private String errorMessage;
    private LocalDateTime createdAt;

    // Transient
    private String modelName;

    public ModelAudit() {
        this.status = AuditStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public ModelAudit(Long modelId) {
        this();
        this.modelId = modelId;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getModelId() { return modelId; }
    public void setModelId(Long modelId) { this.modelId = modelId; }

    public AuditStatus getStatus() { return status; }
    public void setStatus(AuditStatus status) { this.status = status; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public int getTotalFindings() { return totalFindings; }
    public void setTotalFindings(int totalFindings) { this.totalFindings = totalFindings; }

    public int getCriticalFindings() { return criticalFindings; }
    public void setCriticalFindings(int criticalFindings) { this.criticalFindings = criticalFindings; }

    public int getHighFindings() { return highFindings; }
    public void setHighFindings(int highFindings) { this.highFindings = highFindings; }

    public int getMediumFindings() { return mediumFindings; }
    public void setMediumFindings(int mediumFindings) { this.mediumFindings = mediumFindings; }

    public int getLowFindings() { return lowFindings; }
    public void setLowFindings(int lowFindings) { this.lowFindings = lowFindings; }

    public int getInfoFindings() { return infoFindings; }
    public void setInfoFindings(int infoFindings) { this.infoFindings = infoFindings; }

    public String getBehavioralFingerprint() { return behavioralFingerprint; }
    public void setBehavioralFingerprint(String behavioralFingerprint) { this.behavioralFingerprint = behavioralFingerprint; }

    public AssessmentDecision getAssessmentDecision() { return assessmentDecision; }
    public void setAssessmentDecision(AssessmentDecision assessmentDecision) { this.assessmentDecision = assessmentDecision; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
}
