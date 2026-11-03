package com.sentinelcv.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelcv.dataset.DatasetModels;
import com.sentinelcv.dataset.DatasetParser;
import com.sentinelcv.drift.DistributionComparator;
import com.sentinelcv.drift.FeatureStatistics;
import com.sentinelcv.entity.AuditHistory;
import com.sentinelcv.entity.Dataset;
import com.sentinelcv.entity.DriftAudit;
import com.sentinelcv.entity.SecurityFinding;
import com.sentinelcv.enums.FindingSeverity;
import com.sentinelcv.repository.AuditHistoryRepository;
import com.sentinelcv.repository.DatasetRepository;
import com.sentinelcv.repository.DriftAuditRepository;
import com.sentinelcv.repository.SecurityFindingRepository;
import com.sentinelcv.repository.SettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service orchestrating distribution shift and anomaly detection between
 * reference and evaluation datasets.
 */
@Service
public class DriftDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(DriftDetectionService.class);

    private final List<DatasetParser> parsers;
    private final FeatureStatistics featureStatistics;
    private final DistributionComparator distributionComparator;
    private final DatasetRepository datasetRepository;
    private final DriftAuditRepository driftAuditRepository;
    private final SecurityFindingRepository securityFindingRepository;
    private final AuditHistoryRepository auditHistoryRepository;
    private final SettingRepository settingRepository;
    private final ObjectMapper objectMapper;

    public DriftDetectionService(List<DatasetParser> parsers,
                                 FeatureStatistics featureStatistics,
                                 DistributionComparator distributionComparator,
                                 DatasetRepository datasetRepository,
                                 DriftAuditRepository driftAuditRepository,
                                 SecurityFindingRepository securityFindingRepository,
                                 AuditHistoryRepository auditHistoryRepository,
                                 SettingRepository settingRepository,
                                 ObjectMapper objectMapper) {
        this.parsers = parsers;
        this.featureStatistics = featureStatistics;
        this.distributionComparator = distributionComparator;
        this.datasetRepository = datasetRepository;
        this.driftAuditRepository = driftAuditRepository;
        this.securityFindingRepository = securityFindingRepository;
        this.auditHistoryRepository = auditHistoryRepository;
        this.settingRepository = settingRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Executes drift analysis comparing a reference dataset and an evaluation dataset.
     */
    public DriftAudit executeDriftAudit(Long referenceDatasetId, Long evaluationDatasetId) {
        Optional<Dataset> refOpt = datasetRepository.findById(referenceDatasetId);
        Optional<Dataset> evalOpt = datasetRepository.findById(evaluationDatasetId);

        if (refOpt.isEmpty() || evalOpt.isEmpty()) {
            throw new IllegalArgumentException("Reference or evaluation dataset does not exist.");
        }

        Dataset refDataset = refOpt.get();
        Dataset evalDataset = evalOpt.get();

        DriftAudit driftAudit = new DriftAudit();
        driftAudit.setReferenceDatasetId(referenceDatasetId);
        driftAudit.setEvaluationDatasetId(evaluationDatasetId);
        driftAudit.setStatus("RUNNING");
        driftAudit.setStartedAt(LocalDateTime.now());
        driftAudit = driftAuditRepository.save(driftAudit);

        try {
            File refDir = new File(refDataset.getPath());
            File evalDir = new File(evalDataset.getPath());

            // 1. Parse both datasets
            DatasetParser refParser = findParser(refDataset, refDir);
            DatasetParser evalParser = findParser(evalDataset, evalDir);

            DatasetModels.ParseResult refResult = refParser.parse(refDir);
            DatasetModels.ParseResult evalResult = evalParser.parse(evalDir);

            // 2. Extract feature distributions and class distributions
            FeatureStatistics.DatasetFeatureDistributions refDist = featureStatistics.extractDistributions(refDir, refResult.images);
            FeatureStatistics.DatasetFeatureDistributions evalDist = featureStatistics.extractDistributions(evalDir, evalResult.images);

            Map<String, Integer> refClasses = countClassOccurrences(refResult);
            Map<String, Integer> evalClasses = countClassOccurrences(evalResult);

            // 3. Compare distributions
            double psiThreshold = settingRepository.getDouble("psi_threshold", 0.2);
            double ksThreshold = settingRepository.getDouble("drift_significance_threshold", 0.2);

            DistributionComparator.ComparisonResult compResult =
                    distributionComparator.compareWithClasses(refDist, evalDist, refClasses, evalClasses, psiThreshold, ksThreshold);

            // 4. Update audit entity
            driftAudit.setDriftDetected(compResult.driftDetected);
            driftAudit.setOverallDriftScore(compResult.overallDriftScore);
            driftAudit.setSeverity(compResult.overallSeverity);
            driftAudit.setMetricsJson(objectMapper.writeValueAsString(compResult));
            driftAudit.setSummary(String.join(" ", compResult.summaryStatements));
            driftAudit.setStatus("COMPLETED");
            driftAudit.setCompletedAt(LocalDateTime.now());
            driftAudit = driftAuditRepository.save(driftAudit);

            // 5. Generate and persist Security Findings
            String assessmentDecision;
            int findingsCount = 0;

            if (!compResult.checksAvailable) {
                // Statistical checks unavailable cannot silently succeed
                assessmentDecision = "REVIEW";
                SecurityFinding finding = SecurityFinding.builder()
                        .findingId("FIND-DRIFT-UNAVAILABLE-" + driftAudit.getId())
                        .module("DRIFT")
                        .category("Statistical Confidence")
                        .description(String.format("Drift detection UNAVAILABLE for '%s' vs '%s': Insufficient sample size for mathematical significance.",
                                refDataset.getName(), evalDataset.getName()))
                        .severity(FindingSeverity.INFO)
                        .confidence(1.0)
                        .evidence("Dataset sample size below minimum threshold of 5 images")
                        .affectedAsset(evalDataset.getName())
                        .recommendedAction("Collect at least 5 representative images per dataset to enable statistical shift verification.")
                        .sourceAuditId(driftAudit.getId())
                        .sourceAuditType("DRIFT")
                        .build();
                securityFindingRepository.save(finding);
                findingsCount++;
            } else if (compResult.driftDetected) {
                FindingSeverity findingSeverity = switch (compResult.overallSeverity) {
                    case "CRITICAL" -> FindingSeverity.CRITICAL;
                    case "HIGH" -> FindingSeverity.HIGH;
                    case "MEDIUM" -> FindingSeverity.MEDIUM;
                    default -> FindingSeverity.LOW;
                };

                assessmentDecision = "CRITICAL".equals(compResult.overallSeverity) ? "QUARANTINE" : "REVIEW";

                SecurityFinding finding = SecurityFinding.builder()
                        .findingId("FIND-DRIFT-" + driftAudit.getId())
                        .module("DRIFT")
                        .category("Distribution Shift")
                        .description(String.format("Significant dataset distribution drift detected between '%s' and '%s' (Drift Score: %.3f)",
                                refDataset.getName(), evalDataset.getName(), compResult.overallDriftScore))
                        .severity(findingSeverity)
                        .confidence(0.92)
                        .evidence("Metrics: " + String.join(", ", compResult.summaryStatements))
                        .affectedAsset(evalDataset.getName())
                        .recommendedAction("Investigate potential covariate shift or concept drift before deploying models trained on reference data.")
                        .sourceAuditId(driftAudit.getId())
                        .sourceAuditType("DRIFT")
                        .build();

                securityFindingRepository.save(finding);
                findingsCount++;
            } else {
                assessmentDecision = "ACCEPT";
            }

            // 6. Record Audit History
            AuditHistory history = new AuditHistory();
            history.setAuditType("DRIFT");
            history.setTargetName(refDataset.getName() + " vs " + evalDataset.getName());
            history.setTargetId(driftAudit.getId());
            history.setStatus("COMPLETED");
            history.setFindingsCount(findingsCount);
            history.setAssessmentDecision(assessmentDecision);
            history.setStartedAt(driftAudit.getStartedAt());
            history.setCompletedAt(driftAudit.getCompletedAt());
            history.setSummary(driftAudit.getSummary());
            auditHistoryRepository.save(history);

            return driftAudit;

        } catch (Exception e) {
            logger.error("Drift audit failed between {} and {}", refDataset.getName(), evalDataset.getName(), e);
            driftAudit.setStatus("FAILED");
            driftAudit.setCompletedAt(LocalDateTime.now());
            driftAudit.setSummary("Failed: " + e.getMessage());
            return driftAuditRepository.save(driftAudit);
        }
    }

    private DatasetParser findParser(Dataset dataset, File dir) {
        return parsers.stream()
                .filter(p -> p.getFormat() == dataset.getFormat() || p.canParse(dir))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No parser for " + dataset.getName()));
    }

    private java.util.Map<String, Integer> countClassOccurrences(DatasetModels.ParseResult parseResult) {
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        if (parseResult == null || parseResult.annotations == null) {
            return counts;
        }
        for (DatasetModels.AnnotationInfo ann : parseResult.annotations) {
            String catName = ann.categoryName != null ? ann.categoryName : "category_" + ann.categoryId;
            counts.merge(catName, 1, Integer::sum);
        }
        return counts;
    }
}
