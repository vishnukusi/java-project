package com.sentinelcv.controller;

import com.sentinelcv.entity.AuditHistory;
import com.sentinelcv.enums.AssessmentDecision;
import com.sentinelcv.enums.FindingSeverity;
import com.sentinelcv.repository.AuditHistoryRepository;
import com.sentinelcv.repository.DatasetRepository;
import com.sentinelcv.repository.InferenceRecordRepository;
import com.sentinelcv.repository.MlModelRepository;
import com.sentinelcv.repository.SecurityFindingRepository;
import com.sentinelcv.service.RiskAssessmentService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Controller for the main SentinelCV dashboard.
 * Aggregates real metrics and audit history from SQLite persistence.
 */
@Controller
public class DashboardController {

    private final DatasetRepository datasetRepository;
    private final MlModelRepository modelRepository;
    private final InferenceRecordRepository inferenceRecordRepository;
    private final SecurityFindingRepository securityFindingRepository;
    private final AuditHistoryRepository auditHistoryRepository;
    private final RiskAssessmentService riskAssessmentService;

    public DashboardController(DatasetRepository datasetRepository,
                               MlModelRepository modelRepository,
                               InferenceRecordRepository inferenceRecordRepository,
                               SecurityFindingRepository securityFindingRepository,
                               AuditHistoryRepository auditHistoryRepository,
                               RiskAssessmentService riskAssessmentService) {
        this.datasetRepository = datasetRepository;
        this.modelRepository = modelRepository;
        this.inferenceRecordRepository = inferenceRecordRepository;
        this.securityFindingRepository = securityFindingRepository;
        this.auditHistoryRepository = auditHistoryRepository;
        this.riskAssessmentService = riskAssessmentService;
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {
        long totalDatasets = datasetRepository.count();
        long totalModels = modelRepository.count();
        long totalInferences = inferenceRecordRepository.count();
        long totalFindings = securityFindingRepository.count();
        long criticalFindings = securityFindingRepository.countBySeverity(FindingSeverity.CRITICAL);
        long highFindings = securityFindingRepository.countBySeverity(FindingSeverity.HIGH);

        RiskAssessmentService.SystemRiskAssessment assessment = riskAssessmentService.assessSystemRisk();
        List<AuditHistory> recentAudits = auditHistoryRepository.findRecent(10);

        model.addAttribute("totalDatasets", totalDatasets);
        model.addAttribute("totalModels", totalModels);
        model.addAttribute("totalInferences", totalInferences);
        model.addAttribute("totalFindings", totalFindings);
        model.addAttribute("criticalFindings", criticalFindings);
        model.addAttribute("highFindings", highFindings);
        model.addAttribute("assessment", assessment);
        model.addAttribute("recentAudits", recentAudits);

        return "dashboard";
    }
}
