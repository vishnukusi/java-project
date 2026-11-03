package com.sentinelcv.entity;

import java.time.LocalDateTime;

/**
 * Represents a historical audit event for the audit trail.
 */
public class AuditHistory {

    private Long id;
    private String auditType;
    private String targetName;
    private Long targetId;
    private String status;
    private int findingsCount;
    private String assessmentDecision;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String summary;
    private LocalDateTime createdAt;

    public AuditHistory() {
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAuditType() { return auditType; }
    public void setAuditType(String auditType) { this.auditType = auditType; }

    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }

    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getFindingsCount() { return findingsCount; }
    public void setFindingsCount(int findingsCount) { this.findingsCount = findingsCount; }

    public String getAssessmentDecision() { return assessmentDecision; }
    public void setAssessmentDecision(String assessmentDecision) { this.assessmentDecision = assessmentDecision; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
