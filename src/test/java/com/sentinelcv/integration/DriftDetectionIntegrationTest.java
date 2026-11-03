package com.sentinelcv.integration;

import com.sentinelcv.entity.Dataset;
import com.sentinelcv.entity.DriftAudit;
import com.sentinelcv.repository.DatasetRepository;
import com.sentinelcv.service.DriftDetectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DriftDetectionIntegrationTest {

    @Autowired
    private DatasetRepository datasetRepository;

    @Autowired
    private DriftDetectionService driftDetectionService;

    @Test
    void testExecuteDriftAuditBetweenDatasets() {
        List<Dataset> datasets = datasetRepository.findAll().stream()
                .filter(d -> new File(d.getPath()).exists())
                .toList();
        assertTrue(datasets.size() >= 2, "Expected at least 2 demo datasets for drift testing");

        Dataset ref = datasets.get(0);
        Dataset eval = datasets.get(1);

        DriftAudit driftAudit = driftDetectionService.executeDriftAudit(ref.getId(), eval.getId());

        assertNotNull(driftAudit);
        assertEquals("COMPLETED", driftAudit.getStatus());
        assertNotNull(driftAudit.getSeverity());
        assertNotNull(driftAudit.getMetricsJson());
        assertTrue(driftAudit.getOverallDriftScore() >= 0.0);
    }
}
