package com.sentinelcv.model;

import com.sentinelcv.entity.MlModel;
import com.sentinelcv.entity.ModelAudit;
import com.sentinelcv.enums.AssessmentDecision;
import com.sentinelcv.enums.ModelFormat;
import com.sentinelcv.repository.AuditHistoryRepository;
import com.sentinelcv.repository.MlModelRepository;
import com.sentinelcv.repository.ModelAuditRepository;
import com.sentinelcv.repository.ModelFindingRepository;
import com.sentinelcv.repository.SecurityFindingRepository;
import com.sentinelcv.service.ModelAuditService;
import com.sentinelcv.util.FixtureGenerator;
import com.sentinelcv.util.HashUtil;
import com.sentinelcv.entity.SecurityFinding;
import com.sentinelcv.enums.FindingSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class RealOnnxModelAuditTest {

    @Autowired
    private OnnxModelLoader onnxModelLoader;

    @Autowired
    private TorchScriptModelLoader torchScriptModelLoader;

    @Autowired
    private BehavioralAnalyzer behavioralAnalyzer;

    @Autowired
    private TriggerAnalyzer triggerAnalyzer;

    @Autowired
    private ModelAuditService modelAuditService;

    @Autowired
    private MlModelRepository modelRepository;

    @Autowired
    private ModelAuditRepository modelAuditRepository;

    @Autowired
    private ModelFingerprintService fingerprintService;

    @Autowired
    private ModelFindingRepository modelFindingRepository;

    @Autowired
    private SecurityFindingRepository securityFindingRepository;

    @Autowired
    private AuditHistoryRepository auditHistoryRepository;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private ModelAuditService createCustomAuditService(List<ModelLoader> loaders,
                                                       BehavioralAnalyzer customBehavioral,
                                                       TriggerAnalyzer customTrigger) {
        return new ModelAuditService(
                loaders != null ? loaders : List.of(onnxModelLoader, torchScriptModelLoader),
                fingerprintService,
                customBehavioral != null ? customBehavioral : behavioralAnalyzer,
                customTrigger != null ? customTrigger : triggerAnalyzer,
                modelRepository,
                modelAuditRepository,
                modelFindingRepository,
                securityFindingRepository,
                auditHistoryRepository,
                objectMapper
        );
    }

    @Test
    void testRealOnnxLoadingAndMetadata(@TempDir Path tempDir) throws Exception {
        File onnxFile = tempDir.resolve("real_vision_model.onnx").toFile();
        FixtureGenerator.createSampleOnnxModel(onnxFile, new long[]{1, 3, 224, 224}, "Relu");

        assertTrue(onnxModelLoader.canLoad(onnxFile));
        ModelMetadata metadata = onnxModelLoader.inspectMetadata(onnxFile);

        assertTrue(metadata.inferenceSupported);
        assertEquals("ONNX", metadata.format);
        assertFalse(metadata.inputTensors.isEmpty());
        assertFalse(metadata.outputTensors.isEmpty());

        ModelMetadata.TensorInfo inInfo = metadata.inputTensors.get(0);
        assertEquals("input", inInfo.name);
        assertArrayEquals(new long[]{1, 3, 224, 224}, inInfo.shape);

        ModelMetadata.TensorInfo outInfo = metadata.outputTensors.get(0);
        assertEquals("output", outInfo.name);
        assertArrayEquals(new long[]{1, 3, 224, 224}, outInfo.shape);
    }

    @Test
    void testRealOnnxInferenceAndDeterminism(@TempDir Path tempDir) throws Exception {
        File onnxFile = tempDir.resolve("real_inference_model.onnx").toFile();
        FixtureGenerator.createSampleOnnxModel(onnxFile, new long[]{1, 3, 224, 224}, "Relu");

        long[] shape = new long[]{1, 3, 224, 224};
        int total = BehavioralAnalyzer.computeTotalElements(shape);
        float[] input = BehavioralAnalyzer.generateStructuredInput(shape, total);

        float[] out1 = onnxModelLoader.runInference(onnxFile, input, shape);
        float[] out2 = onnxModelLoader.runInference(onnxFile, input, shape);

        assertNotNull(out1);
        assertEquals(total, out1.length, "Flattened output length must match tensor dimensions");
        assertArrayEquals(out1, out2, 1e-5f, "Repeated inference on identical inputs must be deterministic");
    }

    @Test
    void testBehavioralAnalysisAndSpatialPatchPerturbation(@TempDir Path tempDir) throws Exception {
        File onnxFile = tempDir.resolve("spatial_patch_model.onnx").toFile();
        FixtureGenerator.createSampleOnnxModel(onnxFile, new long[]{1, 3, 224, 224}, "Relu");

        ModelMetadata metadata = onnxModelLoader.inspectMetadata(onnxFile);
        BehavioralAnalyzer.BehavioralResult behavioral =
                behavioralAnalyzer.analyzeBehavior(onnxModelLoader, onnxFile, metadata, 999L);

        assertTrue(behavioral.inferenceExecuted);
        assertTrue(behavioral.deterministic);
        assertEquals(0.0, behavioral.maxOutputDeviation, 1e-4);

        TriggerAnalyzer.TriggerAnalysisReport triggerReport =
                triggerAnalyzer.analyzePerturbations(onnxModelLoader, onnxFile, metadata, behavioral, 999L);

        assertTrue(triggerReport.executed);
        assertEquals(2, triggerReport.tests.size());
        TriggerAnalyzer.PerturbationTestResult patchTest = triggerReport.tests.get(0);
        assertTrue(patchTest.parameters.contains("Spatial"), "Patch test must use true multi-channel spatial patch");
    }

    @Test
    void testUnsupportedTorchScriptModelProducesReview(@TempDir Path tempDir) throws Exception {
        File torchModel = tempDir.resolve("unsupported_model.pt").toFile();
        try (FileOutputStream fos = new FileOutputStream(torchModel)) {
            fos.write(new byte[]{0x50, 0x4B, 0x03, 0x04, 0x14, 0x00}); // zip header mock pt
        }

        MlModel model = new MlModel("TorchScript Mock", ModelFormat.TORCHSCRIPT, torchModel.getAbsolutePath());
        model.setSha256Hash(HashUtil.sha256(torchModel));
        model.setFileSizeBytes(torchModel.length());
        model = modelRepository.save(model);

        try {
            ModelAudit audit = new ModelAudit(model.getId());
            audit = modelAuditRepository.save(audit);

            ModelAudit completedAudit = modelAuditService.executeAudit(audit.getId());
            assertNotNull(completedAudit);
            // Unsupported model where required behavioral checks cannot be executed MUST produce REVIEW
            assertEquals(AssessmentDecision.REVIEW, completedAudit.getAssessmentDecision(),
                    "Unsupported model runtime must mandate REVIEW, never falsely produce ACCEPT");
        } finally {
            if (model.getId() != null) {
                modelRepository.deleteById(model.getId());
            }
        }
    }

    @Test
    void testBaselineInferenceSucceedsTriggerAnalysisFailsProducesReview(@TempDir Path tempDir) throws Exception {
        File onnxFile = tempDir.resolve("trigger_fail.onnx").toFile();
        FixtureGenerator.createSampleOnnxModel(onnxFile, new long[]{1, 3, 224, 224}, "Relu");

        MlModel model = new MlModel("Trigger Fail Model", ModelFormat.ONNX, onnxFile.getAbsolutePath());
        model.setSha256Hash(HashUtil.sha256(onnxFile));
        model.setFileSizeBytes(onnxFile.length());
        model = modelRepository.save(model);

        try {
            ModelAudit audit = modelAuditRepository.save(new ModelAudit(model.getId()));

            TriggerAnalyzer triggerMock = Mockito.mock(TriggerAnalyzer.class);
            TriggerAnalyzer.TriggerAnalysisReport failedReport = new TriggerAnalyzer.TriggerAnalysisReport();
            failedReport.executed = false; // Check failed or was not executed
            failedReport.statusDescription = "Trigger analysis failed execution";
            when(triggerMock.analyzePerturbations(any(), any(), any(), any(), any())).thenReturn(failedReport);

            ModelAuditService auditService = createCustomAuditService(null, null, triggerMock);
            ModelAudit result = auditService.executeAudit(audit.getId());

            assertNotNull(result);
            assertEquals(AssessmentDecision.REVIEW, result.getAssessmentDecision(),
                    "When baseline inference succeeds but trigger analysis fails/is unexecuted, decision must be REVIEW");
        } finally {
            if (model.getId() != null) {
                modelRepository.deleteById(model.getId());
            }
        }
    }

    @Test
    void testBehavioralAnalysisFailsProducesReview(@TempDir Path tempDir) throws Exception {
        File onnxFile = tempDir.resolve("behavior_fail.onnx").toFile();
        FixtureGenerator.createSampleOnnxModel(onnxFile, new long[]{1, 3, 224, 224}, "Relu");

        MlModel model = new MlModel("Behavior Fail Model", ModelFormat.ONNX, onnxFile.getAbsolutePath());
        model.setSha256Hash(HashUtil.sha256(onnxFile));
        model.setFileSizeBytes(onnxFile.length());
        model = modelRepository.save(model);

        try {
            ModelAudit audit = modelAuditRepository.save(new ModelAudit(model.getId()));

            BehavioralAnalyzer behavioralMock = Mockito.mock(BehavioralAnalyzer.class);
            BehavioralAnalyzer.BehavioralResult failedBehavior = new BehavioralAnalyzer.BehavioralResult();
            failedBehavior.inferenceExecuted = false; // Behavioral inference execution failed
            when(behavioralMock.analyzeBehavior(any(), any(), any(), any())).thenReturn(failedBehavior);

            ModelAuditService auditService = createCustomAuditService(null, behavioralMock, null);
            ModelAudit result = auditService.executeAudit(audit.getId());

            assertNotNull(result);
            assertEquals(AssessmentDecision.REVIEW, result.getAssessmentDecision(),
                    "When behavioral analysis fails, decision must be REVIEW");
        } finally {
            if (model.getId() != null) {
                modelRepository.deleteById(model.getId());
            }
        }
    }

    @Test
    void testOnnxInferenceUnavailableProducesReview(@TempDir Path tempDir) throws Exception {
        File onnxFile = tempDir.resolve("unsupported_onnx.onnx").toFile();
        FixtureGenerator.createSampleOnnxModel(onnxFile, new long[]{1, 3, 224, 224}, "Relu");

        MlModel model = new MlModel("Unsupported ONNX Model", ModelFormat.ONNX, onnxFile.getAbsolutePath());
        model.setSha256Hash(HashUtil.sha256(onnxFile));
        model.setFileSizeBytes(onnxFile.length());
        model = modelRepository.save(model);

        try {
            ModelAudit audit = modelAuditRepository.save(new ModelAudit(model.getId()));

            ModelLoader loaderMock = Mockito.mock(ModelLoader.class);
            when(loaderMock.canLoad(any())).thenReturn(true);
            ModelMetadata metadata = new ModelMetadata();
            metadata.format = "ONNX";
            metadata.inferenceSupported = false; // Inference unavailable
            when(loaderMock.inspectMetadata(any())).thenReturn(metadata);

            ModelAuditService auditService = createCustomAuditService(List.of(loaderMock), null, null);
            ModelAudit result = auditService.executeAudit(audit.getId());

            assertNotNull(result);
            assertEquals(AssessmentDecision.REVIEW, result.getAssessmentDecision(),
                    "When ONNX inference is unavailable, decision must be REVIEW");
        } finally {
            if (model.getId() != null) {
                modelRepository.deleteById(model.getId());
            }
        }
    }

    @Test
    void testCriticalFindingProducesQuarantine(@TempDir Path tempDir) throws Exception {
        File onnxFile = tempDir.resolve("crit_finding.onnx").toFile();
        FixtureGenerator.createSampleOnnxModel(onnxFile, new long[]{1, 3, 224, 224}, "Relu");

        MlModel model = new MlModel("Critical Model", ModelFormat.ONNX, onnxFile.getAbsolutePath());
        model.setSha256Hash(HashUtil.sha256(onnxFile));
        model.setFileSizeBytes(onnxFile.length());
        model = modelRepository.save(model);

        try {
            ModelAudit audit = modelAuditRepository.save(new ModelAudit(model.getId()));

            BehavioralAnalyzer behavioralMock = Mockito.mock(BehavioralAnalyzer.class);
            BehavioralAnalyzer.BehavioralResult behaviorResult = new BehavioralAnalyzer.BehavioralResult();
            behaviorResult.inferenceExecuted = true;
            behaviorResult.findings.add(SecurityFinding.builder()
                    .findingId("FIND-CRITICAL-TEST")
                    .module("MODEL")
                    .category("Integrity")
                    .description("Critical backdoor detected")
                    .severity(FindingSeverity.CRITICAL)
                    .confidence(1.0)
                    .affectedAsset(onnxFile.getName())
                    .sourceAuditId(audit.getId())
                    .sourceAuditType("MODEL")
                    .build());
            when(behavioralMock.analyzeBehavior(any(), any(), any(), any())).thenReturn(behaviorResult);

            ModelAuditService auditService = createCustomAuditService(null, behavioralMock, null);
            ModelAudit result = auditService.executeAudit(audit.getId());

            assertNotNull(result);
            assertEquals(AssessmentDecision.QUARANTINE, result.getAssessmentDecision(),
                    "Critical finding must mandate QUARANTINE");
        } finally {
            if (model.getId() != null) {
                modelRepository.deleteById(model.getId());
            }
        }
    }

    @Test
    void testHighFindingProducesReview(@TempDir Path tempDir) throws Exception {
        File onnxFile = tempDir.resolve("high_finding.onnx").toFile();
        FixtureGenerator.createSampleOnnxModel(onnxFile, new long[]{1, 3, 224, 224}, "Relu");

        MlModel model = new MlModel("High Finding Model", ModelFormat.ONNX, onnxFile.getAbsolutePath());
        model.setSha256Hash(HashUtil.sha256(onnxFile));
        model.setFileSizeBytes(onnxFile.length());
        model = modelRepository.save(model);

        try {
            ModelAudit audit = modelAuditRepository.save(new ModelAudit(model.getId()));

            BehavioralAnalyzer behavioralMock = Mockito.mock(BehavioralAnalyzer.class);
            BehavioralAnalyzer.BehavioralResult behaviorResult = new BehavioralAnalyzer.BehavioralResult();
            behaviorResult.inferenceExecuted = true;
            behaviorResult.findings.add(SecurityFinding.builder()
                    .findingId("FIND-HIGH-TEST")
                    .module("MODEL")
                    .category("Integrity")
                    .description("High risk anomaly detected")
                    .severity(FindingSeverity.HIGH)
                    .confidence(0.9)
                    .affectedAsset(onnxFile.getName())
                    .sourceAuditId(audit.getId())
                    .sourceAuditType("MODEL")
                    .build());
            when(behavioralMock.analyzeBehavior(any(), any(), any(), any())).thenReturn(behaviorResult);

            ModelAuditService auditService = createCustomAuditService(null, behavioralMock, null);
            ModelAudit result = auditService.executeAudit(audit.getId());

            assertNotNull(result);
            assertEquals(AssessmentDecision.REVIEW, result.getAssessmentDecision(),
                    "High finding must mandate REVIEW");
        } finally {
            if (model.getId() != null) {
                modelRepository.deleteById(model.getId());
            }
        }
    }

    @Test
    void testAllMandatoryChecksExecuteSuccessfullyProducesAccept(@TempDir Path tempDir) throws Exception {
        File onnxFile = tempDir.resolve("accept_clean_model.onnx").toFile();
        FixtureGenerator.createSampleOnnxModel(onnxFile, new long[]{1, 3, 224, 224}, "Relu");

        MlModel model = new MlModel("Clean Model", ModelFormat.ONNX, onnxFile.getAbsolutePath());
        model.setSha256Hash(HashUtil.sha256(onnxFile));
        model.setFileSizeBytes(onnxFile.length());
        model = modelRepository.save(model);

        try {
            ModelAudit audit = modelAuditRepository.save(new ModelAudit(model.getId()));

            // Audit using the real application services
            ModelAudit result = modelAuditService.executeAudit(audit.getId());

            assertNotNull(result);
            assertEquals(AssessmentDecision.ACCEPT, result.getAssessmentDecision(),
                    "When all mandatory checks execute successfully with no adverse findings, decision must be ACCEPT");
        } finally {
            if (model.getId() != null) {
                modelRepository.deleteById(model.getId());
            }
        }
    }
}
