package com.sentinelcv.inference;

import com.sentinelcv.entity.InferenceRecord;
import com.sentinelcv.entity.ProvenanceVerification;
import com.sentinelcv.enums.VerificationStatus;
import com.sentinelcv.util.CryptoUtil;
import com.sentinelcv.util.HashUtil;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates cryptographic hashes, hash-chain integrity, nonce uniqueness,
 * monotonic sequence ordering, and signature verification on inference records.
 */
@Component
public class InferenceValidator {

    public enum FailureCategory {
        BROKEN_CHAIN,
        HASH_MISMATCH,
        HMAC_MISMATCH,
        REPLAY_DETECTED,
        INVALID_SEQUENCE,
        INVALID_TIMESTAMP,
        MISSING_KEY,
        UNSUPPORTED_SCHEMA,
        DUPLICATE_RECORD_ID,
        MALFORMED_RECORD
    }

    public static class VerificationBatchResult {
        public int totalRecordsChecked = 0;
        public int verifiedCount = 0;
        public int failedCount = 0;
        public int unavailableCount = 0;
        public int brokenChainLinks = 0;
        public int replayAttempts = 0;
        public int cryptographicFailures = 0;
        public String assessmentDecision;
        public List<ProvenanceVerification> verifications = new ArrayList<>();
        public List<String> anomalyDescriptions = new ArrayList<>();
        public Set<FailureCategory> failureCategories = new HashSet<>();

        public boolean hasCategory(FailureCategory cat) {
            return failureCategories != null && failureCategories.contains(cat);
        }

        public boolean hasCryptographicTampering() {
            return hasCategory(FailureCategory.BROKEN_CHAIN)
                    || hasCategory(FailureCategory.HASH_MISMATCH)
                    || hasCategory(FailureCategory.HMAC_MISMATCH)
                    || cryptographicFailures > 0;
        }
    }

    /**
     * Verifies an ordered sequence of inference records.
     * Computes deterministic record hashes and verifies blockchain-style hash links.
     */
    public VerificationBatchResult verifyRecords(List<InferenceRecord> records, String hmacSecretKey) {
        VerificationBatchResult result = new VerificationBatchResult();
        if (records == null || records.isEmpty()) {
            result.assessmentDecision = "UNAVAILABLE";
            return result;
        }

        // Handle null elements in submission batch safely
        List<InferenceRecord> nonNullRecords = new ArrayList<>();
        int nullRecordCount = 0;
        for (InferenceRecord r : records) {
            if (r == null) {
                nullRecordCount++;
            } else {
                nonNullRecords.add(r);
            }
        }

        if (nullRecordCount > 0) {
            for (int i = 0; i < nullRecordCount; i++) {
                result.totalRecordsChecked++;
                result.failedCount++;
                ProvenanceVerification v = new ProvenanceVerification("MALFORMED_NULL_RECORD");
                v.setOverallStatus(VerificationStatus.FAILED);
                v.setDetails("Null record entry in submission batch");
                v.setVerifiedAt(LocalDateTime.now());
                result.verifications.add(v);
            }
            result.anomalyDescriptions.add("Encountered " + nullRecordCount + " null record entries in batch");
            result.failureCategories.add(FailureCategory.MALFORMED_RECORD);
        }

        // Ensure deterministic sequence processing on valid records
        List<InferenceRecord> sortedRecords = new ArrayList<>(nonNullRecords);
        sortedRecords.sort(Comparator.comparing(
                r -> r.getSequenceNumber() != null ? r.getSequenceNumber() : Integer.MAX_VALUE
        ));

        Set<String> observedRecordIds = new HashSet<>();
        Set<String> observedNonces = new HashSet<>();
        Set<Integer> observedSequences = new HashSet<>();
        String expectedPreviousHash = "GENESIS";
        int expectedSequence = 1;
        LocalDateTime lastTimestamp = null;

        for (InferenceRecord record : sortedRecords) {
            result.totalRecordsChecked++;
            ProvenanceVerification v = new ProvenanceVerification(record.getRecordId());
            List<String> recordDetails = new ArrayList<>();
            boolean recordPassed = true;
            boolean cryptoTampered = false;

            // 0. Validate Record ID and Schema Version
            if (record.getRecordId() == null || record.getRecordId().isBlank()) {
                recordDetails.add("Missing record ID");
                recordPassed = false;
                result.failureCategories.add(FailureCategory.MALFORMED_RECORD);
            } else if (observedRecordIds.contains(record.getRecordId())) {
                recordDetails.add("Duplicate record ID detected: " + record.getRecordId());
                recordPassed = false;
                result.failureCategories.add(FailureCategory.DUPLICATE_RECORD_ID);
            } else {
                observedRecordIds.add(record.getRecordId());
            }

            if (record.getSchemaVersion() != null && !SCHEMA_VERSION.equalsIgnoreCase(record.getSchemaVersion())) {
                recordDetails.add("Unsupported schema version: " + record.getSchemaVersion() + " (expected " + SCHEMA_VERSION + ")");
                recordPassed = false;
                result.failureCategories.add(FailureCategory.UNSUPPORTED_SCHEMA);
            }

            // 1. Validate Nonce (Replay Detection)
            if (record.getNonce() == null || record.getNonce().isBlank()) {
                v.setNonceStatus(VerificationStatus.FAILED);
                recordDetails.add("Missing nonce");
                recordPassed = false;
                result.failureCategories.add(FailureCategory.REPLAY_DETECTED);
            } else if (observedNonces.contains(record.getNonce())) {
                v.setNonceStatus(VerificationStatus.FAILED);
                result.replayAttempts++;
                recordDetails.add("Duplicate nonce detected (replay attack indicator): " + record.getNonce());
                recordPassed = false;
                result.failureCategories.add(FailureCategory.REPLAY_DETECTED);
            } else {
                observedNonces.add(record.getNonce());
                v.setNonceStatus(VerificationStatus.VERIFIED);
            }

            // 2. Validate Sequence Monotonicity and starting index (must start at 1)
            if (record.getSequenceNumber() == null) {
                v.setSequenceStatus(VerificationStatus.FAILED);
                recordDetails.add("Missing sequence number");
                recordPassed = false;
                result.failureCategories.add(FailureCategory.INVALID_SEQUENCE);
            } else if (observedSequences.contains(record.getSequenceNumber())) {
                v.setSequenceStatus(VerificationStatus.FAILED);
                result.replayAttempts++;
                recordDetails.add("Duplicate sequence number detected: " + record.getSequenceNumber());
                recordPassed = false;
                result.failureCategories.add(FailureCategory.INVALID_SEQUENCE);
                result.failureCategories.add(FailureCategory.REPLAY_DETECTED);
            } else if (record.getSequenceNumber() != expectedSequence) {
                v.setSequenceStatus(VerificationStatus.FAILED);
                result.replayAttempts++;
                recordDetails.add(String.format("Sequence discontinuity: expected %d, got %d",
                        expectedSequence, record.getSequenceNumber()));
                recordPassed = false;
                result.failureCategories.add(FailureCategory.INVALID_SEQUENCE);
                result.failureCategories.add(FailureCategory.REPLAY_DETECTED);
            } else {
                observedSequences.add(record.getSequenceNumber());
                v.setSequenceStatus(VerificationStatus.VERIFIED);
                expectedSequence++;
            }

            // 3. Validate Timestamp Monotonicity and Format
            if (record.getTimestamp() != null && !record.getTimestamp().isBlank()) {
                try {
                    LocalDateTime ts = LocalDateTime.parse(record.getTimestamp().replace(" ", "T"));
                    if (lastTimestamp != null && ts.isBefore(lastTimestamp)) {
                        v.setTimestampStatus(VerificationStatus.FAILED);
                        recordDetails.add("Retroactive timestamp: " + record.getTimestamp() + " is before " + lastTimestamp);
                        recordPassed = false;
                        result.failureCategories.add(FailureCategory.INVALID_TIMESTAMP);
                    } else {
                        v.setTimestampStatus(VerificationStatus.VERIFIED);
                        lastTimestamp = ts;
                    }
                } catch (DateTimeParseException e) {
                    v.setTimestampStatus(VerificationStatus.FAILED);
                    recordDetails.add("Malformed timestamp: " + record.getTimestamp());
                    recordPassed = false;
                    result.failureCategories.add(FailureCategory.INVALID_TIMESTAMP);
                }
            } else {
                v.setTimestampStatus(VerificationStatus.FAILED);
                recordDetails.add("Missing timestamp");
                recordPassed = false;
                result.failureCategories.add(FailureCategory.INVALID_TIMESTAMP);
            }

            // 4. Validate Individual Field Hashes (SHA-256 standard)
            boolean inputHashValid = validateHashFormat(record.getInputImageHash());
            boolean modelHashValid = validateHashFormat(record.getModelHash());
            boolean configHashValid = validateHashFormat(record.getConfigHash());
            boolean outputHashValid = validateHashFormat(record.getOutputHash());

            v.setInputHashStatus(inputHashValid ? VerificationStatus.VERIFIED : VerificationStatus.FAILED);
            v.setModelHashStatus(modelHashValid ? VerificationStatus.VERIFIED : VerificationStatus.FAILED);
            v.setConfigHashStatus(configHashValid ? VerificationStatus.VERIFIED : VerificationStatus.FAILED);
            v.setOutputHashStatus(outputHashValid ? VerificationStatus.VERIFIED : VerificationStatus.FAILED);

            if (!inputHashValid) {
                recordDetails.add("Invalid/missing inputImageHash");
                recordPassed = false;
                cryptoTampered = true;
                result.failureCategories.add(FailureCategory.HASH_MISMATCH);
            }
            if (!modelHashValid) {
                recordDetails.add("Invalid/missing modelHash");
                recordPassed = false;
                cryptoTampered = true;
                result.failureCategories.add(FailureCategory.HASH_MISMATCH);
            }
            if (!configHashValid) {
                recordDetails.add("Invalid/missing configHash");
                recordPassed = false;
                cryptoTampered = true;
                result.failureCategories.add(FailureCategory.HASH_MISMATCH);
            }
            if (!outputHashValid) {
                recordDetails.add("Invalid/missing outputHash");
                recordPassed = false;
                cryptoTampered = true;
                result.failureCategories.add(FailureCategory.HASH_MISMATCH);
            }

            // 5. Validate Hash-Linked Chain (First record MUST have previousRecordHash = GENESIS)
            String prevHashInRecord = record.getPreviousRecordHash() != null ? record.getPreviousRecordHash() : "";
            if (!prevHashInRecord.equalsIgnoreCase(expectedPreviousHash)) {
                v.setChainLinkStatus(VerificationStatus.FAILED);
                result.brokenChainLinks++;
                recordDetails.add(String.format("Broken hash chain: expected prev %s, got %s",
                        expectedPreviousHash.substring(0, Math.min(10, expectedPreviousHash.length())) + "...",
                        prevHashInRecord.substring(0, Math.min(10, prevHashInRecord.length())) + "..."));
                recordPassed = false;
                cryptoTampered = true;
                result.failureCategories.add(FailureCategory.BROKEN_CHAIN);
            } else {
                v.setChainLinkStatus(VerificationStatus.VERIFIED);
            }

            // 6. Recompute and Verify Current Record Hash against canonical payload
            String payloadToHash = computeCanonicalPayload(record);
            String recomputedHash = HashUtil.sha256(payloadToHash);

            if (record.getCurrentRecordHash() == null || !record.getCurrentRecordHash().equalsIgnoreCase(recomputedHash)) {
                recordDetails.add("Current record hash mismatch (content tampering detected)");
                recordPassed = false;
                cryptoTampered = true;
                result.failureCategories.add(FailureCategory.HASH_MISMATCH);
            }

            // Update expected previous hash for next record in chain
            expectedPreviousHash = recomputedHash;

            // 7. HMAC / Digital Signature Verification
            boolean signatureUnavailable = false;
            if (hmacSecretKey == null || hmacSecretKey.isBlank()) {
                v.setSignatureStatus(VerificationStatus.UNAVAILABLE);
                signatureUnavailable = true;
                recordDetails.add("HMAC verification key unavailable");
                result.failureCategories.add(FailureCategory.MISSING_KEY);
            } else if (record.getHmacValue() == null || record.getHmacValue().isBlank()) {
                v.setSignatureStatus(VerificationStatus.UNAVAILABLE);
                signatureUnavailable = true;
                recordDetails.add("Missing HMAC signature value");
                result.failureCategories.add(FailureCategory.MISSING_KEY);
            } else {
                boolean hmacValid = CryptoUtil.verifyHmac(payloadToHash, hmacSecretKey, record.getHmacValue());
                if (hmacValid) {
                    v.setSignatureStatus(VerificationStatus.VERIFIED);
                } else {
                    v.setSignatureStatus(VerificationStatus.FAILED);
                    recordDetails.add("HMAC-SHA256 signature mismatch (unauthorized entity or key mismatch)");
                    recordPassed = false;
                    cryptoTampered = true;
                    result.failureCategories.add(FailureCategory.HMAC_MISMATCH);
                }
            }

            // 8. Overall Status Determination (Cannot contradict sub-checks)
            if (!recordPassed) {
                v.setOverallStatus(VerificationStatus.FAILED);
                result.failedCount++;
                if (cryptoTampered) {
                    result.cryptographicFailures++;
                }
                v.setDetails(String.join("; ", recordDetails));
                result.anomalyDescriptions.add(String.format("Record %s: %s", record.getRecordId(), v.getDetails()));
            } else if (signatureUnavailable) {
                v.setOverallStatus(VerificationStatus.UNAVAILABLE);
                result.unavailableCount++;
                v.setDetails("All hashes and sequence verified; signature verification UNAVAILABLE.");
            } else {
                v.setOverallStatus(VerificationStatus.VERIFIED);
                result.verifiedCount++;
                v.setDetails("All provenance and cryptographic checks verified.");
            }

            v.setVerifiedAt(LocalDateTime.now());
            result.verifications.add(v);
        }

        return result;
    }

    public static final String SCHEMA_VERSION = "V2";

    /**
     * Constructs canonical payload byte array for deterministic hashing and HMAC verification.
     */
    public byte[] computeCanonicalPayloadBytes(InferenceRecord r) {
        return computeCanonicalPayload(r).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Constructs unambiguous length-prefixed canonical payload string:
     * V2|len(recordId):recordId;len(sequenceNumber):sequenceNumber;len(timestamp):timestamp;
     * len(modelIdentifier):modelIdentifier;len(modelHash):modelHash;len(inputImageHash):inputImageHash;
     * len(configHash):configHash;len(outputHash):outputHash;len(nonce):nonce;len(previousRecordHash):previousRecordHash;
     */
    public String computeCanonicalPayload(InferenceRecord r) {
        if (r == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(SCHEMA_VERSION).append("|");
        appendLengthPrefixedField(sb, r.getRecordId());
        appendLengthPrefixedField(sb, r.getSequenceNumber() != null ? String.valueOf(r.getSequenceNumber()) : "");
        appendLengthPrefixedField(sb, r.getTimestamp());
        appendLengthPrefixedField(sb, r.getModelIdentifier());
        appendLengthPrefixedField(sb, r.getModelHash());
        appendLengthPrefixedField(sb, r.getInputImageHash());
        appendLengthPrefixedField(sb, r.getConfigHash());
        appendLengthPrefixedField(sb, r.getOutputHash());
        appendLengthPrefixedField(sb, r.getNonce());
        appendLengthPrefixedField(sb, r.getPreviousRecordHash());
        return sb.toString();
    }

    private static void appendLengthPrefixedField(StringBuilder sb, String val) {
        String s = (val != null) ? val : "";
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        sb.append(bytes.length).append(":").append(s).append(";");
    }

    public boolean validateHashFormat(String hash) {
        return hash != null && hash.matches("^[a-fA-F0-9]{64}$");
    }
}
