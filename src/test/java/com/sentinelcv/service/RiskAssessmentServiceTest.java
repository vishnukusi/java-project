package com.sentinelcv.service;

import com.sentinelcv.entity.SecurityFinding;
import com.sentinelcv.enums.AssessmentDecision;
import com.sentinelcv.enums.CheckExecutionStatus;
import com.sentinelcv.enums.FindingSeverity;
import com.sentinelcv.repository.SecurityFindingRepository;
import com.sentinelcv.repository.SettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class RiskAssessmentServiceTest {

    private SecurityFindingRepository findingRepository;
    private SettingRepository settingRepository;
    private RiskAssessmentService riskAssessmentService;

    @BeforeEach
    void setUp() {
        findingRepository = Mockito.mock(SecurityFindingRepository.class);
        settingRepository = Mockito.mock(SettingRepository.class);
        when(settingRepository.getDouble("quarantine_score", 80.0)).thenReturn(80.0);
        when(settingRepository.getDouble("review_score", 40.0)).thenReturn(40.0);
        riskAssessmentService = new RiskAssessmentService(findingRepository, settingRepository);
    }

    @Test
    void testAcceptDecisionWhenNoIssues() {
        RiskAssessmentService.SystemRiskAssessment assessment = riskAssessmentService.evaluateFindings(List.of());
        assertEquals(AssessmentDecision.REVIEW, assessment.overallDecision,
                "Absence of findings without passed mandatory checks must produce REVIEW, not ACCEPT");
        assertEquals(0.0, assessment.compositeRiskScore);
        assertEquals(4, assessment.notExecutedChecksCount);
    }

    @Test
    void testQuarantineOnCriticalFinding() {
        SecurityFinding critical = SecurityFinding.builder()
                .findingId("FIND-CRIT-1")
                .module("DATASET")
                .category("Integrity")
                .description("Corrupted image payload")
                .severity(FindingSeverity.CRITICAL)
                .confidence(1.0)
                .build();

        RiskAssessmentService.SystemRiskAssessment assessment = riskAssessmentService.evaluateFindings(List.of(critical));
        assertEquals(AssessmentDecision.QUARANTINE, assessment.overallDecision);
        assertEquals(1, assessment.criticalCount);
    }

    @Test
    void testReviewOnHighFinding() {
        SecurityFinding high = SecurityFinding.builder()
                .findingId("FIND-HIGH-1")
                .module("MODEL")
                .category("Perturbation")
                .description("Sensitivity swing detected")
                .severity(FindingSeverity.HIGH)
                .confidence(0.9)
                .build();

        Map<String, CheckExecutionStatus> checks = Map.of(
                "model.baseline_inference", CheckExecutionStatus.PASSED,
                "model.trigger_perturbation", CheckExecutionStatus.PASSED
        );

        RiskAssessmentService.SystemRiskAssessment assessment = riskAssessmentService.evaluateFindings(List.of(high), checks);
        assertEquals(AssessmentDecision.REVIEW, assessment.overallDecision);
        assertEquals(1, assessment.highCount);
    }

    @Test
    void testUnavailableDistinction() {
        SecurityFinding unavail = SecurityFinding.builder()
                .findingId("FIND-INFO-1")
                .module("MODEL")
                .category("Runtime")
                .description("Model runtime is UNAVAILABLE in this environment")
                .severity(FindingSeverity.INFO)
                .confidence(1.0)
                .build();

        RiskAssessmentService.SystemRiskAssessment assessment = riskAssessmentService.evaluateFindings(List.of(unavail));
        assertEquals(1, assessment.unavailableChecksCount);
        assertEquals(AssessmentDecision.REVIEW, assessment.overallDecision);
    }

    @Test
    void testMediumFindingPlusUnavailableMandatoryCheckProducesReview() {
        // A MEDIUM finding alone has low weighted risk score (6.0 < 40.0 review threshold)
        SecurityFinding medium = SecurityFinding.builder()
                .findingId("FIND-MED-1")
                .module("MODEL")
                .category("Inference")
                .description("Inference latency deviation detected")
                .severity(FindingSeverity.MEDIUM)
                .confidence(1.0)
                .build();

        Map<String, CheckExecutionStatus> checks = Map.of(
                "model.baseline_inference", CheckExecutionStatus.PASSED,
                "model.trigger_perturbation", CheckExecutionStatus.UNAVAILABLE
        );

        RiskAssessmentService.SystemRiskAssessment assessment = riskAssessmentService.evaluateFindings(List.of(medium), checks);
        assertEquals(AssessmentDecision.REVIEW, assessment.overallDecision,
                "MEDIUM finding plus unavailable mandatory check must mandate REVIEW despite low numerical score");
        assertEquals(6.0, assessment.compositeRiskScore);
        assertEquals(1, assessment.unavailableChecksCount);
    }

    @Test
    void testMediumFindingRepresentingUnavailableCheckProducesReview() {
        // When an analyzer catches an error and emits a MEDIUM finding with UNAVAILABLE status
        SecurityFinding medUnavailable = SecurityFinding.builder()
                .findingId("FIND-MODEL-EXEC-FAILURE")
                .module("MODEL")
                .category("Inference Execution")
                .description("Failed to execute baseline inference: UNAVAILABLE tensor layout")
                .severity(FindingSeverity.MEDIUM)
                .confidence(1.0)
                .build();

        RiskAssessmentService.SystemRiskAssessment assessment = riskAssessmentService.evaluateFindings(List.of(medUnavailable));
        assertEquals(1, assessment.unavailableChecksCount);
        assertEquals(AssessmentDecision.REVIEW, assessment.overallDecision,
                "MEDIUM finding indicating unavailable check must mandate REVIEW");
    }

    @Test
    void testInfoFindingPlusUnavailableMandatoryCheckProducesReview() {
        SecurityFinding info = SecurityFinding.builder()
                .findingId("FIND-INFO-NOTICE")
                .module("DATASET")
                .category("General")
                .description("Dataset format is standard JPEG")
                .severity(FindingSeverity.INFO)
                .confidence(1.0)
                .build();

        Map<String, CheckExecutionStatus> checks = Map.of(
                "dataset.integrity", CheckExecutionStatus.UNAVAILABLE
        );

        RiskAssessmentService.SystemRiskAssessment assessment = riskAssessmentService.evaluateFindings(List.of(info), checks);
        assertEquals(AssessmentDecision.REVIEW, assessment.overallDecision,
                "INFO finding plus unavailable check must mandate REVIEW");
        assertEquals(1, assessment.unavailableChecksCount);
    }

    @Test
    void testCriticalFindingPlusUnavailableCheckProducesQuarantine() {
        SecurityFinding critical = SecurityFinding.builder()
                .findingId("FIND-CRIT-POISON")
                .module("MODEL")
                .category("Backdoor")
                .description("Backdoor signature verified")
                .severity(FindingSeverity.CRITICAL)
                .confidence(1.0)
                .build();

        Map<String, CheckExecutionStatus> checks = Map.of(
                "model.trigger_perturbation", CheckExecutionStatus.UNAVAILABLE
        );

        RiskAssessmentService.SystemRiskAssessment assessment = riskAssessmentService.evaluateFindings(List.of(critical), checks);
        assertEquals(AssessmentDecision.QUARANTINE, assessment.overallDecision,
                "Critical finding must mandate QUARANTINE even when a check is unavailable");
        assertEquals(1, assessment.criticalCount);
        assertEquals(1, assessment.unavailableChecksCount);
    }

    @Test
    void testAllMandatoryChecksPassedAndNoAdverseFindingsProducesAccept() {
        Map<String, CheckExecutionStatus> checks = Map.of(
                "model.baseline_inference", CheckExecutionStatus.PASSED,
                "model.trigger_perturbation", CheckExecutionStatus.PASSED,
                "dataset.integrity", CheckExecutionStatus.PASSED,
                "provenance.verification", CheckExecutionStatus.PASSED
        );

        RiskAssessmentService.SystemRiskAssessment assessment = riskAssessmentService.evaluateFindings(List.of(), checks);
        assertEquals(AssessmentDecision.ACCEPT, assessment.overallDecision,
                "When all mandatory checks pass and no adverse findings exist, decision must be ACCEPT");
        assertEquals(0.0, assessment.compositeRiskScore);
        assertEquals(0, assessment.unavailableChecksCount);
        assertEquals(4, assessment.passedChecksCount);
    }

    @Test
    void testMissingCheckStatusIsNotSilentlyTreatedAsPassed() {
        Map<String, CheckExecutionStatus> checks = new HashMap<>();
        checks.put("model.baseline_inference", CheckExecutionStatus.PASSED);
        checks.put("model.trigger_perturbation", null); // Missing / null status
        checks.put("dataset.integrity", CheckExecutionStatus.PASSED);
        checks.put("provenance.verification", CheckExecutionStatus.PASSED);

        RiskAssessmentService.SystemRiskAssessment assessment = riskAssessmentService.evaluateFindings(List.of(), checks);
        assertNotEquals(AssessmentDecision.ACCEPT, assessment.overallDecision,
                "Missing check status must NOT be silently treated as PASSED");
        assertEquals(AssessmentDecision.REVIEW, assessment.overallDecision);
        assertEquals(1, assessment.notExecutedChecksCount);
        assertEquals(CheckExecutionStatus.NOT_EXECUTED, assessment.checkStatuses.get("model.trigger_perturbation"));
    }
}
