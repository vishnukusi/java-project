package com.sentinelcv.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelcv.entity.AuditHistory;
import com.sentinelcv.entity.MlModel;
import com.sentinelcv.entity.ModelAudit;
import com.sentinelcv.entity.SecurityFinding;
import com.sentinelcv.enums.AssessmentDecision;
import com.sentinelcv.enums.AuditStatus;
import com.sentinelcv.enums.CheckExecutionStatus;
import com.sentinelcv.enums.FindingSeverity;
import com.sentinelcv.model.BehavioralAnalyzer;
import com.sentinelcv.model.ModelFingerprintService;
import com.sentinelcv.model.ModelLoader;
import com.sentinelcv.model.ModelMetadata;
import com.sentinelcv.model.TriggerAnalyzer;
import com.sentinelcv.repository.AuditHistoryRepository;
import com.sentinelcv.repository.MlModelRepository;
import com.sentinelcv.repository.ModelAuditRepository;
import com.sentinelcv.repository.ModelFindingRepository;
import com.sentinelcv.repository.SecurityFindingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
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
 * Service orchestrating the complete model audit pipeline:
 * cryptographic identity, tensor metadata inspection, behavioral baseline testing,
 * trigger perturbation analysis, and security finding aggregation.
 */
@Service
public class ModelAuditService {

    private static final Logger logger = LoggerFactory.getLogger(ModelAuditService.class);

    private final List<ModelLoader> modelLoaders;
    private final ModelFingerprintService fingerprintService;
    private final BehavioralAnalyzer behavioralAnalyzer;
    private final TriggerAnalyzer triggerAnalyzer;
    private final MlModelRepository modelRepository;
    private final ModelAuditRepository auditRepository;
    private final ModelFindingRepository modelFindingRepository;
    private final SecurityFindingRepository securityFindingRepository;
    private final AuditHistoryRepository auditHistoryRepository;
    private final RiskAssessmentService riskAssessmentService;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private PlatformTransactionManager transactionManager;

    @Autowired
    public ModelAuditService(List<ModelLoader> modelLoaders,
                             ModelFingerprintService fingerprintService,
                             BehavioralAnalyzer behavioralAnalyzer,
                             TriggerAnalyzer triggerAnalyzer,
                             MlModelRepository modelRepository,
                             ModelAuditRepository auditRepository,
                             ModelFindingRepository modelFindingRepository,
                             SecurityFindingRepository securityFindingRepository,
                             AuditHistoryRepository auditHistoryRepository,
                             RiskAssessmentService riskAssessmentService,
                             ObjectMapper objectMapper) {
        this.modelLoaders = modelLoaders;
        this.fingerprintService = fingerprintService;
        this.behavioralAnalyzer = behavioralAnalyzer;
        this.triggerAnalyzer = triggerAnalyzer;
        this.modelRepository = modelRepository;
        this.auditRepository = auditRepository;
        this.modelFindingRepository = modelFindingRepository;
        this.securityFindingRepository = securityFindingRepository;
        this.auditHistoryRepository = auditHistoryRepository;
        this.riskAssessmentService = riskAssessmentService;
        this.objectMapper = objectMapper;
    }

    public ModelAuditService(List<ModelLoader> modelLoaders,
                             ModelFingerprintService fingerprintService,
                             BehavioralAnalyzer behavioralAnalyzer,
                             TriggerAnalyzer triggerAnalyzer,
                             MlModelRepository modelRepository,
                             ModelAuditRepository auditRepository,
                             ModelFindingRepository modelFindingRepository,
                             SecurityFindingRepository securityFindingRepository,
                             AuditHistoryRepository auditHistoryRepository,
                             ObjectMapper objectMapper) {
        this(modelLoaders, fingerprintService, behavioralAnalyzer, triggerAnalyzer,
             modelRepository, auditRepository, modelFindingRepository, securityFindingRepository,
             auditHistoryRepository, null, objectMapper);
    }

    /**
     * Executes a complete model audit synchronously.
     */
    public ModelAudit executeAudit(Long auditId) {
        Optional<ModelAudit> auditOpt = auditRepository.findById(auditId);
        if (auditOpt.isEmpty()) {
            throw new IllegalArgumentException("Model audit not found: " + auditId);
        }

        ModelAudit audit = auditOpt.get();
        MlModel mlModel = null;

        try {
            Optional<MlModel> modelOpt = modelRepository.findById(audit.getModelId());
            if (modelOpt.isEmpty()) {
                return markAuditFailedSafely(audit, null, "Associated ML model not found: " + audit.getModelId(), null);
            }

            mlModel = modelOpt.get();
            File modelFile = new File(mlModel.getPath());
            if (!modelFile.exists() || !modelFile.isFile()) {
                return markAuditFailedSafely(
                        audit,
                        String.valueOf(mlModel.getId()),
                        "Model file does not exist on disk: " + mlModel.getPath(),
                        mlModel.getSha256Hash()
                );
            }

            audit.setStatus(AuditStatus.RUNNING);
            audit.setStartedAt(LocalDateTime.now());
            audit.setCompletedAt(null);
            audit.setErrorMessage(null);
            audit.setAssessmentDecision(null);
            auditRepository.save(audit);
            if (riskAssessmentService != null) {
                riskAssessmentService.recordCheckStatus("model.baseline_inference", audit.getId(), String.valueOf(mlModel.getId()), mlModel.getSha256Hash(), CheckExecutionStatus.NOT_EXECUTED, "Audit in progress");
                riskAssessmentService.recordCheckStatus("model.trigger_perturbation", audit.getId(), String.valueOf(mlModel.getId()), mlModel.getSha256Hash(), CheckExecutionStatus.NOT_EXECUTED, "Audit in progress");
            }

            // 1. Select appropriate ModelLoader
            final var modelFormat = mlModel.getFormat();
            ModelLoader loader = modelLoaders.stream()
                    .filter(l -> l.canLoad(modelFile))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No loader available for model format: " + modelFormat));

            List<SecurityFinding> allFindings = new ArrayList<>();

            // 2. Metadata Inspection
            ModelMetadata metadata = loader.inspectMetadata(modelFile);
            mlModel.setMetadataJson(objectMapper.writeValueAsString(metadata));
            mlModel.setInputInfo(metadata.inputTensors.toString());
            mlModel.setOutputInfo(metadata.outputTensors.toString());

            // 3. Cryptographic Fingerprint
            ModelFingerprintService.FingerprintResult fpResult =
                    fingerprintService.computeFingerprint(modelFile, metadata);

            mlModel.setSha256Hash(fpResult.sha256);
            mlModel.setFileSizeBytes(fpResult.fileSizeBytes);

            audit.setBehavioralFingerprint(fpResult.structuralFingerprint);

            // 4. Behavioral Baseline Testing
            BehavioralAnalyzer.BehavioralResult behavioralResult =
                    behavioralAnalyzer.analyzeBehavior(loader, modelFile, metadata, audit.getId());
            allFindings.addAll(behavioralResult.findings);

            // 5. Trigger Perturbation Analysis
            TriggerAnalyzer.TriggerAnalysisReport triggerReport =
                    triggerAnalyzer.analyzePerturbations(loader, modelFile, metadata, behavioralResult, audit.getId());
            allFindings.addAll(triggerReport.findings);

            // 6. Tally Findings
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

            // 7. Determine Assessment Decision
            AssessmentDecision decision;
            if (crit > 0) {
                decision = AssessmentDecision.QUARANTINE;
            } else if (high > 0 || triggerReport.unusualSensitivityDetected) {
                decision = AssessmentDecision.REVIEW;
            } else if (!metadata.inferenceSupported || !behavioralResult.inferenceExecuted || !triggerReport.executed) {
                // Unsupported runtime, behavioral failure, or incomplete trigger perturbation check mandate REVIEW
                decision = AssessmentDecision.REVIEW;
            } else {
                decision = AssessmentDecision.ACCEPT;
            }

            final MlModel persistedModel = mlModel;
            final int finalCrit = crit;
            final int finalHigh = high;
            final int finalMed = med;
            final int finalLow = low;
            final int finalInfo = info;

            // Persist model metadata, findings, check statuses, and COMPLETED transition atomically
            runInTransaction(() -> {
                modelRepository.save(persistedModel);

                for (SecurityFinding f : allFindings) {
                    modelFindingRepository.save(f, persistedModel.getId());
                    securityFindingRepository.save(f);
                }

                audit.setCriticalFindings(finalCrit);
                audit.setHighFindings(finalHigh);
                audit.setMediumFindings(finalMed);
                audit.setLowFindings(finalLow);
                audit.setInfoFindings(finalInfo);
                audit.setTotalFindings(allFindings.size());
                audit.setAssessmentDecision(decision);
                audit.setErrorMessage(null);
                audit.setStatus(AuditStatus.COMPLETED);
                audit.setCompletedAt(LocalDateTime.now());
                audit.setSummary(String.format("Audited %s model (%d bytes). Deterministic: %b. Perturbation tests: %d. Findings: %d. Decision: %s.",
                        metadata.format, fpResult.fileSizeBytes, behavioralResult.deterministic, triggerReport.tests.size(), allFindings.size(), decision));

                if (riskAssessmentService != null) {
                    CheckExecutionStatus baselineStatus;
                    if (!metadata.inferenceSupported) {
                        baselineStatus = CheckExecutionStatus.UNAVAILABLE;
                    } else if (!behavioralResult.inferenceExecuted || !behavioralResult.deterministic) {
                        baselineStatus = CheckExecutionStatus.FAILED;
                    } else {
                        baselineStatus = CheckExecutionStatus.PASSED;
                    }
                    riskAssessmentService.recordCheckStatus("model.baseline_inference", audit.getId(),
                            String.valueOf(persistedModel.getId()), persistedModel.getSha256Hash(), baselineStatus,
                            "Behavioral baseline: " + baselineStatus);

                    CheckExecutionStatus triggerStatus;
                    if (!metadata.inferenceSupported || !behavioralResult.inferenceExecuted) {
                        triggerStatus = CheckExecutionStatus.UNAVAILABLE;
                    } else if (!triggerReport.executed || triggerReport.unusualSensitivityDetected) {
                        triggerStatus = CheckExecutionStatus.FAILED;
                    } else {
                        triggerStatus = CheckExecutionStatus.PASSED;
                    }
                    riskAssessmentService.recordCheckStatus("model.trigger_perturbation", audit.getId(),
                            String.valueOf(persistedModel.getId()), persistedModel.getSha256Hash(), triggerStatus,
                            "Trigger perturbation: " + triggerStatus);
                }

                auditRepository.save(audit);

                AuditHistory history = new AuditHistory();
                history.setAuditType("MODEL");
                history.setTargetName(persistedModel.getName());
                history.setTargetId(persistedModel.getId());
                history.setStatus("COMPLETED");
                history.setFindingsCount(allFindings.size());
                history.setAssessmentDecision(decision.name());
                history.setStartedAt(audit.getStartedAt());
                history.setCompletedAt(audit.getCompletedAt());
                history.setSummary(audit.getSummary());
                auditHistoryRepository.save(history);
            });

            logger.info("Model audit completed for: {}. Decision: {}", mlModel.getName(), decision);
            return audit;

        } catch (Throwable t) {
            String assetLabel = mlModel != null ? mlModel.getName() : "auditId=" + auditId;
            logger.error("Model audit failed for: {}", assetLabel, t);
            String modelIdStr = mlModel != null && mlModel.getId() != null
                    ? String.valueOf(mlModel.getId())
                    : (audit.getModelId() != null ? String.valueOf(audit.getModelId()) : null);
            String sha = mlModel != null ? mlModel.getSha256Hash() : null;
            markAuditFailedSafely(audit, modelIdStr, extractErrorMessage(t), sha);
            if (t instanceof Error err) {
                throw err;
            }
            return audit;
        }
    }

    @Async("auditTaskExecutor")
    public CompletableFuture<ModelAudit> executeAuditAsync(Long auditId) {
        try {
            ModelAudit result = executeAudit(auditId);
            return CompletableFuture.completedFuture(result);
        } catch (Throwable t) {
            logger.error("Async model audit execution failed for audit ID {}", auditId, t);
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

    private ModelAudit markAuditFailedSafely(ModelAudit audit, String modelIdStr, String message, String modelHash) {
        audit.setStatus(AuditStatus.FAILED);
        audit.setCompletedAt(LocalDateTime.now());
        audit.setAssessmentDecision(null);
        audit.setErrorMessage(message);
        if (riskAssessmentService != null && modelIdStr != null) {
            try {
                riskAssessmentService.recordCheckStatus("model.baseline_inference", audit.getId(),
                        modelIdStr, modelHash, CheckExecutionStatus.FAILED, message);
                riskAssessmentService.recordCheckStatus("model.trigger_perturbation", audit.getId(),
                        modelIdStr, modelHash, CheckExecutionStatus.FAILED, message);
            } catch (Exception ex) {
                logger.warn("Could not record failed check statuses for model audit {}: {}", audit.getId(), ex.getMessage());
            }
        }
        try {
            return auditRepository.save(audit);
        } catch (Exception ex) {
            logger.error("Failed to persist FAILED status for model audit {}: {}", audit.getId(), ex.getMessage());
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
            logger.error("Could not mark orphaned model audit {} as FAILED: {}", auditId, ex.getMessage());
        }
    }

    private static String extractErrorMessage(Throwable t) {
        if (t == null) {
            return "Unknown model audit execution error";
        }
        String msg = t.getMessage();
        return (msg != null && !msg.isBlank()) ? msg : t.getClass().getSimpleName();
    }
}
