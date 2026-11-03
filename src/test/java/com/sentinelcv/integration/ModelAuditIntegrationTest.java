package com.sentinelcv.integration;

import com.sentinelcv.entity.MlModel;
import com.sentinelcv.entity.ModelAudit;
import com.sentinelcv.entity.SecurityFinding;
import com.sentinelcv.enums.AuditStatus;
import com.sentinelcv.enums.ModelFormat;
import com.sentinelcv.repository.MlModelRepository;
import com.sentinelcv.repository.ModelAuditRepository;
import com.sentinelcv.repository.ModelFindingRepository;
import com.sentinelcv.service.ModelAuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ModelAuditIntegrationTest {

    @Autowired
    private MlModelRepository modelRepository;

    @Autowired
    private ModelAuditRepository auditRepository;

    @Autowired
    private ModelFindingRepository findingRepository;

    @Autowired
    private ModelAuditService modelAuditService;

    @Test
    void testTorchScriptUnavailablePathway(@TempDir Path tempDir) throws IOException {
        // Create dummy PyTorch model file
        File ptFile = tempDir.resolve("sample_checkpoint.pt").toFile();
        Files.write(ptFile.toPath(), new byte[]{0x50, 0x4b, 0x03, 0x04}); // ZIP header typical of PyTorch .pt

        MlModel model = new MlModel("TorchScript Sample Model", ModelFormat.TORCHSCRIPT, ptFile.getAbsolutePath());
        model = modelRepository.save(model);

        ModelAudit audit = new ModelAudit(model.getId());
        audit = auditRepository.save(audit);

        // Execute audit
        ModelAudit completed = modelAuditService.executeAudit(audit.getId());

        assertNotNull(completed);
        assertEquals(AuditStatus.COMPLETED, completed.getStatus());
        assertNotNull(completed.getAssessmentDecision());

        // Verify honest UNAVAILABLE finding was produced
        List<SecurityFinding> findings = findingRepository.findByAuditId(completed.getId());
        assertFalse(findings.isEmpty());
        assertTrue(findings.stream().anyMatch(f ->
                f.getDescription().contains("UNAVAILABLE") || f.getCategory().contains("Behavioral")
        ), "Should report an honest UNAVAILABLE finding rather than fabricating fake inference results");
    }
}
