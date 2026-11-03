package com.sentinelcv.integration;

import com.sentinelcv.inference.InferenceValidator;
import com.sentinelcv.service.InferenceProvenanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class InferenceProvenanceIntegrationTest {

    @Autowired
    private InferenceProvenanceService provenanceService;

    @Test
    void testVerifyAllInferenceRecords() {
        InferenceValidator.VerificationBatchResult result = provenanceService.verifyAllRecords();
        assertNotNull(result);
        assertTrue(result.totalRecordsChecked >= 3, "At least 3 initialized records should be present");
        assertEquals(result.totalRecordsChecked, result.verifiedCount);
        assertEquals(0, result.failedCount);
        assertEquals(0, result.brokenChainLinks);
        assertEquals(0, result.replayAttempts);
    }
}
