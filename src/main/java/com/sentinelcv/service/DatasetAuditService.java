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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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

    @Autowired(required = false)
    private PlatformTransactionManager transactionManager;

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
        Dataset dataset = null;

        try {
            Optional<Dataset> datasetOpt = datasetRepository.findById(audit.getDatasetId());
            if (datasetOpt.isEmpty()) {
                return markAuditFailedSafely(audit, null, "Associated dataset not found: " + audit.getDatasetId(), null);
            }

            dataset = datasetOpt.get();
            File datasetDir = new File(dataset.getPath());
            if (!datasetDir.exists() || !datasetDir.isDirectory()) {
                return markAuditFailedSafely(
                        audit,
                        String.valueOf(dataset.getId()),
                        "Dataset directory does not exist on disk: " + dataset.getPath(),
                        dataset.getSha256Hash()
                );
            }

            audit.setStatus(AuditStatus.RUNNING);
            audit.setStartedAt(LocalDateTime.now());
            audit.setCompletedAt(null);
            audit.setErrorMessage(null);
            audit.setAssessmentDecision(null);
            auditRepository.save(audit);
            if (riskAssessmentService != null) {
                riskAssessmentService.recordCheckStatus("dataset.integrity", audit.getId(),
                        String.valueOf(dataset.getId()), dataset.getSha256Hash(), CheckExecutionStatus.NOT_EXECUTED, "Dataset audit in progress");
            }

            // 1. Strictly select parser matching the persisted authoritative dataset format
            DatasetParser parser = DatasetParser.resolveAuthoritativeParser(parsers, dataset);

            logger.info("Executing dataset audit (auditId={}, datasetId={}, name='{}', persistedFormat={}) using parser: {}",
                    audit.getId(), dataset.getId(), dataset.getName(), dataset.getFormat(), parser.getClass().getSimpleName());
            DatasetModels.ParseResult parseResult = parser.parse(datasetDir);

            // Prepare dataset metrics
            dataset.setTotalImages(parseResult.images.size());
            dataset.setTotalAnnotations(parseResult.annotations.size());
            dataset.setTotalClasses(parseResult.categories.size());

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

            // Compute deterministic directory hash of the extracted dataset
            String dirHash = null;
            try {
                dirHash = HashUtil.calculateDatasetDirectoryHash(datasetDir);
                dataset.setSha256Hash(dirHash);
            } catch (Exception e) {
                logger.warn("Could not calculate directory content hash for dataset {}: {}", dataset.getName(), e.getMessage());
            }

            int crit = 0, high = 0, med = 0, low = 0, info = 0;
            for (SecurityFinding f : allFindings) {
                switch (f.getSeverity()) {
                    case CRITICAL -> crit++;
                    case HIGH -> high++;
                    case MEDIUM -> med++;
                    case LOW -> low++;
                    case INFO -> info++;
                }
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

            final Dataset persistedDataset = dataset;
            final String recordedHash = dirHash != null ? dirHash : dataset.getSha256Hash();
            final boolean dirHashPresent = dirHash != null;
            final int finalCrit = crit;
            final int finalHigh = high;
            final int finalMed = med;
            final int finalLow = low;
            final int finalInfo = info;

            // 6 & 7b. Persist findings, metrics, check status, and COMPLETED state atomically
            // so findings are always durable before/with the COMPLETED transition.
            runInTransaction(() -> {
                for (SecurityFinding f : allFindings) {
                    datasetFindingRepository.save(f, persistedDataset.getId());
                    securityFindingRepository.save(f);
                }

                datasetRepository.save(persistedDataset);

                if (riskAssessmentService != null) {
                    CheckExecutionStatus integrityStatus;
                    if (parseResult.images.isEmpty() || finalCrit > 0 || stats.integrityScore < 50.0
                            || !parseResult.parsingErrors.isEmpty() || !dirHashPresent) {
                        integrityStatus = CheckExecutionStatus.FAILED;
                    } else {
                        integrityStatus = CheckExecutionStatus.PASSED;
                    }
                    riskAssessmentService.recordCheckStatus("dataset.integrity", audit.getId(),
                            String.valueOf(persistedDataset.getId()), recordedHash, integrityStatus,
                            "Dataset integrity score: " + stats.integrityScore);
                }

                audit.setCriticalFindings(finalCrit);
                audit.setHighFindings(finalHigh);
                audit.setMediumFindings(finalMed);
                audit.setLowFindings(finalLow);
                audit.setInfoFindings(finalInfo);
                audit.setTotalFindings(allFindings.size());
                audit.setIntegrityScore(stats.integrityScore);
                audit.setAssessmentDecision(decision);
                audit.setErrorMessage(null);
                audit.setStatus(AuditStatus.COMPLETED);
                audit.setCompletedAt(LocalDateTime.now());
                audit.setSummary(String.format("Audited %d images, %d annotations across %d classes. Integrity score: %.1f/100. Findings: %d.",
                        stats.totalImages, stats.totalAnnotations, stats.totalClasses, stats.integrityScore, allFindings.size()));

                auditRepository.save(audit);

                AuditHistory history = new AuditHistory();
                history.setAuditType("DATASET");
                history.setTargetName(persistedDataset.getName());
                history.setTargetId(persistedDataset.getId());
                history.setStatus("COMPLETED");
                history.setFindingsCount(allFindings.size());
                history.setAssessmentDecision(decision.name());
                history.setStartedAt(audit.getStartedAt());
                history.setCompletedAt(audit.getCompletedAt());
                history.setSummary(audit.getSummary());
                auditHistoryRepository.save(history);
            });

            logger.info("Dataset audit completed for: {}. Decision: {}", dataset.getName(), decision);
            return audit;

        } catch (Throwable t) {
            String assetLabel = dataset != null ? dataset.getName() : "auditId=" + auditId;
            logger.error("Dataset audit failed for: {}", assetLabel, t);
            String targetIdStr = dataset != null && dataset.getId() != null
                    ? String.valueOf(dataset.getId())
                    : (audit.getDatasetId() != null ? String.valueOf(audit.getDatasetId()) : null);
            String sha = dataset != null ? dataset.getSha256Hash() : null;
            markAuditFailedSafely(audit, targetIdStr, extractErrorMessage(t), sha);
            if (t instanceof Error err) {
                throw err;
            }
            return audit;
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
        } catch (Throwable t) {
            logger.error("Async dataset audit execution failed for audit ID {}", auditId, t);
            ensureTerminalFailureState(auditId, extractErrorMessage(t));
            return CompletableFuture.failedFuture(t);
        }
    }

    private void runInTransaction(Runnable work) {
        if (transactionManager != null) {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
        } else {
            work.run();
        }
    }

    private DatasetAudit markAuditFailedSafely(DatasetAudit audit, String datasetIdStr, String message, String datasetHash) {
        audit.setStatus(AuditStatus.FAILED);
        audit.setCompletedAt(LocalDateTime.now());
        audit.setAssessmentDecision(null);
        audit.setErrorMessage(message);
        if (riskAssessmentService != null && datasetIdStr != null) {
            try {
                riskAssessmentService.recordCheckStatus("dataset.integrity", audit.getId(),
                        datasetIdStr, datasetHash, CheckExecutionStatus.FAILED, message);
            } catch (Exception ex) {
                logger.warn("Could not record failed check status for audit {}: {}", audit.getId(), ex.getMessage());
            }
        }
        try {
            return auditRepository.save(audit);
        } catch (Exception ex) {
            logger.error("Failed to persist FAILED status for dataset audit {}: {}", audit.getId(), ex.getMessage());
            return audit;
        }
    }

    private void ensureTerminalFailureState(Long auditId, String message) {
        try {
            auditRepository.findById(auditId).ifPresent(existing -> {
                if (existing.getStatus() == AuditStatus.PENDING || existing.getStatus() == AuditStatus.RUNNING) {
                    existing.setStatus(AuditStatus.FAILED);
                    existing.setCompletedAt(LocalDateTime.now());
                    existing.setAssessmentDecision(null);
                    existing.setErrorMessage(message);
                    auditRepository.save(existing);
                }
            });
        } catch (Exception ex) {
            logger.error("Could not mark orphaned dataset audit {} as FAILED: {}", auditId, ex.getMessage());
        }
    }

    private static String extractErrorMessage(Throwable t) {
        if (t == null) {
            return "Unknown dataset audit execution error";
        }
        String msg = t.getMessage();
        return (msg != null && !msg.isBlank()) ? msg : t.getClass().getSimpleName();
    }
}
