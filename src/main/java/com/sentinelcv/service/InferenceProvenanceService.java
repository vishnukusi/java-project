package com.sentinelcv.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelcv.entity.AuditHistory;
import com.sentinelcv.entity.InferenceRecord;
import com.sentinelcv.entity.ProvenanceVerification;
import com.sentinelcv.entity.SecurityFinding;
import com.sentinelcv.enums.CheckExecutionStatus;
import com.sentinelcv.enums.FindingSeverity;
import com.sentinelcv.exception.ProvenanceException;
import com.sentinelcv.inference.InferenceValidator;
import com.sentinelcv.provenance.ProvenanceKeyManager;
import com.sentinelcv.repository.AuditHistoryRepository;
import com.sentinelcv.repository.InferenceRecordRepository;
import com.sentinelcv.repository.ProvenanceVerificationRepository;
import com.sentinelcv.repository.SecurityFindingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service managing inference provenance records, hash-linked blockchain validation,
 * replay detection, and security findings generation.
 */
@Service
public class InferenceProvenanceService {

    private static final Logger logger = LoggerFactory.getLogger(InferenceProvenanceService.class);

    private final InferenceRecordRepository recordRepository;
    private final ProvenanceVerificationRepository verificationRepository;
    private final SecurityFindingRepository securityFindingRepository;
    private final AuditHistoryRepository auditHistoryRepository;
    private final InferenceValidator inferenceValidator;
    private final ProvenanceKeyManager provenanceKeyManager;
    private final RiskAssessmentService riskAssessmentService;
    private final ObjectMapper objectMapper;

    @Autowired
    public InferenceProvenanceService(InferenceRecordRepository recordRepository,
                                      ProvenanceVerificationRepository verificationRepository,
                                      SecurityFindingRepository securityFindingRepository,
                                      AuditHistoryRepository auditHistoryRepository,
                                      InferenceValidator inferenceValidator,
                                      ProvenanceKeyManager provenanceKeyManager,
                                      RiskAssessmentService riskAssessmentService,
                                      ObjectMapper objectMapper) {
        this.recordRepository = recordRepository;
        this.verificationRepository = verificationRepository;
        this.securityFindingRepository = securityFindingRepository;
        this.auditHistoryRepository = auditHistoryRepository;
        this.inferenceValidator = inferenceValidator;
        this.provenanceKeyManager = provenanceKeyManager;
        this.riskAssessmentService = riskAssessmentService;
        this.objectMapper = objectMapper;
    }

    public InferenceProvenanceService(InferenceRecordRepository recordRepository,
                                      ProvenanceVerificationRepository verificationRepository,
                                      SecurityFindingRepository securityFindingRepository,
                                      AuditHistoryRepository auditHistoryRepository,
                                      InferenceValidator inferenceValidator,
                                      ProvenanceKeyManager provenanceKeyManager,
                                      ObjectMapper objectMapper) {
        this(recordRepository, verificationRepository, securityFindingRepository,
             auditHistoryRepository, inferenceValidator, provenanceKeyManager, null, objectMapper);
    }

    /**
     * Imports inference records from a JSON string and verifies the entire chain.
     */
    public InferenceValidator.VerificationBatchResult importAndVerify(String jsonContent) {
        if (jsonContent == null || jsonContent.trim().isEmpty() || "null".equalsIgnoreCase(jsonContent.trim())) {
            return processAndVerifyRecords(java.util.Collections.emptyList());
        }
        try {
            List<InferenceRecord> records = objectMapper.readValue(jsonContent, new TypeReference<List<InferenceRecord>>() {});
            if (records == null) {
                return processAndVerifyRecords(java.util.Collections.emptyList());
            }
            return processAndVerifyRecords(records);
        } catch (Exception e) {
            throw new ProvenanceException("Failed to parse inference records JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Verifies all existing inference records in sequence.
     */
    public InferenceValidator.VerificationBatchResult verifyAllRecords() {
        List<InferenceRecord> records = recordRepository.findAllOrderedBySequence();
        return processAndVerifyRecords(records);
    }

    public InferenceValidator.VerificationBatchResult processAndVerifyRecords(List<InferenceRecord> records) {
        if (records == null || records.isEmpty()) {
            InferenceValidator.VerificationBatchResult batchResult = new InferenceValidator.VerificationBatchResult();
            batchResult.assessmentDecision = "UNAVAILABLE";
            if (riskAssessmentService != null) {
                riskAssessmentService.recordCheckStatus("provenance.verification", null, "provenance-chain", null, CheckExecutionStatus.UNAVAILABLE, "No records provided");
            }
            return batchResult;
        }

        // Save records if new (skip null elements)
        for (InferenceRecord r : records) {
            if (r != null && r.getId() == null && r.getRecordId() != null && recordRepository.findByRecordId(r.getRecordId()).isEmpty()) {
                recordRepository.save(r);
            }
        }

        // Retrieve HMAC verification key if available (no hardcoded secret)
        Optional<String> hmacKeyOpt = provenanceKeyManager.getVerificationKey();
        String hmacKey = hmacKeyOpt.orElse(null);

        // Verify batch deterministically
        InferenceValidator.VerificationBatchResult batchResult =
                inferenceValidator.verifyRecords(records, hmacKey);

        // Persist verification results
        for (ProvenanceVerification pv : batchResult.verifications) {
            verificationRepository.save(pv);
        }

        // Generate findings
        List<SecurityFinding> findings = new ArrayList<>();
        int findingCounter = 1;

        if (batchResult.brokenChainLinks > 0) {
            findings.add(SecurityFinding.builder()
                    .findingId("FIND-PROV-BROKEN-CHAIN-" + findingCounter++)
                    .module("INFERENCE")
                    .category("Provenance Chain Integrity")
                    .description(String.format("Detected %d broken hash links in the inference record chain (evidence of log tampering or record deletion).",
                            batchResult.brokenChainLinks))
                    .severity(FindingSeverity.CRITICAL)
                    .confidence(1.0)
                    .evidence(String.join("; ", batchResult.anomalyDescriptions))
                    .affectedAsset("Inference Provenance Chain")
                    .recommendedAction("Quarantine downstream decisions. Trace log storage to identify unauthorized modifications.")
                    .sourceAuditType("INFERENCE")
                    .build());
        }

        if (batchResult.replayAttempts > 0) {
            findings.add(SecurityFinding.builder()
                    .findingId("FIND-PROV-REPLAY-" + findingCounter++)
                    .module("INFERENCE")
                    .category("Replay Detection")
                    .description(String.format("Detected %d replay indicators (duplicate nonces or sequence discontinuities).",
                            batchResult.replayAttempts))
                    .severity(FindingSeverity.HIGH)
                    .confidence(0.95)
                    .evidence("Replay anomalies detected in batch verification.")
                    .affectedAsset("Inference Records")
                    .recommendedAction("Reject replayed inference submissions. Invalidate affected nonces.")
                    .sourceAuditType("INFERENCE")
                    .build());
        }

        int tamperCount = batchResult.cryptographicFailures > 0
                ? batchResult.cryptographicFailures
                : Math.max(0, batchResult.failedCount - batchResult.replayAttempts);

        if (tamperCount > 0) {
            findings.add(SecurityFinding.builder()
                    .findingId("FIND-PROV-TAMPER-" + findingCounter++)
                    .module("INFERENCE")
                    .category("Data Integrity Violation")
                    .description(String.format("Detected %d records with invalid cryptographic hashes, malformed timestamps, or corrupted signatures.",
                            tamperCount))
                    .severity(FindingSeverity.CRITICAL)
                    .confidence(1.0)
                    .evidence(String.join("; ", batchResult.anomalyDescriptions))
                    .affectedAsset("Inference Records")
                    .recommendedAction("Quarantine tainted records and investigate audit log integrity.")
                    .sourceAuditType("INFERENCE")
                    .build());
        }

        if (batchResult.unavailableCount > 0) {
            findings.add(SecurityFinding.builder()
                    .findingId("FIND-PROV-KEY-UNAVAILABLE-" + findingCounter++)
                    .module("INFERENCE")
                    .category("Cryptographic Verification")
                    .description(String.format("Cryptographic signature verification is UNAVAILABLE for %d records (no HMAC secret key configured or missing signatures).",
                            batchResult.unavailableCount))
                    .severity(FindingSeverity.INFO)
                    .confidence(1.0)
                    .evidence("No active HMAC key configured in environment or local key store")
                    .affectedAsset("Cryptographic Key Management")
                    .recommendedAction("Configure 'sentinelcv.provenance.hmac-key' or set SENTINELCV_HMAC_KEY to enable full digital signature verification.")
                    .sourceAuditType("INFERENCE")
                    .build());
        }

        // Always add explicit cryptographic limitation notice (INFO)
        findings.add(SecurityFinding.builder()
                .findingId("FIND-PROV-LIMITATION-" + findingCounter++)
                .module("INFERENCE")
                .category("Cryptographic Limitations")
                .description("Cryptographic verification proves internal consistency with hashed artifacts. " +
                        "A valid hash does NOT prove that inference was genuinely executed inside a trusted enclave.")
                .severity(FindingSeverity.INFO)
                .confidence(1.0)
                .evidence("Theoretical limit of offline software hash-linked records")
                .affectedAsset("Cryptographic Trust Model")
                .recommendedAction("Combine software hash chains with hardware-based attestation (TPM / SGX) where high-assurance guarantees are required.")
                .sourceAuditType("INFERENCE")
                .build());

        for (SecurityFinding f : findings) {
            securityFindingRepository.save(f);
        }

        // Determine assessment decision according to strict precedence:
        // 1. Empty batch → UNAVAILABLE
        // 2. Any broken hash chain → QUARANTINE
        // 3. Any tampered authenticated payload / invalid hash → QUARANTINE
        // 4. Replay detection without cryptographic integrity compromise → REVIEW
        // 5. Missing key, unavailable verification, or unsupported schema → REVIEW
        // 6. VERIFIED only if at least one record was checked and all checks verified
        String assessmentDecision;
        boolean hasIntegrityCompromise = batchResult.hasCryptographicTampering()
                || batchResult.brokenChainLinks > 0
                || batchResult.cryptographicFailures > 0;

        if (records == null || records.isEmpty() || batchResult.totalRecordsChecked == 0) {
            assessmentDecision = "UNAVAILABLE";
        } else if (hasIntegrityCompromise) {
            assessmentDecision = "QUARANTINE";
        } else if (batchResult.replayAttempts > 0
                || batchResult.hasCategory(InferenceValidator.FailureCategory.REPLAY_DETECTED)
                || batchResult.hasCategory(InferenceValidator.FailureCategory.INVALID_SEQUENCE)
                || batchResult.hasCategory(InferenceValidator.FailureCategory.DUPLICATE_RECORD_ID)) {
            assessmentDecision = "REVIEW";
        } else if (batchResult.unavailableCount > 0
                || batchResult.hasCategory(InferenceValidator.FailureCategory.MISSING_KEY)
                || batchResult.hasCategory(InferenceValidator.FailureCategory.UNSUPPORTED_SCHEMA)) {
            assessmentDecision = "REVIEW";
        } else if (batchResult.totalRecordsChecked > 0 && batchResult.verifiedCount == batchResult.totalRecordsChecked) {
            assessmentDecision = "VERIFIED";
        } else {
            assessmentDecision = "REVIEW";
        }

        batchResult.assessmentDecision = assessmentDecision;

        // Propagate structured check execution status to RiskAssessmentService
        String lastHash = null;
        if (records != null) {
            for (int i = records.size() - 1; i >= 0; i--) {
                InferenceRecord rec = records.get(i);
                if (rec != null && rec.getCurrentRecordHash() != null) {
                    lastHash = rec.getCurrentRecordHash();
                    break;
                }
            }
        }

        if (riskAssessmentService != null) {
            CheckExecutionStatus checkStatus;
            if (records == null || records.isEmpty() || batchResult.totalRecordsChecked == 0) {
                checkStatus = CheckExecutionStatus.NOT_EXECUTED;
            } else if (hasIntegrityCompromise || batchResult.failedCount > 0) {
                checkStatus = CheckExecutionStatus.FAILED;
            } else if (batchResult.unavailableCount > 0) {
                checkStatus = CheckExecutionStatus.UNAVAILABLE;
            } else if (batchResult.verifiedCount == batchResult.totalRecordsChecked) {
                checkStatus = CheckExecutionStatus.PASSED;
            } else {
                checkStatus = CheckExecutionStatus.FAILED;
            }
            riskAssessmentService.recordCheckStatus("provenance.verification", null, "provenance-chain", lastHash, checkStatus, "Batch decision: " + assessmentDecision);
        }

        // Record in Audit History
        int nonNullCount = records != null ? (int) records.stream().filter(java.util.Objects::nonNull).count() : 0;
        AuditHistory history = new AuditHistory();
        history.setAuditType("INFERENCE");
        history.setTargetName("Inference Batch (" + nonNullCount + " records)");
        history.setStatus("COMPLETED");
        history.setFindingsCount(findings.size());
        history.setAssessmentDecision(assessmentDecision);
        history.setStartedAt(LocalDateTime.now());
        history.setCompletedAt(LocalDateTime.now());
        history.setSummary(String.format("Verified %d records: %d verified, %d failed, %d unavailable, %d broken chains, %d replays. Decision: %s.",
                batchResult.totalRecordsChecked, batchResult.verifiedCount, batchResult.failedCount,
                batchResult.unavailableCount, batchResult.brokenChainLinks, batchResult.replayAttempts, assessmentDecision));
        auditHistoryRepository.save(history);

        return batchResult;
    }
}
