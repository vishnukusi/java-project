package com.sentinelcv.entity;

import com.sentinelcv.enums.FindingSeverity;
import java.time.LocalDateTime;

/**
 * Represents a single security finding from any audit module.
 * Used for both module-specific and aggregated security findings.
 */
public class SecurityFinding {

    private Long id;
    private String findingId;
    private String module;
    private String category;
    private String description;
    private FindingSeverity severity;
    private double confidence;
    private String evidence;
    private String affectedAsset;
    private String recommendedAction;
    private Long sourceAuditId;
    private String sourceAuditType;
    private LocalDateTime detectedAt;

    public SecurityFinding() {
        this.detectedAt = LocalDateTime.now();
    }

    /**
     * Builder pattern for creating findings fluently.
     */
    public static class Builder {
        private final SecurityFinding finding = new SecurityFinding();

        public Builder findingId(String findingId) { finding.findingId = findingId; return this; }
        public Builder module(String module) { finding.module = module; return this; }
        public Builder category(String category) { finding.category = category; return this; }
        public Builder description(String description) { finding.description = description; return this; }
        public Builder severity(FindingSeverity severity) { finding.severity = severity; return this; }
        public Builder confidence(double confidence) { finding.confidence = confidence; return this; }
        public Builder evidence(String evidence) { finding.evidence = evidence; return this; }
        public Builder affectedAsset(String affectedAsset) { finding.affectedAsset = affectedAsset; return this; }
        public Builder recommendedAction(String action) { finding.recommendedAction = action; return this; }
        public Builder sourceAuditId(Long id) { finding.sourceAuditId = id; return this; }
        public Builder sourceAuditType(String type) { finding.sourceAuditType = type; return this; }

        public SecurityFinding build() {
            if (finding.findingId == null || finding.module == null || finding.severity == null) {
                throw new IllegalStateException("findingId, module, and severity are required");
            }
            return finding;
        }
    }

    public static Builder builder() { return new Builder(); }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFindingId() { return findingId; }
    public void setFindingId(String findingId) { this.findingId = findingId; }

    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public FindingSeverity getSeverity() { return severity; }
    public void setSeverity(FindingSeverity severity) { this.severity = severity; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }

    public String getAffectedAsset() { return affectedAsset; }
    public void setAffectedAsset(String affectedAsset) { this.affectedAsset = affectedAsset; }

    public String getRecommendedAction() { return recommendedAction; }
    public void setRecommendedAction(String recommendedAction) { this.recommendedAction = recommendedAction; }

    public Long getSourceAuditId() { return sourceAuditId; }
    public void setSourceAuditId(Long sourceAuditId) { this.sourceAuditId = sourceAuditId; }

    public String getSourceAuditType() { return sourceAuditType; }
    public void setSourceAuditType(String sourceAuditType) { this.sourceAuditType = sourceAuditType; }

    public LocalDateTime getDetectedAt() { return detectedAt; }
    public void setDetectedAt(LocalDateTime detectedAt) { this.detectedAt = detectedAt; }

    @Override
    public String toString() {
        return "SecurityFinding{id='" + findingId + "', severity=" + severity + ", module='" + module + "'}";
    }
}
