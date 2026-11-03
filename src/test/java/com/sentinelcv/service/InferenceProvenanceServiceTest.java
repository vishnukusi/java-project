package com.sentinelcv.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelcv.entity.AuditHistory;
import com.sentinelcv.entity.InferenceRecord;
import com.sentinelcv.inference.InferenceValidator;
import com.sentinelcv.provenance.ProvenanceKeyManager;
import com.sentinelcv.repository.AuditHistoryRepository;
import com.sentinelcv.repository.InferenceRecordRepository;
import com.sentinelcv.repository.ProvenanceVerificationRepository;
import com.sentinelcv.repository.SecurityFindingRepository;
import com.sentinelcv.util.CryptoUtil;
import com.sentinelcv.util.HashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InferenceProvenanceServiceTest {

    private InferenceRecordRepository recordRepository;
    private ProvenanceVerificationRepository verificationRepository;
    private SecurityFindingRepository securityFindingRepository;
    private AuditHistoryRepository auditHistoryRepository;
    private ProvenanceKeyManager provenanceKeyManager;
    private InferenceValidator validator;
    private ObjectMapper objectMapper;
    private InferenceProvenanceService service;

    private static final String TEST_KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @BeforeEach
    void setUp() {
        recordRepository = mock(InferenceRecordRepository.class);
        verificationRepository = mock(ProvenanceVerificationRepository.class);
        securityFindingRepository = mock(SecurityFindingRepository.class);
        auditHistoryRepository = mock(AuditHistoryRepository.class);
        provenanceKeyManager = mock(ProvenanceKeyManager.class);
        validator = new InferenceValidator();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

        when(provenanceKeyManager.getVerificationKey()).thenReturn(Optional.of(TEST_KEY));

        service = new InferenceProvenanceService(
                recordRepository,
                verificationRepository,
                securityFindingRepository,
                auditHistoryRepository,
                validator,
                provenanceKeyManager,
                objectMapper
        );
    }

    private InferenceRecord createSignedRecord(String recordId, int seq, String timestamp, String modelId,
                                              String prevHash, String nonce, String key) {
        InferenceRecord r = new InferenceRecord();
        r.setRecordId(recordId);
        r.setSequenceNumber(seq);
        r.setTimestamp(timestamp);
        r.setModelIdentifier(modelId);
        r.setModelHash("a".repeat(64));
        r.setInputImageHash("b".repeat(64));
        r.setConfigHash("c".repeat(64));
        r.setOutputHash("d".repeat(64));
        r.setPreviousRecordHash(prevHash);
        r.setNonce(nonce);

        String payload = validator.computeCanonicalPayload(r);
        r.setCurrentRecordHash(HashUtil.sha256(payload));
        if (key != null) {
            r.setHmacValue(CryptoUtil.computeHmac(payload, key));
        }
        return r;
    }

    @Test
    void testBrokenChainPlusReplayProducesQuarantine() throws Exception {
        InferenceRecord r1 = createSignedRecord("rec-1", 1, "2026-11-01T10:00:00", "model-A", "GENESIS", "nonce-1", TEST_KEY);

        // r2 has broken chain link AND replay indicator (duplicate nonce-1)
        InferenceRecord r2 = createSignedRecord("rec-2", 2, "2026-11-01T10:01:00", "model-A", "BROKEN_PREV_HASH", "nonce-1", TEST_KEY);

        String json = objectMapper.writeValueAsString(List.of(r1, r2));
        InferenceValidator.VerificationBatchResult batchResult = service.importAndVerify(json);

        assertTrue(batchResult.brokenChainLinks > 0, "Broken chain links must be detected");
        assertTrue(batchResult.replayAttempts > 0, "Replay attempt must be detected");
        assertEquals("QUARANTINE", batchResult.assessmentDecision,
                "Broken chain + replay indicator must mandate QUARANTINE");

        ArgumentCaptor<AuditHistory> captor = ArgumentCaptor.forClass(AuditHistory.class);
        verify(auditHistoryRepository).save(captor.capture());
        assertEquals("QUARANTINE", captor.getValue().getAssessmentDecision());
    }

    @Test
    void testTamperedPayloadPlusReplayProducesQuarantine() throws Exception {
        InferenceRecord r1 = createSignedRecord("rec-1", 1, "2026-11-01T10:00:00", "model-A", "GENESIS", "nonce-1", TEST_KEY);

        // r2 has duplicate nonce (replay indicator) AND tampered record hash (cryptographic failure)
        InferenceRecord r2 = createSignedRecord("rec-2", 2, "2026-11-01T10:01:00", "model-A", r1.getCurrentRecordHash(), "nonce-1", TEST_KEY);
        // Tamper with record hash
        r2.setCurrentRecordHash("f".repeat(64));

        String json = objectMapper.writeValueAsString(List.of(r1, r2));
        InferenceValidator.VerificationBatchResult batchResult = service.importAndVerify(json);

        assertEquals(0, batchResult.brokenChainLinks);
        assertTrue(batchResult.replayAttempts > 0, "Replay attempt must be detected");
        assertTrue(batchResult.cryptographicFailures > 0, "Cryptographic failure must be detected");
        assertEquals("QUARANTINE", batchResult.assessmentDecision,
                "Tampered payload + replay indicator must mandate QUARANTINE, never downgrade to REVIEW");

        ArgumentCaptor<AuditHistory> captor = ArgumentCaptor.forClass(AuditHistory.class);
        verify(auditHistoryRepository).save(captor.capture());
        assertEquals("QUARANTINE", captor.getValue().getAssessmentDecision());
    }

    @Test
    void testReplayAloneProducesReview() throws Exception {
        InferenceRecord r1 = createSignedRecord("rec-1", 1, "2026-11-01T10:00:00", "model-A", "GENESIS", "nonce-1", TEST_KEY);

        // r2 is authentic in hash and signature, but duplicates nonce-1 (replay alone)
        InferenceRecord r2 = createSignedRecord("rec-2", 2, "2026-11-01T10:01:00", "model-A", r1.getCurrentRecordHash(), "nonce-1", TEST_KEY);

        String json = objectMapper.writeValueAsString(List.of(r1, r2));
        InferenceValidator.VerificationBatchResult batchResult = service.importAndVerify(json);

        assertEquals(0, batchResult.brokenChainLinks);
        assertEquals(0, batchResult.cryptographicFailures);
        assertTrue(batchResult.replayAttempts > 0);
        assertEquals("REVIEW", batchResult.assessmentDecision,
                "Replay indicator alone without cryptographic tampering must mandate REVIEW");

        ArgumentCaptor<AuditHistory> captor = ArgumentCaptor.forClass(AuditHistory.class);
        verify(auditHistoryRepository).save(captor.capture());
        assertEquals("REVIEW", captor.getValue().getAssessmentDecision());
    }

    @Test
    void testMissingKeyProducesReview() throws Exception {
        when(provenanceKeyManager.getVerificationKey()).thenReturn(Optional.empty());

        InferenceRecord r1 = createSignedRecord("rec-1", 1, "2026-11-01T10:00:00", "model-A", "GENESIS", "nonce-1", null);

        String json = objectMapper.writeValueAsString(List.of(r1));
        InferenceValidator.VerificationBatchResult batchResult = service.importAndVerify(json);

        assertEquals(0, batchResult.failedCount);
        assertEquals(0, batchResult.brokenChainLinks);
        assertEquals(0, batchResult.replayAttempts);
        assertTrue(batchResult.unavailableCount > 0);
        assertEquals("REVIEW", batchResult.assessmentDecision,
                "Missing verification key must mandate REVIEW");

        ArgumentCaptor<AuditHistory> captor = ArgumentCaptor.forClass(AuditHistory.class);
        verify(auditHistoryRepository).save(captor.capture());
        assertEquals("REVIEW", captor.getValue().getAssessmentDecision());
    }

    @Test
    void testEntireValidChainProducesVerified() throws Exception {
        InferenceRecord r1 = createSignedRecord("rec-1", 1, "2026-11-01T10:00:00", "model-A", "GENESIS", "nonce-1", TEST_KEY);
        InferenceRecord r2 = createSignedRecord("rec-2", 2, "2026-11-01T10:01:00", "model-A", r1.getCurrentRecordHash(), "nonce-2", TEST_KEY);

        String json = objectMapper.writeValueAsString(List.of(r1, r2));
        InferenceValidator.VerificationBatchResult batchResult = service.importAndVerify(json);

        assertEquals(2, batchResult.totalRecordsChecked);
        assertEquals(2, batchResult.verifiedCount);
        assertEquals(0, batchResult.failedCount);
        assertEquals(0, batchResult.brokenChainLinks);
        assertEquals(0, batchResult.replayAttempts);
        assertEquals(0, batchResult.unavailableCount);
        assertEquals("VERIFIED", batchResult.assessmentDecision,
                "Entire valid chain must mandate VERIFIED");

        ArgumentCaptor<AuditHistory> captor = ArgumentCaptor.forClass(AuditHistory.class);
        verify(auditHistoryRepository).save(captor.capture());
        assertEquals("VERIFIED", captor.getValue().getAssessmentDecision());
    }

    @Test
    void testDecisionIsIndependentOfRecordOrder() throws Exception {
        InferenceRecord r1 = createSignedRecord("rec-1", 1, "2026-11-01T10:00:00", "model-A", "GENESIS", "nonce-1", TEST_KEY);
        InferenceRecord r2 = createSignedRecord("rec-2", 2, "2026-11-01T10:01:00", "model-A", r1.getCurrentRecordHash(), "nonce-1", TEST_KEY);
        r2.setCurrentRecordHash("f".repeat(64)); // Tampered + replay

        // Pass in forward order [r1, r2]
        String jsonForward = objectMapper.writeValueAsString(List.of(r1, r2));
        InferenceValidator.VerificationBatchResult forwardResult = service.importAndVerify(jsonForward);

        // Pass in reversed order [r2, r1]
        String jsonReverse = objectMapper.writeValueAsString(List.of(r2, r1));
        InferenceValidator.VerificationBatchResult reverseResult = service.importAndVerify(jsonReverse);

        assertEquals(forwardResult.assessmentDecision, reverseResult.assessmentDecision,
                "Assessment decision must be independent of input record ordering");
        assertEquals("QUARANTINE", reverseResult.assessmentDecision);
    }

    @Test
    void testEmptyBatchProducesUnavailableNotVerified() throws Exception {
        String jsonEmpty = "[]";
        InferenceValidator.VerificationBatchResult result = service.importAndVerify(jsonEmpty);

        assertEquals(0, result.totalRecordsChecked);
        assertNotEquals("VERIFIED", result.assessmentDecision,
                "Empty batch must NEVER produce VERIFIED");
        assertEquals("UNAVAILABLE", result.assessmentDecision);
    }

    @Test
    void testUnsupportedSchemaProducesReview() throws Exception {
        InferenceRecord r1 = createSignedRecord("rec-1", 1, "2026-11-01T10:00:00", "model-A", "GENESIS", "nonce-1", TEST_KEY);
        r1.setSchemaVersion("V99_UNKNOWN");

        String json = objectMapper.writeValueAsString(List.of(r1));
        InferenceValidator.VerificationBatchResult result = service.importAndVerify(json);

        assertEquals("REVIEW", result.assessmentDecision,
                "Unsupported schema without cryptographic tampering must mandate REVIEW");
        assertTrue(result.hasCategory(InferenceValidator.FailureCategory.UNSUPPORTED_SCHEMA));
    }

    @Test
    void testNullJsonStringProducesUnavailableWithoutException() {
        assertDoesNotThrow(() -> {
            InferenceValidator.VerificationBatchResult result = service.importAndVerify(null);
            assertNotNull(result);
            assertEquals(0, result.totalRecordsChecked);
            assertEquals("UNAVAILABLE", result.assessmentDecision);
            assertNotEquals("VERIFIED", result.assessmentDecision);
        });
    }

    @Test
    void testLiteralNullJsonStringProducesUnavailableWithoutException() {
        assertDoesNotThrow(() -> {
            InferenceValidator.VerificationBatchResult result = service.importAndVerify("null");
            assertNotNull(result);
            assertEquals(0, result.totalRecordsChecked);
            assertEquals("UNAVAILABLE", result.assessmentDecision);
            assertNotEquals("VERIFIED", result.assessmentDecision);
        });
    }

    @Test
    void testNullRecordsCollectionProducesUnavailableWithoutException() {
        assertDoesNotThrow(() -> {
            InferenceValidator.VerificationBatchResult result = service.processAndVerifyRecords(null);
            assertNotNull(result);
            assertEquals(0, result.totalRecordsChecked);
            assertEquals("UNAVAILABLE", result.assessmentDecision);
        });
    }

    @Test
    void testBatchContainingNullRecordsClassifiedAsMalformedAndNeverVerified() {
        InferenceRecord r1 = createSignedRecord("rec-1", 1, "2026-11-01T10:00:00", "model-A", "GENESIS", "nonce-1", TEST_KEY);
        java.util.List<InferenceRecord> recordsWithNull = new java.util.ArrayList<>();
        recordsWithNull.add(null);
        recordsWithNull.add(r1);

        assertDoesNotThrow(() -> {
            InferenceValidator.VerificationBatchResult result = service.processAndVerifyRecords(recordsWithNull);
            assertNotNull(result);
            assertEquals(2, result.totalRecordsChecked);
            assertTrue(result.failedCount >= 1, "Null record entry must increment failedCount");
            assertTrue(result.hasCategory(InferenceValidator.FailureCategory.MALFORMED_RECORD),
                    "Null record must be classified as MALFORMED_RECORD");
            assertNotEquals("VERIFIED", result.assessmentDecision, "Batch with null records must NEVER be VERIFIED");
        });
    }

    @Test
    void testAllNullRecordsBatchClassifiedAsFailed() {
        java.util.List<InferenceRecord> allNullRecords = new java.util.ArrayList<>();
        allNullRecords.add(null);
        allNullRecords.add(null);

        assertDoesNotThrow(() -> {
            InferenceValidator.VerificationBatchResult result = service.processAndVerifyRecords(allNullRecords);
            assertNotNull(result);
            assertEquals(2, result.totalRecordsChecked);
            assertEquals(2, result.failedCount);
            assertTrue(result.hasCategory(InferenceValidator.FailureCategory.MALFORMED_RECORD));
            assertNotEquals("VERIFIED", result.assessmentDecision);
        });
    }
}
