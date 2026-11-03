package com.sentinelcv.entity;

import com.sentinelcv.enums.CheckExecutionStatus;
import java.time.LocalDateTime;

/**
 * Entity representing an authoritative mandatory check execution status,
 * strictly associated with an audit execution ID, asset ID, and immutable asset content hash.
 */
public class MandatoryCheckRecord {

    private String checkId;
    private Long auditId;
    private String assetId;
    private String assetHash;
    private LocalDateTime executionTimestamp;
    private CheckExecutionStatus executionStatus;
    private String details;

    public MandatoryCheckRecord() {
        this.executionTimestamp = LocalDateTime.now();
        this.executionStatus = CheckExecutionStatus.NOT_EXECUTED;
    }

    public MandatoryCheckRecord(String checkId, Long auditId, String assetId, String assetHash,
                                CheckExecutionStatus executionStatus, String details) {
        this.checkId = checkId;
        this.auditId = auditId;
        this.assetId = assetId;
        this.assetHash = assetHash;
        this.executionTimestamp = LocalDateTime.now();
        this.executionStatus = executionStatus != null ? executionStatus : CheckExecutionStatus.NOT_EXECUTED;
        this.details = details;
    }

    public String getCheckId() { return checkId; }
    public void setCheckId(String checkId) { this.checkId = checkId; }

    public Long getAuditId() { return auditId; }
    public void setAuditId(Long auditId) { this.auditId = auditId; }

    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }

    public String getAssetHash() { return assetHash; }
    public void setAssetHash(String assetHash) { this.assetHash = assetHash; }

    public LocalDateTime getExecutionTimestamp() { return executionTimestamp; }
    public void setExecutionTimestamp(LocalDateTime executionTimestamp) { this.executionTimestamp = executionTimestamp; }

    public CheckExecutionStatus getExecutionStatus() { return executionStatus; }
    public void setExecutionStatus(CheckExecutionStatus executionStatus) { this.executionStatus = executionStatus; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}

