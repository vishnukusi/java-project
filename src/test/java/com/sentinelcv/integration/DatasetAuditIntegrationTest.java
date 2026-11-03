package com.sentinelcv.integration;

import com.sentinelcv.entity.Dataset;
import com.sentinelcv.entity.DatasetAudit;
import com.sentinelcv.entity.SecurityFinding;
import com.sentinelcv.enums.AuditStatus;
import com.sentinelcv.repository.DatasetAuditRepository;
import com.sentinelcv.repository.DatasetFindingRepository;
import com.sentinelcv.repository.DatasetRepository;
import com.sentinelcv.service.DatasetAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DatasetAuditIntegrationTest {

    @Autowired
    private DatasetRepository datasetRepository;

    @Autowired
    private DatasetAuditRepository auditRepository;

    @Autowired
    private DatasetFindingRepository findingRepository;

    @Autowired
    private DatasetAuditService auditService;

    @Test
    void testEndToEndDatasetAudit() {
        List<Dataset> datasets = datasetRepository.findAll().stream()
                .filter(d -> new File(d.getPath()).exists())
                .toList();
        assertFalse(datasets.isEmpty(), "Sample datasets should be initialized and exist on disk");

        Dataset dataset = datasets.get(0);
        DatasetAudit audit = new DatasetAudit(dataset.getId());
        audit = auditRepository.save(audit);

        // Execute audit synchronously
        DatasetAudit completedAudit = auditService.executeAudit(audit.getId());

        assertNotNull(completedAudit);
        assertEquals(AuditStatus.COMPLETED, completedAudit.getStatus(), () -> "Audit failed with: " + completedAudit.getErrorMessage());
        assertNotNull(completedAudit.getAssessmentDecision());
        assertTrue(completedAudit.getIntegrityScore() >= 0.0 && completedAudit.getIntegrityScore() <= 100.0);

        List<SecurityFinding> findings = findingRepository.findByAuditId(completedAudit.getId());
        assertNotNull(findings);
        assertEquals(completedAudit.getTotalFindings(), findings.size());
    }
}
