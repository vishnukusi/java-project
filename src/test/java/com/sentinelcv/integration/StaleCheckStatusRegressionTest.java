package com.sentinelcv.integration;

import com.sentinelcv.entity.AuditReport;
import com.sentinelcv.entity.Dataset;
import com.sentinelcv.entity.DatasetAudit;
import com.sentinelcv.entity.InferenceRecord;
import com.sentinelcv.entity.MlModel;
import com.sentinelcv.entity.ModelAudit;
import com.sentinelcv.enums.AssessmentDecision;
import com.sentinelcv.enums.AuditStatus;
import com.sentinelcv.enums.CheckExecutionStatus;
import com.sentinelcv.enums.DatasetFormat;
import com.sentinelcv.enums.ModelFormat;
import com.sentinelcv.repository.DatasetAuditRepository;
import com.sentinelcv.repository.DatasetRepository;
import com.sentinelcv.repository.InferenceRecordRepository;
import com.sentinelcv.repository.MandatoryCheckStatusRepository;
import com.sentinelcv.repository.MlModelRepository;
import com.sentinelcv.repository.ModelAuditRepository;
import com.sentinelcv.repository.SecurityFindingRepository;
import com.sentinelcv.service.ReportService;
import com.sentinelcv.service.RiskAssessmentService;
import com.sentinelcv.util.FixtureGenerator;
import com.sentinelcv.util.HashUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class StaleCheckStatusRegressionTest {

    @Autowired
    private RiskAssessmentService riskAssessmentService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private MandatoryCheckStatusRepository checkStatusRepository;

    @Autowired
    private MlModelRepository modelRepository;

    @Autowired
    private ModelAuditRepository modelAuditRepository;

    @Autowired
    private DatasetRepository datasetRepository;

    @Autowired
    private DatasetAuditRepository datasetAuditRepository;

    @Autowired
    private InferenceRecordRepository recordRepository;

    @Autowired
    private SecurityFindingRepository findingRepository;

    @BeforeEach
    @AfterEach
    void cleanState() {
        findingRepository.deleteAll();
        checkStatusRepository.deleteAll();
        riskAssessmentService.invalidateProvenanceCache();
    }

    @Test
    void testFreshApplicationWithNoChecksProducesReview() {
        // When check status repository is empty, assessSystemRisk must produce REVIEW, never false ACCEPT
        RiskAssessmentService.SystemRiskAssessment assessment = riskAssessmentService.assessSystemRisk();
        assertEquals(AssessmentDecision.REVIEW, assessment.overallDecision,
                "Fresh application with no executed checks must produce REVIEW");
        assertTrue(assessment.notExecutedChecksCount >= 4,
                "All 4 mandatory checks must be classified NOT_EXECUTED");
    }

    @Test
    void testAllFourChecksPassedForCurrentAssetVersionsProducesAccept(@TempDir Path tempDir) throws Exception {
        // Setup genuine model
        File onnxFile = tempDir.resolve("test_model.onnx").toFile();
        FixtureGenerator.createSampleOnnxModel(onnxFile, new long[]{1, 3, 224, 224}, "Relu");
        MlModel model = new MlModel("Valid Model", ModelFormat.ONNX, onnxFile.getAbsolutePath());
        model.setSha256Hash(HashUtil.sha256(onnxFile));
        model = modelRepository.save(model);

        ModelAudit mAudit = new ModelAudit(model.getId());
        mAudit.setStatus(AuditStatus.COMPLETED);
        mAudit = modelAuditRepository.save(mAudit);

        // Setup genuine dataset
        File cocoDir = FixtureGenerator.createSampleCocoDataset(tempDir, "valid_coco");
        Dataset dataset = new Dataset("Valid Dataset", DatasetFormat.COCO, cocoDir.getAbsolutePath());
        dataset.setSha256Hash(HashUtil.calculateDatasetDirectoryHash(cocoDir));
        dataset = datasetRepository.save(dataset);

        DatasetAudit dAudit = new DatasetAudit(dataset.getId());
        dAudit.setStatus(AuditStatus.COMPLETED);
        dAudit = datasetAuditRepository.save(dAudit);

        // Record check statuses bound to exact asset versions
        riskAssessmentService.recordCheckStatus("model.baseline_inference", mAudit.getId(),
                String.valueOf(model.getId()), model.getSha256Hash(), CheckExecutionStatus.PASSED, "Baseline ok");
        riskAssessmentService.recordCheckStatus("model.trigger_perturbation", mAudit.getId(),
                String.valueOf(model.getId()), model.getSha256Hash(), CheckExecutionStatus.PASSED, "Perturbation ok");
        riskAssessmentService.recordCheckStatus("dataset.integrity", dAudit.getId(),
                String.valueOf(dataset.getId()), dataset.getSha256Hash(), CheckExecutionStatus.PASSED, "Integrity ok");

        // Record provenance
        String lastHash = recordRepository.findAllOrderedBySequence().isEmpty() ? "dummy_hash" :
                recordRepository.findAllOrderedBySequence().get(recordRepository.findAllOrderedBySequence().size() - 1).getCurrentRecordHash();
        riskAssessmentService.recordCheckStatus("provenance.verification", null,
                "provenance-chain", lastHash, CheckExecutionStatus.PASSED, "Provenance chain ok");

        RiskAssessmentService.SystemRiskAssessment assessment = riskAssessmentService.assessSystemRisk();
        assertEquals(AssessmentDecision.ACCEPT, assessment.overallDecision,
                "When all mandatory checks explicitly passed for current asset versions, system is eligible for ACCEPT");
        assertEquals(4, assessment.passedChecksCount);
    }

    @Test
    void testModelContentChangeAfterPassingAuditProducesReview(@TempDir Path tempDir) throws Exception {
        File onnxFile = tempDir.resolve("model_change.onnx").toFile();
        FixtureGenerator.createSampleOnnxModel(onnxFile, new long[]{1, 3, 224, 224}, "Relu");
        MlModel model = new MlModel("Changing Model", ModelFormat.ONNX, onnxFile.getAbsolutePath());
        model.setSha256Hash(HashUtil.sha256(onnxFile));
        model = modelRepository.save(model);

        ModelAudit mAudit = new ModelAudit(model.getId());
        mAudit.setStatus(AuditStatus.COMPLETED);
        mAudit = modelAuditRepository.save(mAudit);

        // Record PASSED with the initial hash
        riskAssessmentService.recordCheckStatus("model.baseline_inference", mAudit.getId(),
                String.valueOf(model.getId()), model.getSha256Hash(), CheckExecutionStatus.PASSED, "OK");
        riskAssessmentService.recordCheckStatus("model.trigger_perturbation", mAudit.getId(),
                String.valueOf(model.getId()), model.getSha256Hash(), CheckExecutionStatus.PASSED, "OK");

        // Now modify model hash in DB to simulate new weights/version
        model.setSha256Hash("MODIFIED_HASH_56789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0");
        modelRepository.save(model);

        // Assess system risk: model check MUST be invalidated to NOT_EXECUTED, producing REVIEW
        RiskAssessmentService.SystemRiskAssessment assessment = riskAssessmentService.assessSystemRisk();
        assertEquals(AssessmentDecision.REVIEW, assessment.overallDecision,
                "Modified model hash must invalidate previous PASSED status and mandate REVIEW");
        assertEquals(CheckExecutionStatus.NOT_EXECUTED, assessment.checkStatuses.get("model.baseline_inference"));
        assertEquals(CheckExecutionStatus.NOT_EXECUTED, assessment.checkStatuses.get("model.trigger_perturbation"));
    }

    @Test
    void testDatasetChangeAfterPassingAuditProducesReview(@TempDir Path tempDir) throws Exception {
        File cocoDir = FixtureGenerator.createSampleCocoDataset(tempDir, "ds_change_coco");
        Dataset dataset = new Dataset("Changing Dataset", DatasetFormat.COCO, cocoDir.getAbsolutePath());
        dataset.setSha256Hash(HashUtil.calculateDatasetDirectoryHash(cocoDir));
        dataset = datasetRepository.save(dataset);

        DatasetAudit dAudit = new DatasetAudit(dataset.getId());
        dAudit.setStatus(AuditStatus.COMPLETED);
        dAudit = datasetAuditRepository.save(dAudit);

        riskAssessmentService.recordCheckStatus("dataset.integrity", dAudit.getId(),
                String.valueOf(dataset.getId()), dataset.getSha256Hash(), CheckExecutionStatus.PASSED, "OK");

        // Invalidate by changing dataset hash
        dataset.setSha256Hash("CHANGED_DATASET_HASH_5678");
        datasetRepository.save(dataset);

        RiskAssessmentService.SystemRiskAssessment assessment = riskAssessmentService.assessSystemRisk();
        assertEquals(AssessmentDecision.REVIEW, assessment.overallDecision);
        assertEquals(CheckExecutionStatus.NOT_EXECUTED, assessment.checkStatuses.get("dataset.integrity"));
    }

    @Test
    void testInterruptedAuditInvalidatesPreviousPassedCheck(@TempDir Path tempDir) throws Exception {
        File onnxFile = tempDir.resolve("model_interrupted.onnx").toFile();
        FixtureGenerator.createSampleOnnxModel(onnxFile, new long[]{1, 3, 224, 224}, "Relu");
        MlModel model = new MlModel("Interrupted Model", ModelFormat.ONNX, onnxFile.getAbsolutePath());
        model.setSha256Hash(HashUtil.sha256(onnxFile));
        model = modelRepository.save(model);

        // Previous passing audit 1
        ModelAudit audit1 = new ModelAudit(model.getId());
        audit1.setStatus(AuditStatus.COMPLETED);
        audit1 = modelAuditRepository.save(audit1);

        riskAssessmentService.recordCheckStatus("model.baseline_inference", audit1.getId(),
                String.valueOf(model.getId()), model.getSha256Hash(), CheckExecutionStatus.PASSED, "Audit 1 passed");

        // Subsequent audit 2 was started but interrupted (status = RUNNING)
        ModelAudit audit2 = new ModelAudit(model.getId());
        audit2.setStatus(AuditStatus.RUNNING);
        modelAuditRepository.save(audit2);

        // Assess system risk: active/interrupted newer audit must invalidate PASSED check to NOT_EXECUTED
        CheckExecutionStatus status = riskAssessmentService.getCheckStatus("model.baseline_inference");
        assertEquals(CheckExecutionStatus.NOT_EXECUTED, status,
                "Newer RUNNING or interrupted audit must prevent older PASSED result from being accepted");

        RiskAssessmentService.SystemRiskAssessment assessment = riskAssessmentService.assessSystemRisk();
        assertEquals(AssessmentDecision.REVIEW, assessment.overallDecision);
    }

    @Test
    void testOldPassedResultCannotOverrideNewerFailedAudit(@TempDir Path tempDir) throws Exception {
        File onnxFile = tempDir.resolve("model_newer_failed.onnx").toFile();
        FixtureGenerator.createSampleOnnxModel(onnxFile, new long[]{1, 3, 224, 224}, "Relu");
        MlModel model = new MlModel("Failed Audit Model", ModelFormat.ONNX, onnxFile.getAbsolutePath());
        model.setSha256Hash(HashUtil.sha256(onnxFile));
        model = modelRepository.save(model);

        ModelAudit audit1 = new ModelAudit(model.getId());
        audit1.setStatus(AuditStatus.COMPLETED);
        audit1 = modelAuditRepository.save(audit1);

        riskAssessmentService.recordCheckStatus("model.baseline_inference", audit1.getId(),
                String.valueOf(model.getId()), model.getSha256Hash(), CheckExecutionStatus.PASSED, "Audit 1 passed");

        // Subsequent audit 2 FAILED
        ModelAudit audit2 = new ModelAudit(model.getId());
        audit2.setStatus(AuditStatus.FAILED);
        audit2.setErrorMessage("Crash during tensor inspection");
        modelAuditRepository.save(audit2);

        // Check status must resolve to FAILED, not PASSED
        CheckExecutionStatus status = riskAssessmentService.getCheckStatus("model.baseline_inference");
        assertEquals(CheckExecutionStatus.FAILED, status,
                "Newer FAILED audit must override older PASSED audit result");
    }

    @Test
    void testDashboardAndReportShowSameDecisionAsRiskAssessmentService() throws Exception {
        RiskAssessmentService.SystemRiskAssessment assessment = riskAssessmentService.assessSystemRisk();

        AuditReport report = reportService.generateReport("Authoritative Parity Test", "JSON");
        assertNotNull(report);
        assertEquals(assessment.overallDecision.name(), report.getAssessmentDecision(),
                "Report decision must strictly match RiskAssessmentService decision");

        new File(report.getFilePath()).delete();
    }

    @Test
    void testModelPhysicalFileModificationOnDiskProducesReview(@TempDir Path tempDir) throws Exception {
        File onnxFile = tempDir.resolve("physical_tamper.onnx").toFile();
        FixtureGenerator.createSampleOnnxModel(onnxFile, new long[]{1, 3, 224, 224}, "Relu");
        MlModel model = new MlModel("Tampered Model", ModelFormat.ONNX, onnxFile.getAbsolutePath());
        model.setSha256Hash(HashUtil.sha256(onnxFile));
        model = modelRepository.save(model);

        ModelAudit mAudit = new ModelAudit(model.getId());
        mAudit.setStatus(AuditStatus.COMPLETED);
        mAudit = modelAuditRepository.save(mAudit);

        riskAssessmentService.recordCheckStatus("model.baseline_inference", mAudit.getId(),
                String.valueOf(model.getId()), model.getSha256Hash(), CheckExecutionStatus.PASSED, "Initial pass");

        // Physical on-disk modification without updating SQLite metadata
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(onnxFile, true)) {
            fos.write("MALICIOUS_WEIGHT_BYTES_2026".getBytes());
        }

        CheckExecutionStatus status = riskAssessmentService.getCheckStatus("model.baseline_inference");
        assertEquals(CheckExecutionStatus.NOT_EXECUTED, status,
                "On-disk model file modification must invalidate check status to NOT_EXECUTED");

        RiskAssessmentService.SystemRiskAssessment assessment = riskAssessmentService.assessSystemRisk();
        assertEquals(AssessmentDecision.REVIEW, assessment.overallDecision,
                "Tampered on-disk model file must mandate REVIEW");
    }

    @Test
    void testDatasetPhysicalFileAdditionOrModificationOnDiskProducesReview(@TempDir Path tempDir) throws Exception {
        File cocoDir = FixtureGenerator.createSampleCocoDataset(tempDir, "ds_physical_coco");
        Dataset dataset = new Dataset("Physical Changing DS", DatasetFormat.COCO, cocoDir.getAbsolutePath());
        dataset.setSha256Hash(HashUtil.calculateDatasetDirectoryHash(cocoDir));
        dataset = datasetRepository.save(dataset);

        DatasetAudit dAudit = new DatasetAudit(dataset.getId());
        dAudit.setStatus(AuditStatus.COMPLETED);
        dAudit = datasetAuditRepository.save(dAudit);

        riskAssessmentService.recordCheckStatus("dataset.integrity", dAudit.getId(),
                String.valueOf(dataset.getId()), dataset.getSha256Hash(), CheckExecutionStatus.PASSED, "Initial pass");

        // Physically add an unrecorded image file into the dataset directory on disk without DB update
        File extraImage = new File(cocoDir, "images/unrecorded_attack_sample.jpg");
        Files.writeString(extraImage.toPath(), "POISONED_DATASET_PAYLOAD");

        CheckExecutionStatus status = riskAssessmentService.getCheckStatus("dataset.integrity");
        assertEquals(CheckExecutionStatus.NOT_EXECUTED, status,
                "Physical file addition in dataset directory must invalidate check status to NOT_EXECUTED");

        RiskAssessmentService.SystemRiskAssessment assessment = riskAssessmentService.assessSystemRisk();
        assertEquals(AssessmentDecision.REVIEW, assessment.overallDecision,
                "Dataset content tampering on disk must mandate REVIEW");
    }

    @Test
    void testDatasetSymlinkInsertionProducesReview(@TempDir Path tempDir) throws Exception {
        File cocoDir = FixtureGenerator.createSampleCocoDataset(tempDir, "ds_symlink_coco");
        Dataset dataset = new Dataset("Symlink DS", DatasetFormat.COCO, cocoDir.getAbsolutePath());
        dataset.setSha256Hash(HashUtil.calculateDatasetDirectoryHash(cocoDir));
        dataset = datasetRepository.save(dataset);

        DatasetAudit dAudit = new DatasetAudit(dataset.getId());
        dAudit.setStatus(AuditStatus.COMPLETED);
        dAudit = datasetAuditRepository.save(dAudit);

        riskAssessmentService.recordCheckStatus("dataset.integrity", dAudit.getId(),
                String.valueOf(dataset.getId()), dataset.getSha256Hash(), CheckExecutionStatus.PASSED, "Initial pass");

        // Create an outside sensitive file and symlink it into the dataset directory
        File outsideSecret = tempDir.resolve("external_secret.txt").toFile();
        Files.writeString(outsideSecret.toPath(), "SENSITIVE_DATA");
        Path symlinkFile = new File(cocoDir, "images/symlink_escape.jpg").toPath();
        try {
            Files.createSymbolicLink(symlinkFile, outsideSecret.toPath());
        } catch (UnsupportedOperationException | java.io.IOException e) {
            return; // Skip on platforms without symlink support
        }

        CheckExecutionStatus status = riskAssessmentService.getCheckStatus("dataset.integrity");
        assertEquals(CheckExecutionStatus.NOT_EXECUTED, status,
                "Symlink presence inside dataset directory must cause failure closed and produce NOT_EXECUTED");

        RiskAssessmentService.SystemRiskAssessment assessment = riskAssessmentService.assessSystemRisk();
        assertEquals(AssessmentDecision.REVIEW, assessment.overallDecision);
    }

    @Test
    void testProvenanceTamperedChainProducesFailedAndReviewOrQuarantine() {
        java.util.List<InferenceRecord> records = recordRepository.findAllOrderedBySequence();
        assertFalse(records.isEmpty(), "Provenance records must exist");
        InferenceRecord last = records.get(records.size() - 1);

        riskAssessmentService.recordCheckStatus("provenance.verification", null,
                "provenance-chain", last.getCurrentRecordHash(), CheckExecutionStatus.PASSED, "Initial pass");

        // Tamper with record #1 nonce in database without re-hashing or HMAC re-signing
        InferenceRecord first = records.get(0);
        String originalNonce = first.getNonce();
        try {
            first.setNonce("TAMPERED_UNAUTHORIZED_NONCE_VALUE");
            recordRepository.save(first);

            CheckExecutionStatus status = riskAssessmentService.getCheckStatus("provenance.verification");
            assertEquals(CheckExecutionStatus.FAILED, status,
                    "Tampered cryptographic provenance chain must resolve to FAILED");

            RiskAssessmentService.SystemRiskAssessment assessment = riskAssessmentService.assessSystemRisk();
            assertTrue(assessment.overallDecision == AssessmentDecision.REVIEW ||
                       assessment.overallDecision == AssessmentDecision.QUARANTINE,
                    "Tampered provenance chain must mandate REVIEW or QUARANTINE, never ACCEPT");
        } finally {
            first.setNonce(originalNonce);
            recordRepository.save(first);
            riskAssessmentService.invalidateProvenanceCache();
        }
    }
}

