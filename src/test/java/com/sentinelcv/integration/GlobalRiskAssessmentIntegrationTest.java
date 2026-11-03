package com.sentinelcv.integration;

import com.sentinelcv.entity.AuditReport;
import com.sentinelcv.entity.SecurityFinding;
import com.sentinelcv.enums.AssessmentDecision;
import com.sentinelcv.enums.CheckExecutionStatus;
import com.sentinelcv.enums.FindingSeverity;
import com.sentinelcv.repository.AuditReportRepository;
import com.sentinelcv.repository.SecurityFindingRepository;
import com.sentinelcv.service.ReportService;
import com.sentinelcv.service.RiskAssessmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class GlobalRiskAssessmentIntegrationTest {

    @Autowired
    private RiskAssessmentService riskAssessmentService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private SecurityFindingRepository securityFindingRepository;

    @Autowired
    private AuditReportRepository reportRepository;

    @BeforeEach
    @AfterEach
    void resetState() {
        securityFindingRepository.deleteAll();
        for (String check : RiskAssessmentService.MANDATORY_CHECKS) {
            riskAssessmentService.recordCheckStatus(check, CheckExecutionStatus.NOT_EXECUTED);
        }
    }

    @Test
    void testEmptyDatabaseProducesReview() {
        // In an empty database without executed checks, system must NOT award false ACCEPT
        RiskAssessmentService.SystemRiskAssessment assessment = riskAssessmentService.assessSystemRisk();
        assertEquals(AssessmentDecision.REVIEW, assessment.overallDecision,
                "Empty database without executed checks must yield REVIEW, never ACCEPT");
        assertTrue(assessment.notExecutedChecksCount >= RiskAssessmentService.MANDATORY_CHECKS.size());
    }

    @Test
    void testNoFindingsWithMissingStatusesProducesReview() {
        assertEquals(0, securityFindingRepository.count());
        // Deliberately reset all checks to NOT_EXECUTED
        for (String check : RiskAssessmentService.MANDATORY_CHECKS) {
            riskAssessmentService.recordCheckStatus(check, CheckExecutionStatus.NOT_EXECUTED);
        }

        RiskAssessmentService.SystemRiskAssessment assessment = riskAssessmentService.assessSystemRisk();
        assertEquals(AssessmentDecision.REVIEW, assessment.overallDecision,
                "No findings but missing statuses must produce REVIEW");
    }

    @Test
    void testAllMandatoryChecksPassedProducesAccept() {
        // Record all mandatory checks as explicitly PASSED
        for (String check : RiskAssessmentService.MANDATORY_CHECKS) {
            riskAssessmentService.recordCheckStatus(check, CheckExecutionStatus.PASSED);
        }

        RiskAssessmentService.SystemRiskAssessment assessment = riskAssessmentService.assessSystemRisk();
        assertEquals(AssessmentDecision.ACCEPT, assessment.overallDecision,
                "All mandatory checks passed and no findings must produce ACCEPT");
        assertEquals(0, assessment.unavailableChecksCount);
        assertEquals(0, assessment.notExecutedChecksCount);
        assertEquals(RiskAssessmentService.MANDATORY_CHECKS.size(), assessment.passedChecksCount);
    }

    @Test
    void testOneUnavailableMandatoryCheckProducesReview() {
        for (String check : RiskAssessmentService.MANDATORY_CHECKS) {
            riskAssessmentService.recordCheckStatus(check, CheckExecutionStatus.PASSED);
        }
        // One mandatory check is UNAVAILABLE
        riskAssessmentService.recordCheckStatus("model.trigger_perturbation", CheckExecutionStatus.UNAVAILABLE);

        RiskAssessmentService.SystemRiskAssessment assessment = riskAssessmentService.assessSystemRisk();
        assertEquals(AssessmentDecision.REVIEW, assessment.overallDecision,
                "Even without findings, one UNAVAILABLE mandatory check must mandate REVIEW");
        assertEquals(1, assessment.unavailableChecksCount);
    }

    @Test
    void testOneNotExecutedMandatoryCheckProducesReview() {
        for (String check : RiskAssessmentService.MANDATORY_CHECKS) {
            riskAssessmentService.recordCheckStatus(check, CheckExecutionStatus.PASSED);
        }
        // One mandatory check was NOT_EXECUTED
        riskAssessmentService.recordCheckStatus("dataset.integrity", CheckExecutionStatus.NOT_EXECUTED);

        RiskAssessmentService.SystemRiskAssessment assessment = riskAssessmentService.assessSystemRisk();
        assertEquals(AssessmentDecision.REVIEW, assessment.overallDecision,
                "One NOT_EXECUTED mandatory check must mandate REVIEW");
        assertEquals(1, assessment.notExecutedChecksCount);
    }

    @Test
    void testCriticalFindingPlusUnavailableCheckProducesQuarantine() {
        for (String check : RiskAssessmentService.MANDATORY_CHECKS) {
            riskAssessmentService.recordCheckStatus(check, CheckExecutionStatus.PASSED);
        }
        riskAssessmentService.recordCheckStatus("model.trigger_perturbation", CheckExecutionStatus.UNAVAILABLE);

        SecurityFinding critical = SecurityFinding.builder()
                .findingId("FIND-CRIT-TEST-1")
                .module("MODEL")
                .category("Adversarial Trojan")
                .description("Confirmed trigger backdoor signature in weights")
                .severity(FindingSeverity.CRITICAL)
                .confidence(1.0)
                .build();
        securityFindingRepository.save(critical);

        RiskAssessmentService.SystemRiskAssessment assessment = riskAssessmentService.assessSystemRisk();
        assertEquals(AssessmentDecision.QUARANTINE, assessment.overallDecision,
                "Critical finding plus unavailable check must strictly yield QUARANTINE by precedence");
        assertEquals(1, assessment.criticalCount);
        assertEquals(1, assessment.unavailableChecksCount);
    }

    @Test
    void testDashboardAndReportsAgreeOnDecision() throws IOException {
        // Pass all checks
        for (String check : RiskAssessmentService.MANDATORY_CHECKS) {
            riskAssessmentService.recordCheckStatus(check, CheckExecutionStatus.PASSED);
        }

        // Add a HIGH finding to trigger REVIEW
        SecurityFinding high = SecurityFinding.builder()
                .findingId("FIND-HIGH-TEST-1")
                .module("DATASET")
                .category("Duplicate Attack")
                .description("High collision cluster detected in training data")
                .severity(FindingSeverity.HIGH)
                .confidence(0.9)
                .build();
        securityFindingRepository.save(high);

        // 1. Dashboard authoritative assessment
        RiskAssessmentService.SystemRiskAssessment dashboardAssessment = riskAssessmentService.assessSystemRisk();
        assertEquals(AssessmentDecision.REVIEW, dashboardAssessment.overallDecision);

        // 2. JSON Report generation
        AuditReport jsonReport = reportService.generateReport("Integration Test Report", "JSON");
        assertNotNull(jsonReport);
        assertEquals(dashboardAssessment.overallDecision.name(), jsonReport.getAssessmentDecision(),
                "JSON report decision must agree with Dashboard assessment");

        // 3. PDF Report generation
        AuditReport pdfReport = reportService.generateReport("Integration Test PDF Report", "PDF");
        assertNotNull(pdfReport);
        assertEquals(dashboardAssessment.overallDecision.name(), pdfReport.getAssessmentDecision(),
                "PDF report decision must agree with Dashboard assessment");

        // Cleanup generated report files
        new File(jsonReport.getFilePath()).delete();
        new File(pdfReport.getFilePath()).delete();
    }
}
