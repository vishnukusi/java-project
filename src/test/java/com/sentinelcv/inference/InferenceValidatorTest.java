package com.sentinelcv.inference;

import com.sentinelcv.entity.InferenceRecord;
import com.sentinelcv.entity.ProvenanceVerification;
import com.sentinelcv.enums.VerificationStatus;
import com.sentinelcv.util.CryptoUtil;
import com.sentinelcv.util.HashUtil;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InferenceValidatorTest {

    private final InferenceValidator validator = new InferenceValidator();
    private final String hmacKey = "test-secret-key-sentinel-2026";

    @Test
    void testValidChainVerification() {
        List<InferenceRecord> records = new ArrayList<>();
        String prevHash = "GENESIS";

        for (int i = 1; i <= 3; i++) {
            InferenceRecord r = new InferenceRecord();
            r.setRecordId("rec_" + i);
            r.setSequenceNumber(i);
            r.setTimestamp(LocalDateTime.now().minusMinutes(10 - i).toString());
            r.setModelHash(HashUtil.sha256("model" + i));
            r.setInputImageHash(HashUtil.sha256("input" + i));
            r.setConfigHash(HashUtil.sha256("config" + i));
            r.setOutputHash(HashUtil.sha256("output" + i));
            r.setNonce(CryptoUtil.generateNonce());
            r.setPreviousRecordHash(prevHash);

            String canonicalPayload = validator.computeCanonicalPayload(r);
            String currentHash = HashUtil.sha256(canonicalPayload);
            r.setCurrentRecordHash(currentHash);
            r.setHmacValue(CryptoUtil.computeHmac(canonicalPayload, hmacKey));

            records.add(r);
            prevHash = currentHash;
        }

        InferenceValidator.VerificationBatchResult result = validator.verifyRecords(records, hmacKey);
        assertEquals(3, result.totalRecordsChecked);
        assertEquals(3, result.verifiedCount);
        assertEquals(0, result.failedCount);
        assertEquals(0, result.brokenChainLinks);
        assertEquals(0, result.replayAttempts);
        assertEquals(0, result.unavailableCount);
    }

    @Test
    void testDetectBrokenHashChain() {
        List<InferenceRecord> records = new ArrayList<>();

        InferenceRecord r1 = createSampleRecord(1, "GENESIS", "nonce1");
        // Tamper r2 previousRecordHash
        InferenceRecord r2 = createSampleRecord(2, "CORRUPTED_PREVIOUS_HASH", "nonce2");

        records.add(r1);
        records.add(r2);

        InferenceValidator.VerificationBatchResult result = validator.verifyRecords(records, hmacKey);
        assertTrue(result.brokenChainLinks > 0);
        assertTrue(result.failedCount > 0);
        assertEquals(VerificationStatus.FAILED, result.verifications.get(1).getChainLinkStatus());
        assertEquals(VerificationStatus.FAILED, result.verifications.get(1).getOverallStatus());
    }

    @Test
    void testDetectReplayDuplicateNonce() {
        List<InferenceRecord> records = new ArrayList<>();
        String reusedNonce = "SAME_REPLAYED_NONCE";

        InferenceRecord r1 = createSampleRecord(1, "GENESIS", reusedNonce);
        InferenceRecord r2 = createSampleRecord(2, r1.getCurrentRecordHash(), reusedNonce); // Reused nonce!

        records.add(r1);
        records.add(r2);

        InferenceValidator.VerificationBatchResult result = validator.verifyRecords(records, hmacKey);
        assertTrue(result.replayAttempts > 0);
        assertEquals(VerificationStatus.FAILED, result.verifications.get(1).getNonceStatus());
        assertEquals(VerificationStatus.FAILED, result.verifications.get(1).getOverallStatus());
    }

    @Test
    void testInvalidInputHashFailsOverall() {
        InferenceRecord r1 = createSampleRecord(1, "GENESIS", "nonce_in_1");
        r1.setInputImageHash("not_a_valid_64_char_hex_hash");
        // Recompute current hash with the invalid input hash to test input validation specifically
        String payload = validator.computeCanonicalPayload(r1);
        r1.setCurrentRecordHash(HashUtil.sha256(payload));
        r1.setHmacValue(CryptoUtil.computeHmac(payload, hmacKey));

        InferenceValidator.VerificationBatchResult result = validator.verifyRecords(List.of(r1), hmacKey);
        assertEquals(1, result.failedCount);
        assertEquals(VerificationStatus.FAILED, result.verifications.get(0).getInputHashStatus());
        assertEquals(VerificationStatus.FAILED, result.verifications.get(0).getOverallStatus());
    }

    @Test
    void testInvalidModelHashFailsOverall() {
        InferenceRecord r1 = createSampleRecord(1, "GENESIS", "nonce_md_1");
        r1.setModelHash("bad_model_hash");
        String payload = validator.computeCanonicalPayload(r1);
        r1.setCurrentRecordHash(HashUtil.sha256(payload));
        r1.setHmacValue(CryptoUtil.computeHmac(payload, hmacKey));

        InferenceValidator.VerificationBatchResult result = validator.verifyRecords(List.of(r1), hmacKey);
        assertEquals(1, result.failedCount);
        assertEquals(VerificationStatus.FAILED, result.verifications.get(0).getModelHashStatus());
        assertEquals(VerificationStatus.FAILED, result.verifications.get(0).getOverallStatus());
    }

    @Test
    void testInvalidConfigHashFailsOverall() {
        InferenceRecord r1 = createSampleRecord(1, "GENESIS", "nonce_cfg_1");
        r1.setConfigHash("bad_config_hash");
        String payload = validator.computeCanonicalPayload(r1);
        r1.setCurrentRecordHash(HashUtil.sha256(payload));
        r1.setHmacValue(CryptoUtil.computeHmac(payload, hmacKey));

        InferenceValidator.VerificationBatchResult result = validator.verifyRecords(List.of(r1), hmacKey);
        assertEquals(1, result.failedCount);
        assertEquals(VerificationStatus.FAILED, result.verifications.get(0).getConfigHashStatus());
        assertEquals(VerificationStatus.FAILED, result.verifications.get(0).getOverallStatus());
    }

    @Test
    void testInvalidOutputHashFailsOverall() {
        InferenceRecord r1 = createSampleRecord(1, "GENESIS", "nonce_out_1");
        r1.setOutputHash("bad_output_hash");
        String payload = validator.computeCanonicalPayload(r1);
        r1.setCurrentRecordHash(HashUtil.sha256(payload));
        r1.setHmacValue(CryptoUtil.computeHmac(payload, hmacKey));

        InferenceValidator.VerificationBatchResult result = validator.verifyRecords(List.of(r1), hmacKey);
        assertEquals(1, result.failedCount);
        assertEquals(VerificationStatus.FAILED, result.verifications.get(0).getOutputHashStatus());
        assertEquals(VerificationStatus.FAILED, result.verifications.get(0).getOverallStatus());
    }

    @Test
    void testMalformedTimestampFailsOverall() {
        InferenceRecord r1 = createSampleRecord(1, "GENESIS", "nonce_ts_1");
        r1.setTimestamp("INVALID_DATE_TIME_STRING_2026");
        String payload = validator.computeCanonicalPayload(r1);
        r1.setCurrentRecordHash(HashUtil.sha256(payload));
        r1.setHmacValue(CryptoUtil.computeHmac(payload, hmacKey));

        InferenceValidator.VerificationBatchResult result = validator.verifyRecords(List.of(r1), hmacKey);
        assertEquals(1, result.failedCount);
        assertEquals(VerificationStatus.FAILED, result.verifications.get(0).getTimestampStatus());
        assertEquals(VerificationStatus.FAILED, result.verifications.get(0).getOverallStatus());
    }

    @Test
    void testMissingHmacProducesUnavailableRatherThanVerified() {
        InferenceRecord r1 = createSampleRecord(1, "GENESIS", "nonce_no_hmac");
        r1.setHmacValue(null); // Missing HMAC

        InferenceValidator.VerificationBatchResult result = validator.verifyRecords(List.of(r1), hmacKey);
        assertEquals(0, result.failedCount);
        assertEquals(1, result.unavailableCount);
        assertEquals(0, result.verifiedCount);
        assertEquals(VerificationStatus.UNAVAILABLE, result.verifications.get(0).getSignatureStatus());
        assertEquals(VerificationStatus.UNAVAILABLE, result.verifications.get(0).getOverallStatus());
    }

    @Test
    void testMissingVerificationKeyProducesUnavailable() {
        InferenceRecord r1 = createSampleRecord(1, "GENESIS", "nonce_key_unavail");

        InferenceValidator.VerificationBatchResult result = validator.verifyRecords(List.of(r1), null); // key is null
        assertEquals(0, result.failedCount);
        assertEquals(1, result.unavailableCount);
        assertEquals(0, result.verifiedCount);
        assertEquals(VerificationStatus.UNAVAILABLE, result.verifications.get(0).getSignatureStatus());
        assertEquals(VerificationStatus.UNAVAILABLE, result.verifications.get(0).getOverallStatus());
    }

    @Test
    void testFirstRecordPreviousHashMustBeGenesis() {
        InferenceRecord r1 = createSampleRecord(1, "NOT_GENESIS_PREV_HASH", "nonce_genesis_fail");
        // Even with recomputed current hash, it MUST fail because record 1 previous hash != GENESIS
        String payload = validator.computeCanonicalPayload(r1);
        r1.setCurrentRecordHash(HashUtil.sha256(payload));
        r1.setHmacValue(CryptoUtil.computeHmac(payload, hmacKey));

        InferenceValidator.VerificationBatchResult result = validator.verifyRecords(List.of(r1), hmacKey);
        assertEquals(1, result.failedCount);
        assertTrue(result.brokenChainLinks > 0);
        assertEquals(VerificationStatus.FAILED, result.verifications.get(0).getChainLinkStatus());
        assertEquals(VerificationStatus.FAILED, result.verifications.get(0).getOverallStatus());
    }

    @Test
    void testSequenceMustStartAtOne() {
        InferenceRecord r1 = createSampleRecord(5, "GENESIS", "nonce_seq_5");
        String payload = validator.computeCanonicalPayload(r1);
        r1.setCurrentRecordHash(HashUtil.sha256(payload));
        r1.setHmacValue(CryptoUtil.computeHmac(payload, hmacKey));

        InferenceValidator.VerificationBatchResult result = validator.verifyRecords(List.of(r1), hmacKey);
        assertEquals(1, result.failedCount);
        assertEquals(VerificationStatus.FAILED, result.verifications.get(0).getSequenceStatus());
        assertEquals(VerificationStatus.FAILED, result.verifications.get(0).getOverallStatus());
    }

    @Test
    void testSequenceDiscontinuityDetected() {
        InferenceRecord r1 = createSampleRecord(1, "GENESIS", "nonce_seq_1");
        InferenceRecord r2 = createSampleRecord(3, r1.getCurrentRecordHash(), "nonce_seq_3"); // Jump from 1 to 3!

        InferenceValidator.VerificationBatchResult result = validator.verifyRecords(List.of(r1, r2), hmacKey);
        assertEquals(1, result.failedCount);
        assertEquals(VerificationStatus.FAILED, result.verifications.get(1).getSequenceStatus());
        assertEquals(VerificationStatus.FAILED, result.verifications.get(1).getOverallStatus());
    }

    @Test
    void testModifyingRecordIdInvalidatesVerification() {
        InferenceRecord r = createSampleRecord(1, "GENESIS", "nonce_id_test");
        InferenceValidator.VerificationBatchResult cleanResult = validator.verifyRecords(List.of(r), hmacKey);
        assertEquals(1, cleanResult.verifiedCount);

        // Tamper recordId without recomputing hash
        r.setRecordId("malicious_tampered_id");
        InferenceValidator.VerificationBatchResult tamperedResult = validator.verifyRecords(List.of(r), hmacKey);
        assertEquals(1, tamperedResult.failedCount);
        assertEquals(VerificationStatus.FAILED, tamperedResult.verifications.get(0).getOverallStatus());
        assertTrue(tamperedResult.verifications.get(0).getDetails().contains("tampering"));
    }

    @Test
    void testModifyingModelIdentifierInvalidatesVerification() {
        InferenceRecord r = createSampleRecord(1, "GENESIS", "nonce_model_id_test");
        InferenceValidator.VerificationBatchResult cleanResult = validator.verifyRecords(List.of(r), hmacKey);
        assertEquals(1, cleanResult.verifiedCount);

        // Tamper modelIdentifier without recomputing hash
        r.setModelIdentifier("substituted_model_trojan");
        InferenceValidator.VerificationBatchResult tamperedResult = validator.verifyRecords(List.of(r), hmacKey);
        assertEquals(1, tamperedResult.failedCount);
        assertEquals(VerificationStatus.FAILED, tamperedResult.verifications.get(0).getOverallStatus());
        assertTrue(tamperedResult.verifications.get(0).getDetails().contains("tampering"));
    }

    @Test
    void testDelimitersDoNotCreateAmbiguousPayloads() {
        // Record A has ":" inside recordId
        InferenceRecord rA = createSampleRecord(1, "GENESIS", "nonce_delim_A");
        rA.setRecordId("rec:1;ext");
        rA.setModelIdentifier("demo_model");

        // Record B has different fields but might collide if delimiters were naive
        InferenceRecord rB = createSampleRecord(1, "GENESIS", "nonce_delim_A");
        rB.setRecordId("rec");
        rB.setModelIdentifier("1;ext:demo_model");

        String payloadA = validator.computeCanonicalPayload(rA);
        String payloadB = validator.computeCanonicalPayload(rB);

        assertNotEquals(payloadA, payloadB,
                "Length-prefixed encoding must prevent cross-field delimiter collision ambiguity");
    }

    @Test
    void testHmacAndSha256UseIdenticalCanonicalPayloadBytes() {
        InferenceRecord r = createSampleRecord(1, "GENESIS", "nonce_bytes_test");
        String payloadString = validator.computeCanonicalPayload(r);
        byte[] payloadBytes = validator.computeCanonicalPayloadBytes(r);

        assertArrayEquals(payloadString.getBytes(java.nio.charset.StandardCharsets.UTF_8), payloadBytes);
        assertEquals(HashUtil.sha256(payloadString), HashUtil.sha256(payloadBytes));
        assertTrue(CryptoUtil.verifyHmac(payloadString, hmacKey, r.getHmacValue()));
    }

    @Test
    void testLegacySchemaRecordCannotSilentlyBecomeVerified() {
        InferenceRecord r = createSampleRecord(1, "GENESIS", "nonce_legacy");
        // Compute legacy hash using old format (omitting recordId and modelIdentifier)
        String legacyPayload = String.format("%s:%s:%s:%s:%s:%s:%s:%s",
                r.getPreviousRecordHash(), r.getSequenceNumber(), r.getTimestamp(),
                r.getModelHash(), r.getInputImageHash(), r.getConfigHash(), r.getOutputHash(), r.getNonce());
        r.setCurrentRecordHash(HashUtil.sha256(legacyPayload));
        r.setHmacValue(CryptoUtil.computeHmac(legacyPayload, hmacKey));

        InferenceValidator.VerificationBatchResult result = validator.verifyRecords(List.of(r), hmacKey);
        // Must fail because current canonical payload V2 includes recordId and modelIdentifier
        assertEquals(1, result.failedCount);
        assertNotEquals(VerificationStatus.VERIFIED, result.verifications.get(0).getOverallStatus());
    }

    @Test
    void testDuplicateRecordIdRejected() {
        InferenceRecord r1 = createSampleRecord(1, "GENESIS", "nonce_id_1");
        r1.setRecordId("duplicate-id");
        r1.setCurrentRecordHash(HashUtil.sha256(validator.computeCanonicalPayload(r1)));
        r1.setHmacValue(CryptoUtil.computeHmac(validator.computeCanonicalPayload(r1), hmacKey));

        InferenceRecord r2 = createSampleRecord(2, r1.getCurrentRecordHash(), "nonce_id_2");
        r2.setRecordId("duplicate-id"); // Duplicate ID!
        r2.setCurrentRecordHash(HashUtil.sha256(validator.computeCanonicalPayload(r2)));
        r2.setHmacValue(CryptoUtil.computeHmac(validator.computeCanonicalPayload(r2), hmacKey));

        InferenceValidator.VerificationBatchResult result = validator.verifyRecords(List.of(r1, r2), hmacKey);
        assertEquals(1, result.failedCount);
        assertTrue(result.hasCategory(InferenceValidator.FailureCategory.DUPLICATE_RECORD_ID));
        assertEquals(VerificationStatus.FAILED, result.verifications.get(1).getOverallStatus());
    }

    @Test
    void testDuplicateSequenceNumberRejected() {
        InferenceRecord r1 = createSampleRecord(1, "GENESIS", "nonce_seq_1");
        InferenceRecord r2 = createSampleRecord(1, r1.getCurrentRecordHash(), "nonce_seq_2"); // Duplicate sequence 1!
        r2.setRecordId("rec_2");
        r2.setCurrentRecordHash(HashUtil.sha256(validator.computeCanonicalPayload(r2)));
        r2.setHmacValue(CryptoUtil.computeHmac(validator.computeCanonicalPayload(r2), hmacKey));

        InferenceValidator.VerificationBatchResult result = validator.verifyRecords(List.of(r1, r2), hmacKey);
        assertEquals(1, result.failedCount);
        assertTrue(result.hasCategory(InferenceValidator.FailureCategory.INVALID_SEQUENCE));
    }

    @Test
    void testUnsupportedSchemaVersionRejected() {
        InferenceRecord r = createSampleRecord(1, "GENESIS", "nonce_schema");
        r.setSchemaVersion("V99_UNKNOWN"); // Unsupported schema!

        InferenceValidator.VerificationBatchResult result = validator.verifyRecords(List.of(r), hmacKey);
        assertEquals(1, result.failedCount);
        assertTrue(result.hasCategory(InferenceValidator.FailureCategory.UNSUPPORTED_SCHEMA));
    }

    @Test
    void testBrokenHashChainPlusReplayIdentifiesTampering() {
        InferenceRecord r1 = createSampleRecord(1, "GENESIS", "reused_nonce");
        InferenceRecord r2 = createSampleRecord(2, "CORRUPT_PREV_HASH", "reused_nonce");

        InferenceValidator.VerificationBatchResult result = validator.verifyRecords(List.of(r1, r2), hmacKey);
        assertTrue(result.hasCryptographicTampering());
        assertTrue(result.hasCategory(InferenceValidator.FailureCategory.BROKEN_CHAIN));
        assertTrue(result.hasCategory(InferenceValidator.FailureCategory.REPLAY_DETECTED));
    }

    private InferenceRecord createSampleRecord(int seq, String prevHash, String nonce) {
        InferenceRecord r = new InferenceRecord();
        r.setRecordId("rec_" + seq);
        r.setSequenceNumber(seq);
        r.setTimestamp(LocalDateTime.now().minusMinutes(10 - seq).toString());
        r.setModelIdentifier("demo_vision_model");
        r.setModelHash(HashUtil.sha256("model" + seq));
        r.setInputImageHash(HashUtil.sha256("input" + seq));
        r.setConfigHash(HashUtil.sha256("config" + seq));
        r.setOutputHash(HashUtil.sha256("output" + seq));
        r.setNonce(nonce);
        r.setPreviousRecordHash(prevHash);

        String canonicalPayload = validator.computeCanonicalPayload(r);
        r.setCurrentRecordHash(HashUtil.sha256(canonicalPayload));
        r.setHmacValue(CryptoUtil.computeHmac(canonicalPayload, hmacKey));
        return r;
    }
}
