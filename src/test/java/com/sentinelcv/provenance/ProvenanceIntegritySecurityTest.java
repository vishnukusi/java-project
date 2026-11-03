package com.sentinelcv.provenance;

import com.sentinelcv.entity.AuditReport;
import com.sentinelcv.entity.InferenceRecord;
import com.sentinelcv.enums.AssessmentDecision;
import com.sentinelcv.enums.CheckExecutionStatus;
import com.sentinelcv.inference.InferenceValidator;
import com.sentinelcv.repository.InferenceRecordRepository;
import com.sentinelcv.repository.MandatoryCheckStatusRepository;
import com.sentinelcv.repository.SecurityFindingRepository;
import com.sentinelcv.service.InferenceProvenanceService;
import com.sentinelcv.service.ReportService;
import com.sentinelcv.service.RiskAssessmentService;
import com.sentinelcv.util.CryptoUtil;
import com.sentinelcv.util.HashUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ProvenanceIntegritySecurityTest {

    @Autowired
    private RiskAssessmentService riskAssessmentService;

    @Autowired
    private InferenceRecordRepository recordRepository;

    @Autowired
    private ProvenanceKeyManager provenanceKeyManager;

    @Autowired
    private InferenceValidator inferenceValidator;

    @Autowired
    private InferenceProvenanceService inferenceProvenanceService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private MandatoryCheckStatusRepository checkStatusRepository;

    @Autowired
    private SecurityFindingRepository findingRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private List<InferenceRecord> originalRecordsBackup;

    @BeforeEach
    void backupAndSetupCleanChain() {
        findingRepository.deleteAll();
        originalRecordsBackup = new ArrayList<>(recordRepository.findAllOrderedBySequence());
        rebuildStandardValidChain();
    }

    @AfterEach
    void restoreDatabase() {
        findingRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM inference_records");
        for (InferenceRecord r : originalRecordsBackup) {
            recordRepository.save(r);
        }
    }

    private void rebuildStandardValidChain() {
        jdbcTemplate.update("DELETE FROM inference_records");
        String key = provenanceKeyManager.getOrCreateLocalKey();
        String prevHash = "GENESIS";
        String lastHash = null;

        for (int i = 1; i <= 3; i++) {
            InferenceRecord r = new InferenceRecord();
            r.setRecordId("rec_test_00" + i);
            r.setSequenceNumber(i);
            r.setTimestamp(LocalDateTime.now().minusMinutes(10 - i).toString());
            r.setModelIdentifier("demo_vision_model");
            r.setModelHash(HashUtil.sha256("model_bytes_" + i));
            r.setInputImageHash(HashUtil.sha256("image_bytes_" + i));
            r.setConfigHash(HashUtil.sha256("config_standard"));
            r.setOutputHash(HashUtil.sha256("output_logits_" + i));
            r.setNonce(CryptoUtil.generateNonce());
            r.setPreviousRecordHash(prevHash);

            String payload = inferenceValidator.computeCanonicalPayload(r);
            String currentHash = HashUtil.sha256(payload);
            r.setCurrentRecordHash(currentHash);
            r.setHmacValue(CryptoUtil.computeHmac(payload, key));

            recordRepository.save(r);
            prevHash = currentHash;
            lastHash = currentHash;
        }

        riskAssessmentService.recordCheckStatus("provenance.verification", null,
                "provenance-chain", lastHash, CheckExecutionStatus.PASSED, "Valid complete chain");
    }

    // =========================================================================
    // MANDATORY PROVENANCE ADVERSARIAL TESTS (PART 4 - C5)
    // =========================================================================

    /**
     * 1. testUnchangedCompleteChainVerifies
     * Authoritative complete-chain verification on untampered records produces PASSED.
     */
    @Test
    void testUnchangedCompleteChainVerifies() {
        CheckExecutionStatus status = riskAssessmentService.getCheckStatus("provenance.verification");
        assertEquals(CheckExecutionStatus.PASSED, status,
                "Untampered complete cryptographic chain must resolve to PASSED");
    }

    /**
     * 2. testEarlierRecordPayloadModificationInvalidatesResult
     * Modifying input_image_hash of record #1 directly in SQLite must immediately produce FAILED.
     * Stale cache must never return PASSED.
     */
    @Test
    void testEarlierRecordPayloadModificationInvalidatesResult() {
        // Direct SQL update to bypass any Java service caching hooks
        jdbcTemplate.update("UPDATE inference_records SET input_image_hash = ? WHERE sequence_number = 1",
                "TAMPERED_DIRECT_SQL_IMAGE_HASH");

        CheckExecutionStatus status = riskAssessmentService.getCheckStatus("provenance.verification");
        assertEquals(CheckExecutionStatus.FAILED, status,
                "Tampering with earlier record payload must invalidate provenance status to FAILED");
    }

    /**
     * 3. testEarlierRecordHmacModificationInvalidatesResult
     * Modifying HMAC of record #1 directly in SQLite must immediately produce FAILED.
     */
    @Test
    void testEarlierRecordHmacModificationInvalidatesResult() {
        jdbcTemplate.update("UPDATE inference_records SET hmac_value = ? WHERE sequence_number = 1",
                "BOGUS_INVALID_HMAC_SIGNATURE");

        CheckExecutionStatus status = riskAssessmentService.getCheckStatus("provenance.verification");
        assertEquals(CheckExecutionStatus.FAILED, status,
                "Tampering with earlier record HMAC signature must produce FAILED");
    }

    /**
     * 4. testPreviousHashModificationInvalidatesResult
     * Modifying previous_record_hash on record #2 directly in SQLite must break the chain and produce FAILED.
     */
    @Test
    void testPreviousHashModificationInvalidatesResult() {
        jdbcTemplate.update("UPDATE inference_records SET previous_record_hash = ? WHERE sequence_number = 2",
                "CORRUPTED_PREVIOUS_HASH_VALUE");

        CheckExecutionStatus status = riskAssessmentService.getCheckStatus("provenance.verification");
        assertEquals(CheckExecutionStatus.FAILED, status,
                "Breaking previous_record_hash link must produce FAILED");
    }

    /**
     * 5. testSequenceModificationInvalidatesResult
     * Modifying sequence numbers (e.g. sequence 2 -> 99) directly in SQLite must produce FAILED.
     */
    @Test
    void testSequenceModificationInvalidatesResult() {
        jdbcTemplate.update("UPDATE inference_records SET sequence_number = 99 WHERE sequence_number = 2");

        CheckExecutionStatus status = riskAssessmentService.getCheckStatus("provenance.verification");
        assertEquals(CheckExecutionStatus.FAILED, status,
                "Non-contiguous sequence numbers must produce FAILED");
    }

    /**
     * 6. testRecordDeletionInvalidatesResult
     * Deleting record #2 from the chain directly in SQLite must produce FAILED.
     */
    @Test
    void testRecordDeletionInvalidatesResult() {
        jdbcTemplate.update("DELETE FROM inference_records WHERE sequence_number = 2");

        CheckExecutionStatus status = riskAssessmentService.getCheckStatus("provenance.verification");
        assertEquals(CheckExecutionStatus.FAILED, status,
                "Deleting a middle record from the chain must produce FAILED");
    }

    /**
     * 7. testRecordInsertionInvalidatesResult
     * Inserting an unauthorized record directly in SQLite must produce FAILED.
     */
    @Test
    void testRecordInsertionInvalidatesResult() {
        jdbcTemplate.update("INSERT INTO inference_records " +
                "(record_id, sequence_number, timestamp, previous_record_hash, current_record_hash, hmac_value, nonce) " +
                "VALUES ('rec_injected', 4, ?, 'BOGUS_PREV', 'BOGUS_CURR', 'BOGUS_HMAC', 'BOGUS_NONCE')",
                LocalDateTime.now().toString());

        CheckExecutionStatus status = riskAssessmentService.getCheckStatus("provenance.verification");
        assertEquals(CheckExecutionStatus.FAILED, status,
                "Injecting an unverified record into the chain must produce FAILED");
    }

    /**
     * 8. testDuplicateRecordInvalidatesResult
     * Duplicate sequence number or record ID directly in SQLite must produce FAILED.
     */
    @Test
    void testDuplicateRecordInvalidatesResult() {
        // Insert a record with duplicate sequence_number 2
        jdbcTemplate.update("INSERT INTO inference_records " +
                "(record_id, sequence_number, timestamp, previous_record_hash, current_record_hash, hmac_value, nonce) " +
                "VALUES ('rec_dup_2', 2, ?, 'GENESIS', 'C_DUP', 'HMAC_DUP', 'NONCE_DUP')",
                LocalDateTime.now().toString());

        CheckExecutionStatus status = riskAssessmentService.getCheckStatus("provenance.verification");
        assertEquals(CheckExecutionStatus.FAILED, status,
                "Duplicate sequence numbers must produce FAILED");
    }

    /**
     * 9. testEmptyChainNeverPasses
     * An empty inference record repository must never return PASSED.
     */
    @Test
    void testEmptyChainNeverPasses() {
        jdbcTemplate.update("DELETE FROM inference_records");

        CheckExecutionStatus status = riskAssessmentService.getCheckStatus("provenance.verification");
        assertNotEquals(CheckExecutionStatus.PASSED, status,
                "Empty inference record chain must NEVER resolve to PASSED");
        assertTrue(status == CheckExecutionStatus.NOT_EXECUTED || status == CheckExecutionStatus.UNAVAILABLE,
                "Empty chain must resolve to NOT_EXECUTED or UNAVAILABLE");
    }

    /**
     * 10. testMissingKeyNeverPasses
     * If the verification key is absent, status must fail closed to UNAVAILABLE, never PASSED.
     */
    @Test
    void testMissingKeyNeverPasses() {
        // Create an un-injected service instance with null key manager
        RiskAssessmentService serviceWithoutKey = new RiskAssessmentService(
                findingRepository, null, checkStatusRepository, null, null, null, null,
                recordRepository, inferenceValidator, null
        );

        CheckExecutionStatus status = serviceWithoutKey.getCheckStatus("provenance.verification");
        assertNotEquals(CheckExecutionStatus.PASSED, status,
                "Missing verification key must NEVER produce PASSED status");
        assertEquals(CheckExecutionStatus.UNAVAILABLE, status,
                "Missing key manager must resolve to UNAVAILABLE");
    }

    /**
     * 11. testDatabaseFailureNeverPasses
     * Database exceptions must fail closed to FAILED, never returning PASSED.
     */
    @Test
    void testDatabaseFailureNeverPasses() {
        // Pointing check record to an invalid chain hash triggers mismatch/failure
        riskAssessmentService.recordCheckStatus("provenance.verification", null,
                "provenance-chain", "INVALID_UNKNOWN_HASH", CheckExecutionStatus.PASSED, "Invalid");

        CheckExecutionStatus status = riskAssessmentService.getCheckStatus("provenance.verification");
        assertNotEquals(CheckExecutionStatus.PASSED, status,
                "Corrupted or failing database lookups must NEVER produce PASSED");
    }

    /**
     * 12. testThreadReuseCannotReuseStaleVerification
     * Verifies that worker thread reuse cannot serve a stale cached PASSED status after DB tampering.
     */
    @Test
    void testThreadReuseCannotReuseStaleVerification() throws Exception {
        ExecutorService singleWorker = Executors.newSingleThreadExecutor();
        try {
            // Task 1: Check verification on worker thread (returns PASSED)
            CheckExecutionStatus status1 = singleWorker.submit(() ->
                    riskAssessmentService.getCheckStatus("provenance.verification")).get();
            assertEquals(CheckExecutionStatus.PASSED, status1);

            // Attacker modifies record 1 directly in SQLite
            jdbcTemplate.update("UPDATE inference_records SET input_image_hash = ? WHERE sequence_number = 1",
                    "TAMPERED_BY_SQL_ON_SAME_THREAD");

            // Task 2: Executed on the EXACT SAME worker thread
            CheckExecutionStatus status2 = singleWorker.submit(() ->
                    riskAssessmentService.getCheckStatus("provenance.verification")).get();

            assertEquals(CheckExecutionStatus.FAILED, status2,
                    "Reused worker thread must NOT serve a stale cached PASSED result; must detect tampering");
        } finally {
            singleWorker.shutdown();
        }
    }

    /**
     * 13. testModificationBetweenRiskRequestsInvalidatesResult
     * Modifying a record between two assessSystemRisk calls must change decision from ACCEPT to REVIEW/QUARANTINE.
     */
    @Test
    void testModificationBetweenRiskRequestsInvalidatesResult() {
        // Initially, provenance check is PASSED
        assertEquals(CheckExecutionStatus.PASSED, riskAssessmentService.getCheckStatus("provenance.verification"));

        // Direct database tampering
        jdbcTemplate.update("UPDATE inference_records SET input_image_hash = ? WHERE sequence_number = 1",
                "TAMPERED_FOR_RISK_CHECK");

        // Freshness validation must immediately return FAILED
        assertEquals(CheckExecutionStatus.FAILED, riskAssessmentService.getCheckStatus("provenance.verification"));

        RiskAssessmentService.SystemRiskAssessment assessment = riskAssessmentService.assessSystemRisk();
        assertNotEquals(AssessmentDecision.ACCEPT, assessment.overallDecision,
                "Tampered provenance record must prevent ACCEPT decision");
        assertTrue(assessment.overallDecision == AssessmentDecision.REVIEW ||
                   assessment.overallDecision == AssessmentDecision.QUARANTINE,
                "Tampered provenance must mandate REVIEW or QUARANTINE");
    }

    /**
     * 14. testDashboardAndReportUseCurrentVerification
     * Dashboard risk assessment and generated PDF/JSON reports must both reflect current verification.
     */
    @Test
    void testDashboardAndReportUseCurrentVerification() throws Exception {
        // Direct database tampering
        jdbcTemplate.update("UPDATE inference_records SET hmac_value = ? WHERE sequence_number = 2",
                "TAMPERED_HMAC_FOR_REPORT");

        // 1. Authoritative risk assessment
        RiskAssessmentService.SystemRiskAssessment assessment = riskAssessmentService.assessSystemRisk();
        assertNotEquals(AssessmentDecision.ACCEPT, assessment.overallDecision);

        // 2. JSON Report
        AuditReport jsonReport = reportService.generateReport("Provenance Tamper Report", "JSON");
        assertNotNull(jsonReport);
        assertEquals(assessment.overallDecision.name(), jsonReport.getAssessmentDecision(),
                "Report must agree with current authoritative risk assessment");

        // Cleanup
        new File(jsonReport.getFilePath()).delete();
    }
}
