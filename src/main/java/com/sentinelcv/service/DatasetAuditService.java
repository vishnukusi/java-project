package com.sentinelcv.service;

import com.sentinelcv.dataset.AnnotationValidator;
import com.sentinelcv.dataset.DatasetModels;
import com.sentinelcv.dataset.DatasetParser;
import com.sentinelcv.dataset.DatasetStatisticsService;
import com.sentinelcv.dataset.DuplicateDetector;
import com.sentinelcv.dataset.ImageIntegrityAnalyzer;
import com.sentinelcv.entity.AuditHistory;
import com.sentinelcv.entity.Dataset;
import com.sentinelcv.entity.DatasetAudit;
import com.sentinelcv.entity.SecurityFinding;
import com.sentinelcv.enums.AssessmentDecision;
import com.sentinelcv.enums.AuditStatus;
import com.sentinelcv.enums.CheckExecutionStatus;
import com.sentinelcv.enums.FindingSeverity;
import com.sentinelcv.exception.DatasetParsingException;
import com.sentinelcv.repository.AuditHistoryRepository;
import com.sentinelcv.repository.DatasetAuditRepository;
import com.sentinelcv.repository.DatasetFindingRepository;
import com.sentinelcv.repository.DatasetRepository;
import com.sentinelcv.repository.SecurityFindingRepository;
import com.sentinelcv.repository.SettingRepository;
import com.sentinelcv.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Service for orchestrating dataset ingestion, integrity validation,
 * duplicate detection, annotation auditing, and persistent security finding generation.
 */
@Service
public class DatasetAuditService {

    private static final Logger logger = LoggerFactory.getLogger(DatasetAuditService.class);

    private final List<DatasetParser> parsers;
    private final DuplicateDetector duplicateDetector;
    private final AnnotationValidator annotationValidator;
    private final ImageIntegrityAnalyzer imageIntegrityAnalyzer;
    private final DatasetStatisticsService statisticsService;
    private final DatasetRepository datasetRepository;
    private final DatasetAuditRepository auditRepository;
    private final DatasetFindingRepository datasetFindingRepository;
    private final SecurityFindingRepository securityFindingRepository;
    private final AuditHistoryRepository auditHistoryRepository;
    private final SettingRepository settingRepository;
    private final RiskAssessmentService riskAssessmentService;

    @Autowired
    public DatasetAuditService(List<DatasetParser> parsers,
                               DuplicateDetector duplicateDetector,
                               AnnotationValidator annotationValidator,
                               ImageIntegrityAnalyzer imageIntegrityAnalyzer,
                               DatasetStatisticsService statisticsService,
                               DatasetRepository datasetRepository,
                               DatasetAuditRepository auditRepository,
                               DatasetFindingRepository datasetFindingRepository,
                               SecurityFindingRepository securityFindingRepository,
                               AuditHistoryRepository auditHistoryRepository,
                               SettingRepository settingRepository,
                               RiskAssessmentService riskAssessmentService) {
        this.parsers = parsers;
        this.duplicateDetector = duplicateDetector;
        this.annotationValidator = annotationValidator;
        this.imageIntegrityAnalyzer = imageIntegrityAnalyzer;
        this.statisticsService = statisticsService;
        this.datasetRepository = datasetRepository;
        this.auditRepository = auditRepository;
        this.datasetFindingRepository = datasetFindingRepository;
        this.securityFindingRepository = securityFindingRepository;
        this.auditHistoryRepository = auditHistoryRepository;
        this.settingRepository = settingRepository;
        this.riskAssessmentService = riskAssessmentService;
    }

    public DatasetAuditService(List<DatasetParser> parsers,
                               DuplicateDetector duplicateDetector,
                               AnnotationValidator annotationValidator,
                               ImageIntegrityAnalyzer imageIntegrityAnalyzer,
                               DatasetStatisticsService statisticsService,
                               DatasetRepository datasetRepository,
                               DatasetAuditRepository auditRepository,
                               DatasetFindingRepository datasetFindingRepository,
                               SecurityFindingRepository securityFindingRepository,
                               AuditHistoryRepository auditHistoryRepository,
                               SettingRepository settingRepository) {
        this(parsers, duplicateDetector, annotationValidator, imageIntegrityAnalyzer,
             statisticsService, datasetRepository, auditRepository, datasetFindingRepository,
             securityFindingRepository, auditHistoryRepository, settingRepository, null);
    }

    /**
     * Executes a complete dataset integrity audit synchronously.
     */
    public DatasetAudit executeAudit(Long auditId) {
        Optional<DatasetAudit> auditOpt = auditRepository.findById(auditId);
        if (auditOpt.isEmpty()) {
            throw new IllegalArgumentException("Dataset audit not found: " + auditId);
        }

        DatasetAudit audit = auditOpt.get();
        Optional<Dataset> datasetOpt = datasetRepository.findById(audit.getDatasetId());
        if (datasetOpt.isEmpty()) {
            audit.setStatus(AuditStatus.FAILED);
            audit.setErrorMessage("Associated dataset not found: " + audit.getDatasetId());
            return auditRepository.save(audit);
        }

        Dataset dataset = datasetOpt.get();
        File datasetDir = new File(dataset.getPath());
        if (!datasetDir.exists() || !datasetDir.isDirectory()) {
            audit.setStatus(AuditStatus.FAILED);
            audit.setErrorMessage("Dataset directory does not exist on disk: " + dataset.getPath());
            if (riskAssessmentService != null) {
                riskAssessmentService.recordCheckStatus("dataset.integrity", audit.getId(),
                        String.valueOf(dataset.getId()), dataset.getSha256Hash(), CheckExecutionStatus.FAILED, "Dataset directory does not exist on disk");
            }
            return auditRepository.save(audit);
        }

        audit.setStatus(AuditStatus.RUNNING);
        audit.setStartedAt(LocalDateTime.now());
        auditRepository.save(audit);
        if (riskAssessmentService != null) {
            riskAssessmentService.recordCheckStatus("dataset.integrity", audit.getId(),
                    String.valueOf(dataset.getId()), dataset.getSha256Hash(), CheckExecutionStatus.NOT_EXECUTED, "Dataset audit in progress");
        }

        try {
            // 1. Select appropriate parser using Strategy pattern
            DatasetParser parser = parsers.stream()
                    .filter(p -> p.getFormat() == dataset.getFormat() || p.canParse(datasetDir))
                    .findFirst()
                    .orElseThrow(() -> new DatasetParsingException("No compatible parser found for dataset: " + dataset.getName()));

            logger.info("Executing dataset audit for: {} using parser: {}", dataset.getName(), parser.getClass().getSimpleName());
            DatasetModels.ParseResult parseResult = parser.parse(datasetDir);

            // Update dataset basic metrics
            dataset.setTotalImages(parseResult.images.size());
            dataset.setTotalAnnotations(parseResult.annotations.size());
            dataset.setTotalClasses(parseResult.categories.size());
            datasetRepository.save(dataset);

            List<SecurityFinding> allFindings = new ArrayList<>();

            // Add parser-level errors/warnings as findings
            int pErrIdx = 1;
            for (String err : parseResult.parsingErrors) {
                allFindings.add(SecurityFinding.builder()
                        .findingId("FIND-PARSE-ERR-" + pErrIdx++)
                        .module("DATASET")
                        .category("Format Parsing")
                        .description(err)
                        .severity(FindingSeverity.HIGH)
                        .confidence(1.0)
                        .evidence("Parser message: " + err)
                        .affectedAsset(dataset.getName())
                        .recommendedAction("Correct the dataset structure or format specification.")
                        .sourceAuditId(audit.getId())
                        .sourceAuditType("DATASET")
                        .build());
            }

            // 2. Image-level Integrity Analysis
            ImageIntegrityAnalyzer.ImageIntegrityResult imgResult =
                    imageIntegrityAnalyzer.analyze(datasetDir, parseResult.images, audit.getId());
            allFindings.addAll(imgResult.findings);

            // 3. Duplicate Detection (Exact & Perceptual)
            int dHashThreshold = settingRepository.getInt("perceptual_hash_threshold", 10);
            DuplicateDetector.DuplicateResult dupResult =
                    duplicateDetector.detectDuplicates(datasetDir, parseResult.images, dHashThreshold, audit.getId());
            allFindings.addAll(dupResult.findings);

            // 4. Annotation Validation
            AnnotationValidator.ValidationResult annResult =
                    annotationValidator.validate(parseResult, audit.getId());
            allFindings.addAll(annResult.findings);

            // 5. Dataset Statistics & Contributor Analysis
            DatasetStatisticsService.DatasetStats stats =
                    statisticsService.calculateStatistics(parseResult, allFindings, audit.getId());
            allFindings.addAll(stats.findings);

            // Handle empty dataset: 0 images must never produce ACCEPT
            if (parseResult.images.isEmpty()) {
                stats.integrityScore = 0.0;
                allFindings.add(SecurityFinding.builder()
                        .findingId("FIND-DATASET-EMPTY-1")
                        .module("DATASET")
                        .category("Dataset Integrity")
                        .description("Dataset contains zero valid image files (empty dataset).")
                        .severity(FindingSeverity.HIGH)
                        .confidence(1.0)
                        .evidence("Total parsed images: 0")
                        .affectedAsset(dataset.getName())
                        .recommendedAction("Populate dataset with valid images before auditing.")
                        .sourceAuditId(audit.getId())
                        .sourceAuditType("DATASET")
                        .build());
            }

            // 6. Persist Findings
            int crit = 0, high = 0, med = 0, low = 0, info = 0;
            for (SecurityFinding f : allFindings) {
                datasetFindingRepository.save(f, dataset.getId());
                securityFindingRepository.save(f);

                switch (f.getSeverity()) {
                    case CRITICAL -> crit++;
                    case HIGH -> high++;
                    case MEDIUM -> med++;
                    case LOW -> low++;
                    case INFO -> info++;
                }
            }

            // Compute deterministic directory hash of the extracted dataset
            String dirHash = null;
            try {
                dirHash = HashUtil.calculateDatasetDirectoryHash(datasetDir);
                dataset.setSha256Hash(dirHash);
                datasetRepository.save(dataset);
            } catch (Exception e) {
                logger.warn("Could not calculate directory content hash for dataset {}: {}", dataset.getName(), e.getMessage());
            }

            // 7. Calculate Decision (ACCEPT, REVIEW, QUARANTINE) - fails closed if hashing failed
            AssessmentDecision decision;
            if (crit > 0 || stats.integrityScore < 50.0) {
                decision = AssessmentDecision.QUARANTINE;
            } else if (high > 0 || stats.integrityScore < 80.0 || parseResult.images.isEmpty() || dirHash == null) {
                decision = AssessmentDecision.REVIEW;
            } else {
                decision = AssessmentDecision.ACCEPT;
            }

            // Record explicit structured check execution status
            if (riskAssessmentService != null) {
                CheckExecutionStatus integrityStatus;
                if (parseResult.images.isEmpty() || crit > 0 || stats.integrityScore < 50.0 || !parseResult.parsingErrors.isEmpty() || dirHash == null) {
                    integrityStatus = CheckExecutionStatus.FAILED;
                } else {
                    integrityStatus = CheckExecutionStatus.PASSED;
                }
                String recordedHash = dirHash != null ? dirHash : dataset.getSha256Hash();
                riskAssessmentService.recordCheckStatus("dataset.integrity", audit.getId(),
                        String.valueOf(dataset.getId()), recordedHash, integrityStatus,
                        "Dataset integrity score: " + stats.integrityScore);
            }

            audit.setCriticalFindings(crit);
            audit.setHighFindings(high);
            audit.setMediumFindings(med);
            audit.setLowFindings(low);
            audit.setInfoFindings(info);
            audit.setTotalFindings(allFindings.size());
            audit.setIntegrityScore(stats.integrityScore);
            audit.setAssessmentDecision(decision);
            audit.setStatus(AuditStatus.COMPLETED);
            audit.setCompletedAt(LocalDateTime.now());
            audit.setSummary(String.format("Audited %d images, %d annotations across %d classes. Integrity score: %.1f/100. Findings: %d.",
                    stats.totalImages, stats.totalAnnotations, stats.totalClasses, stats.integrityScore, allFindings.size()));

            auditRepository.save(audit);

            // Record in Audit History
            AuditHistory history = new AuditHistory();
            history.setAuditType("DATASET");
            history.setTargetName(dataset.getName());
            history.setTargetId(dataset.getId());
            history.setStatus("COMPLETED");
            history.setFindingsCount(allFindings.size());
            history.setAssessmentDecision(decision.name());
            history.setStartedAt(audit.getStartedAt());
            history.setCompletedAt(audit.getCompletedAt());
            history.setSummary(audit.getSummary());
            auditHistoryRepository.save(history);

            logger.info("Dataset audit completed for: {}. Decision: {}", dataset.getName(), decision);
            return audit;

        } catch (Exception e) {
            logger.error("Dataset audit failed for: {}", dataset.getName(), e);
            if (riskAssessmentService != null) {
                riskAssessmentService.recordCheckStatus("dataset.integrity", audit.getId(),
                        String.valueOf(dataset.getId()), dataset.getSha256Hash(), CheckExecutionStatus.FAILED, e.getMessage());
            }
            audit.setStatus(AuditStatus.FAILED);
            audit.setCompletedAt(LocalDateTime.now());
            audit.setErrorMessage(e.getMessage());
            return auditRepository.save(audit);
        }
    }

    /**
     * Executes a dataset audit asynchronously.
     */
    @Async("auditTaskExecutor")
    public CompletableFuture<DatasetAudit> executeAuditAsync(Long auditId) {
        try {
            DatasetAudit result = executeAudit(auditId);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            logger.error("Async dataset audit execution failed for audit ID {}", auditId, e);
            return CompletableFuture.failedFuture(e);
        }
    }
}
