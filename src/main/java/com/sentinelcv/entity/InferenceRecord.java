package com.sentinelcv.entity;

import java.time.LocalDateTime;

/**
 * Represents a single inference record for provenance verification.
 */
public class InferenceRecord {

    private Long id;
    private String recordId;
    private String timestamp;
    private String modelIdentifier;
    private String modelHash;
    private String inputImageHash;
    private String configHash;
    private String outputHash;
    private String nonce;
    private Integer sequenceNumber;
    private String previousRecordHash;
    private String currentRecordHash;
    private String signature;
    private String hmacValue;
    private String rawOutput;
    private String schemaVersion = "V2";
    private LocalDateTime createdAt;

    public InferenceRecord() {
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getModelIdentifier() { return modelIdentifier; }
    public void setModelIdentifier(String modelIdentifier) { this.modelIdentifier = modelIdentifier; }

    public String getModelHash() { return modelHash; }
    public void setModelHash(String modelHash) { this.modelHash = modelHash; }

    public String getInputImageHash() { return inputImageHash; }
    public void setInputImageHash(String inputImageHash) { this.inputImageHash = inputImageHash; }

    public String getConfigHash() { return configHash; }
    public void setConfigHash(String configHash) { this.configHash = configHash; }

    public String getOutputHash() { return outputHash; }
    public void setOutputHash(String outputHash) { this.outputHash = outputHash; }

    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }

    public Integer getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(Integer sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    public String getPreviousRecordHash() { return previousRecordHash; }
    public void setPreviousRecordHash(String previousRecordHash) { this.previousRecordHash = previousRecordHash; }

    public String getCurrentRecordHash() { return currentRecordHash; }
    public void setCurrentRecordHash(String currentRecordHash) { this.currentRecordHash = currentRecordHash; }

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }

    public String getHmacValue() { return hmacValue; }
    public void setHmacValue(String hmacValue) { this.hmacValue = hmacValue; }

    public String getRawOutput() { return rawOutput; }
    public void setRawOutput(String rawOutput) { this.rawOutput = rawOutput; }

    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
