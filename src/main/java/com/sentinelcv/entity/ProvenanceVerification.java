package com.sentinelcv.entity;

import com.sentinelcv.enums.VerificationStatus;
import java.time.LocalDateTime;

/**
 * Represents the verification outcome of an inference record.
 */
public class ProvenanceVerification {

    private Long id;
    private String recordId;
    private VerificationStatus inputHashStatus;
    private VerificationStatus modelHashStatus;
    private VerificationStatus configHashStatus;
    private VerificationStatus outputHashStatus;
    private VerificationStatus chainLinkStatus;
    private VerificationStatus nonceStatus;
    private VerificationStatus sequenceStatus;
    private VerificationStatus signatureStatus;
    private VerificationStatus timestampStatus;
    private VerificationStatus overallStatus;
    private String details;
    private LocalDateTime verifiedAt;

    public ProvenanceVerification() {
        this.inputHashStatus = VerificationStatus.PENDING;
        this.modelHashStatus = VerificationStatus.PENDING;
        this.configHashStatus = VerificationStatus.PENDING;
        this.outputHashStatus = VerificationStatus.PENDING;
        this.chainLinkStatus = VerificationStatus.PENDING;
        this.nonceStatus = VerificationStatus.PENDING;
        this.sequenceStatus = VerificationStatus.PENDING;
        this.signatureStatus = VerificationStatus.PENDING;
        this.timestampStatus = VerificationStatus.PENDING;
        this.overallStatus = VerificationStatus.PENDING;
        this.verifiedAt = LocalDateTime.now();
    }

    public ProvenanceVerification(String recordId) {
        this();
        this.recordId = recordId;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }

    public VerificationStatus getInputHashStatus() { return inputHashStatus; }
    public void setInputHashStatus(VerificationStatus inputHashStatus) { this.inputHashStatus = inputHashStatus; }

    public VerificationStatus getModelHashStatus() { return modelHashStatus; }
    public void setModelHashStatus(VerificationStatus modelHashStatus) { this.modelHashStatus = modelHashStatus; }

    public VerificationStatus getConfigHashStatus() { return configHashStatus; }
    public void setConfigHashStatus(VerificationStatus configHashStatus) { this.configHashStatus = configHashStatus; }

    public VerificationStatus getOutputHashStatus() { return outputHashStatus; }
    public void setOutputHashStatus(VerificationStatus outputHashStatus) { this.outputHashStatus = outputHashStatus; }

    public VerificationStatus getChainLinkStatus() { return chainLinkStatus; }
    public void setChainLinkStatus(VerificationStatus chainLinkStatus) { this.chainLinkStatus = chainLinkStatus; }

    public VerificationStatus getNonceStatus() { return nonceStatus; }
    public void setNonceStatus(VerificationStatus nonceStatus) { this.nonceStatus = nonceStatus; }

    public VerificationStatus getSequenceStatus() { return sequenceStatus; }
    public void setSequenceStatus(VerificationStatus sequenceStatus) { this.sequenceStatus = sequenceStatus; }

    public VerificationStatus getSignatureStatus() { return signatureStatus; }
    public void setSignatureStatus(VerificationStatus signatureStatus) { this.signatureStatus = signatureStatus; }

    public VerificationStatus getTimestampStatus() { return timestampStatus; }
    public void setTimestampStatus(VerificationStatus timestampStatus) { this.timestampStatus = timestampStatus; }

    public VerificationStatus getOverallStatus() { return overallStatus; }
    public void setOverallStatus(VerificationStatus overallStatus) { this.overallStatus = overallStatus; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }
}
