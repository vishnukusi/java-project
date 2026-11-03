package com.sentinelcv.service;

import com.sentinelcv.entity.Dataset;
import com.sentinelcv.entity.InferenceRecord;
import com.sentinelcv.entity.MandatoryCheckRecord;
import com.sentinelcv.entity.MlModel;
import com.sentinelcv.enums.AssessmentDecision;
import com.sentinelcv.enums.CheckExecutionStatus;
import com.sentinelcv.enums.DatasetFormat;
import com.sentinelcv.enums.ModelFormat;
import com.sentinelcv.provenance.ProvenanceKeyManager;
import com.sentinelcv.repository.DatasetAuditRepository;
import com.sentinelcv.repository.DatasetRepository;
import com.sentinelcv.repository.InferenceRecordRepository;
import com.sentinelcv.repository.MandatoryCheckStatusRepository;
import com.sentinelcv.repository.MlModelRepository;
import com.sentinelcv.repository.ModelAuditRepository;
import com.sentinelcv.repository.SecurityFindingRepository;
import com.sentinelcv.repository.SettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Adversarial regression tests for Critical Issue D: Fail-Closed Dependency Handling.
 * Verifies that missing repositories, missing services, missing keys, unreadable files,
 * unknown check IDs, or unexecuted checks NEVER produce PASSED or ACCEPT decisions.
 */
public class FailClosedDependencyTest {

    private SecurityFindingRepository findingRepository;
    private SettingRepository settingRepository;
    private MandatoryCheckStatusRepository checkStatusRepository;
    private MlModelRepository modelRepository;
    private ModelAuditRepository modelAuditRepository;
    private DatasetRepository datasetRepository;
    private DatasetAuditRepository datasetAuditRepository;
    private InferenceRecordRepository recordRepository;
    private ProvenanceKeyManager provenanceKeyManager;

    @BeforeEach
    void setUp() {
        findingRepository = Mockito.mock(SecurityFindingRepository.class);
        settingRepository = Mockito.mock(SettingRepository.class);
        checkStatusRepository = Mockito.mock(MandatoryCheckStatusRepository.class);
        modelRepository = Mockito.mock(MlModelRepository.class);
        modelAuditRepository = Mockito.mock(ModelAuditRepository.class);
        datasetRepository = Mockito.mock(DatasetRepository.class);
        datasetAuditRepository = Mockito.mock(DatasetAuditRepository.class);
        recordRepository = Mockito.mock(InferenceRecordRepository.class);
        provenanceKeyManager = Mockito.mock(ProvenanceKeyManager.class);

        when(settingRepository.getDouble("quarantine_score", 80.0)).thenReturn(80.0);
        when(settingRepository.getDouble("review_score", 40.0)).thenReturn(40.0);
        when(findingRepository.findAll()).thenReturn(List.of());
    }

    @Test
    void testMissingModelRepositoryResolvesToUnavailableAndReview() {
        // modelRepository is NULL
        RiskAssessmentService service = new RiskAssessmentService(
                findingRepository, settingRepository, checkStatusRepository,
                null, modelAuditRepository, datasetRepository, datasetAuditRepository,
                recordRepository, null, provenanceKeyManager
        );

        MandatoryCheckRecord record = new MandatoryCheckRecord("model.baseline_inference", 1L, "1", "hash", CheckExecutionStatus.PASSED, "ok");
        when(checkStatusRepository.findById("model.baseline_inference")).thenReturn(Optional.of(record));

        CheckExecutionStatus status = service.getCheckStatus("model.baseline_inference");
        assertEquals(CheckExecutionStatus.UNAVAILABLE, status,
                "Missing modelRepository must resolve to UNAVAILABLE, never PASSED");

        RiskAssessmentService.SystemRiskAssessment assessment = service.assessSystemRisk();
        assertNotEquals(AssessmentDecision.ACCEPT, assessment.overallDecision,
                "Missing model repository must NEVER result in ACCEPT");
        assertEquals(AssessmentDecision.REVIEW, assessment.overallDecision);
    }

    @Test
    void testMissingDatasetRepositoryResolvesToUnavailableAndReview() {
        // datasetRepository is NULL
        RiskAssessmentService service = new RiskAssessmentService(
                findingRepository, settingRepository, checkStatusRepository,
                modelRepository, modelAuditRepository, null, datasetAuditRepository,
                recordRepository, null, provenanceKeyManager
        );

        MandatoryCheckRecord record = new MandatoryCheckRecord("dataset.integrity", 1L, "1", "hash", CheckExecutionStatus.PASSED, "ok");
        when(checkStatusRepository.findById("dataset.integrity")).thenReturn(Optional.of(record));

        CheckExecutionStatus status = service.getCheckStatus("dataset.integrity");
        assertEquals(CheckExecutionStatus.UNAVAILABLE, status,
                "Missing datasetRepository must resolve to UNAVAILABLE, never PASSED");

        RiskAssessmentService.SystemRiskAssessment assessment = service.assessSystemRisk();
        assertNotEquals(AssessmentDecision.ACCEPT, assessment.overallDecision);
        assertEquals(AssessmentDecision.REVIEW, assessment.overallDecision);
    }

    @Test
    void testMissingInferenceRecordRepositoryResolvesToUnavailableAndReview() {
        // recordRepository is NULL
        RiskAssessmentService service = new RiskAssessmentService(
                findingRepository, settingRepository, checkStatusRepository,
                modelRepository, modelAuditRepository, datasetRepository, datasetAuditRepository,
                null, null, provenanceKeyManager
        );

        MandatoryCheckRecord record = new MandatoryCheckRecord("provenance.verification", null, "provenance-chain", "hash", CheckExecutionStatus.PASSED, "ok");
        when(checkStatusRepository.findById("provenance.verification")).thenReturn(Optional.of(record));

        CheckExecutionStatus status = service.getCheckStatus("provenance.verification");
        assertEquals(CheckExecutionStatus.UNAVAILABLE, status,
                "Missing inferenceRecordRepository must resolve to UNAVAILABLE, never PASSED");

        RiskAssessmentService.SystemRiskAssessment assessment = service.assessSystemRisk();
        assertNotEquals(AssessmentDecision.ACCEPT, assessment.overallDecision);
        assertEquals(AssessmentDecision.REVIEW, assessment.overallDecision);
    }

    @Test
    void testMissingModelAuditRepositoryResolvesToUnavailable() {
        // modelAuditRepository is NULL
        RiskAssessmentService service = new RiskAssessmentService(
                findingRepository, settingRepository, checkStatusRepository,
                modelRepository, null, datasetRepository, datasetAuditRepository,
                recordRepository, null, provenanceKeyManager
        );

        MlModel m = new MlModel("test", ModelFormat.ONNX, "/tmp/nonexistent_model.onnx");
        m.setId(1L);
        when(modelRepository.findAll()).thenReturn(List.of(m));
        when(modelRepository.findById(1L)).thenReturn(Optional.of(m));

        MandatoryCheckRecord record = new MandatoryCheckRecord("model.baseline_inference", 1L, "1", "hash", CheckExecutionStatus.PASSED, "ok");
        when(checkStatusRepository.findById("model.baseline_inference")).thenReturn(Optional.of(record));

        CheckExecutionStatus status = service.getCheckStatus("model.baseline_inference");
        assertEquals(CheckExecutionStatus.UNAVAILABLE, status);
    }

    @Test
    void testMissingDatasetAuditRepositoryResolvesToUnavailable() {
        // datasetAuditRepository is NULL
        RiskAssessmentService service = new RiskAssessmentService(
                findingRepository, settingRepository, checkStatusRepository,
                modelRepository, modelAuditRepository, datasetRepository, null,
                recordRepository, null, provenanceKeyManager
        );

        Dataset d = new Dataset("test", DatasetFormat.COCO, "/tmp/nonexistent_ds");
        d.setId(1L);
        when(datasetRepository.findAll()).thenReturn(List.of(d));
        when(datasetRepository.findById(1L)).thenReturn(Optional.of(d));

        MandatoryCheckRecord record = new MandatoryCheckRecord("dataset.integrity", 1L, "1", "hash", CheckExecutionStatus.PASSED, "ok");
        when(checkStatusRepository.findById("dataset.integrity")).thenReturn(Optional.of(record));

        CheckExecutionStatus status = service.getCheckStatus("dataset.integrity");
        assertEquals(CheckExecutionStatus.UNAVAILABLE, status);
    }

    @Test
    void testMissingProvenanceKeyManagerResolvesToUnavailable() {
        // provenanceKeyManager is NULL
        RiskAssessmentService service = new RiskAssessmentService(
                findingRepository, settingRepository, checkStatusRepository,
                modelRepository, modelAuditRepository, datasetRepository, datasetAuditRepository,
                recordRepository, null, null
        );

        InferenceRecord ir = new InferenceRecord();
        ir.setSequenceNumber(1);
        ir.setCurrentRecordHash("HASH_1");
        when(recordRepository.findAllOrderedBySequence()).thenReturn(List.of(ir));

        MandatoryCheckRecord record = new MandatoryCheckRecord("provenance.verification", null, "provenance-chain", "HASH_1", CheckExecutionStatus.PASSED, "ok");
        when(checkStatusRepository.findById("provenance.verification")).thenReturn(Optional.of(record));

        CheckExecutionStatus status = service.getCheckStatus("provenance.verification");
        assertEquals(CheckExecutionStatus.UNAVAILABLE, status);
    }

    @Test
    void testMissingProvenanceKeyResolvesToUnavailable() {
        when(provenanceKeyManager.getVerificationKey()).thenReturn(Optional.empty());

        RiskAssessmentService service = new RiskAssessmentService(
                findingRepository, settingRepository, checkStatusRepository,
                modelRepository, modelAuditRepository, datasetRepository, datasetAuditRepository,
                recordRepository, null, provenanceKeyManager
        );

        InferenceRecord ir = new InferenceRecord();
        ir.setSequenceNumber(1);
        ir.setCurrentRecordHash("HASH_1");
        when(recordRepository.findAllOrderedBySequence()).thenReturn(List.of(ir));

        MandatoryCheckRecord record = new MandatoryCheckRecord("provenance.verification", null, "provenance-chain", "HASH_1", CheckExecutionStatus.PASSED, "ok");
        when(checkStatusRepository.findById("provenance.verification")).thenReturn(Optional.of(record));

        CheckExecutionStatus status = service.getCheckStatus("provenance.verification");
        assertEquals(CheckExecutionStatus.UNAVAILABLE, status);
    }

    @Test
    void testMissingCheckStatusRepositoryResolvesToUnavailable() {
        RiskAssessmentService service = new RiskAssessmentService(
                findingRepository, null, null, null, null, null, null, null, null, null
        );

        CheckExecutionStatus status = service.getCheckStatus("dataset.integrity");
        assertEquals(CheckExecutionStatus.UNAVAILABLE, status,
                "When both checkStatusRepository and settingRepository are null, check status must be UNAVAILABLE");
    }

    @Test
    void testUnknownCheckIdentifierFailsClosed() {
        RiskAssessmentService service = new RiskAssessmentService(
                findingRepository, settingRepository, checkStatusRepository,
                modelRepository, modelAuditRepository, datasetRepository, datasetAuditRepository,
                recordRepository, null, provenanceKeyManager
        );

        CheckExecutionStatus status = service.getCheckStatus("unrecognized.unknown_security_check");
        assertEquals(CheckExecutionStatus.UNAVAILABLE, status,
                "Unknown check identifier must fail closed to UNAVAILABLE");
    }

    @Test
    void testUnreadableModelFileResolvesToUnavailableAndReview() {
        RiskAssessmentService service = new RiskAssessmentService(
                findingRepository, settingRepository, checkStatusRepository,
                modelRepository, modelAuditRepository, datasetRepository, datasetAuditRepository,
                recordRepository, null, provenanceKeyManager
        );

        MlModel m = new MlModel("test", ModelFormat.ONNX, "/path/to/missing_model_file.onnx");
        m.setId(1L);
        when(modelRepository.findAll()).thenReturn(List.of(m));
        when(modelRepository.findById(1L)).thenReturn(Optional.of(m));

        MandatoryCheckRecord record = new MandatoryCheckRecord("model.baseline_inference", 1L, "1", "hash", CheckExecutionStatus.PASSED, "ok");
        when(checkStatusRepository.findById("model.baseline_inference")).thenReturn(Optional.of(record));

        CheckExecutionStatus status = service.getCheckStatus("model.baseline_inference");
        assertEquals(CheckExecutionStatus.UNAVAILABLE, status,
                "Missing or unreadable model file on disk must resolve to UNAVAILABLE");
    }

    @Test
    void testUnreadableDatasetDirectoryResolvesToUnavailableAndReview() {
        RiskAssessmentService service = new RiskAssessmentService(
                findingRepository, settingRepository, checkStatusRepository,
                modelRepository, modelAuditRepository, datasetRepository, datasetAuditRepository,
                recordRepository, null, provenanceKeyManager
        );

        Dataset d = new Dataset("test", DatasetFormat.COCO, "/path/to/missing_dataset_directory");
        d.setId(1L);
        when(datasetRepository.findAll()).thenReturn(List.of(d));
        when(datasetRepository.findById(1L)).thenReturn(Optional.of(d));

        MandatoryCheckRecord record = new MandatoryCheckRecord("dataset.integrity", 1L, "1", "hash", CheckExecutionStatus.PASSED, "ok");
        when(checkStatusRepository.findById("dataset.integrity")).thenReturn(Optional.of(record));

        CheckExecutionStatus status = service.getCheckStatus("dataset.integrity");
        assertEquals(CheckExecutionStatus.UNAVAILABLE, status,
                "Missing or unreadable dataset directory on disk must resolve to UNAVAILABLE");
    }

    @Test
    void testAnyUnavailableCheckPreventsAcceptDecision() {
        Map<String, CheckExecutionStatus> checks = Map.of(
                "model.baseline_inference", CheckExecutionStatus.PASSED,
                "model.trigger_perturbation", CheckExecutionStatus.PASSED,
                "dataset.integrity", CheckExecutionStatus.PASSED,
                "provenance.verification", CheckExecutionStatus.UNAVAILABLE
        );

        RiskAssessmentService service = new RiskAssessmentService(findingRepository, settingRepository);
        RiskAssessmentService.SystemRiskAssessment assessment = service.evaluateFindings(List.of(), checks);

        assertNotEquals(AssessmentDecision.ACCEPT, assessment.overallDecision,
                "Even with 3 passed checks, 1 UNAVAILABLE check must strictly prevent ACCEPT");
        assertEquals(AssessmentDecision.REVIEW, assessment.overallDecision);
        assertEquals(1, assessment.unavailableChecksCount);
    }

    @Test
    void testAnyNotExecutedCheckPreventsAcceptDecision() {
        Map<String, CheckExecutionStatus> checks = Map.of(
                "model.baseline_inference", CheckExecutionStatus.PASSED,
                "model.trigger_perturbation", CheckExecutionStatus.PASSED,
                "dataset.integrity", CheckExecutionStatus.NOT_EXECUTED,
                "provenance.verification", CheckExecutionStatus.PASSED
        );

        RiskAssessmentService service = new RiskAssessmentService(findingRepository, settingRepository);
        RiskAssessmentService.SystemRiskAssessment assessment = service.evaluateFindings(List.of(), checks);

        assertNotEquals(AssessmentDecision.ACCEPT, assessment.overallDecision,
                "1 NOT_EXECUTED check must strictly prevent ACCEPT");
        assertEquals(AssessmentDecision.REVIEW, assessment.overallDecision);
        assertEquals(1, assessment.notExecutedChecksCount);
    }

    @Test
    void testAnyFailedCheckPreventsAcceptDecision() {
        Map<String, CheckExecutionStatus> checks = Map.of(
                "model.baseline_inference", CheckExecutionStatus.PASSED,
                "model.trigger_perturbation", CheckExecutionStatus.FAILED,
                "dataset.integrity", CheckExecutionStatus.PASSED,
                "provenance.verification", CheckExecutionStatus.PASSED
        );

        RiskAssessmentService service = new RiskAssessmentService(findingRepository, settingRepository);
        RiskAssessmentService.SystemRiskAssessment assessment = service.evaluateFindings(List.of(), checks);

        assertNotEquals(AssessmentDecision.ACCEPT, assessment.overallDecision,
                "1 FAILED check must strictly prevent ACCEPT");
        assertEquals(AssessmentDecision.REVIEW, assessment.overallDecision);
        assertEquals(1, assessment.failedChecksCount);
    }
}

