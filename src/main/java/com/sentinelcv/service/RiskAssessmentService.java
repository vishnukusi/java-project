package com.sentinelcv.service;

import com.sentinelcv.entity.Dataset;
import com.sentinelcv.entity.DatasetAudit;
import com.sentinelcv.entity.InferenceRecord;
import com.sentinelcv.entity.MandatoryCheckRecord;
import com.sentinelcv.entity.MlModel;
import com.sentinelcv.entity.ModelAudit;
import com.sentinelcv.entity.SecurityFinding;
import com.sentinelcv.enums.AssessmentDecision;
import com.sentinelcv.enums.AuditStatus;
import com.sentinelcv.enums.CheckExecutionStatus;
import com.sentinelcv.enums.FindingSeverity;
import com.sentinelcv.inference.InferenceValidator;
import com.sentinelcv.provenance.ProvenanceKeyManager;
import com.sentinelcv.repository.DatasetAuditRepository;
import com.sentinelcv.repository.DatasetRepository;
import com.sentinelcv.repository.InferenceRecordRepository;
import com.sentinelcv.repository.MandatoryCheckStatusRepository;
import com.sentinelcv.repository.MlModelRepository;
import com.sentinelcv.repository.ModelAuditRepository;
import com.sentinelcv.repository.SecurityFindingRepository;
import com.sentinelcv.repository.SettingRepository;
import com.sentinelcv.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Centralized risk assessment engine aggregating security findings across all modules.
 * Evaluates composite risk score and generates transparent, explainable decisions.
 * Enforces that PASSED mandatory check statuses are valid only for the exact asset version audited.
 */
@Service
public class RiskAssessmentService {

    private static final Logger logger = LoggerFactory.getLogger(RiskAssessmentService.class);

    public static final Set<String> MANDATORY_CHECKS = Set.of(
            "model.baseline_inference",
            "model.trigger_perturbation",
            "dataset.integrity",
            "provenance.verification"
    );

    private final SecurityFindingRepository securityFindingRepository;
    private final SettingRepository settingRepository;
    private final MandatoryCheckStatusRepository checkStatusRepository;
    private final MlModelRepository modelRepository;
    private final ModelAuditRepository modelAuditRepository;
    private final DatasetRepository datasetRepository;
    private final DatasetAuditRepository datasetAuditRepository;
    private final InferenceRecordRepository inferenceRecordRepository;
    private final InferenceValidator inferenceValidator;
    private final ProvenanceKeyManager provenanceKeyManager;

    // Provenance verification caching has been removed in favor of authoritative complete-chain verification.
    // ThreadLocal caches can leak across reused HTTP worker threads and fail to detect tampering of earlier records.
    public void invalidateProvenanceCache() {
        // No-op: caching removed; verification is always live and authoritative.
    }

    @Autowired
    public RiskAssessmentService(SecurityFindingRepository securityFindingRepository,
                                 SettingRepository settingRepository,
                                 MandatoryCheckStatusRepository checkStatusRepository,
                                 MlModelRepository modelRepository,
                                 ModelAuditRepository modelAuditRepository,
                                 DatasetRepository datasetRepository,
                                 DatasetAuditRepository datasetAuditRepository,
                                 InferenceRecordRepository inferenceRecordRepository,
                                 @Autowired(required = false) InferenceValidator inferenceValidator,
                                 @Autowired(required = false) ProvenanceKeyManager provenanceKeyManager) {
        this.securityFindingRepository = securityFindingRepository;
        this.settingRepository = settingRepository;
        this.checkStatusRepository = checkStatusRepository;
        this.modelRepository = modelRepository;
        this.modelAuditRepository = modelAuditRepository;
        this.datasetRepository = datasetRepository;
        this.datasetAuditRepository = datasetAuditRepository;
        this.inferenceRecordRepository = inferenceRecordRepository;
        this.inferenceValidator = inferenceValidator != null ? inferenceValidator : new InferenceValidator();
        this.provenanceKeyManager = provenanceKeyManager;
    }

    public RiskAssessmentService(SecurityFindingRepository securityFindingRepository,
                                 SettingRepository settingRepository,
                                 MandatoryCheckStatusRepository checkStatusRepository,
                                 MlModelRepository modelRepository,
                                 ModelAuditRepository modelAuditRepository,
                                 DatasetRepository datasetRepository,
                                 DatasetAuditRepository datasetAuditRepository,
                                 InferenceRecordRepository inferenceRecordRepository) {
        this(securityFindingRepository, settingRepository, checkStatusRepository, modelRepository,
                modelAuditRepository, datasetRepository, datasetAuditRepository, inferenceRecordRepository,
                new InferenceValidator(), null);
    }

    public RiskAssessmentService(SecurityFindingRepository securityFindingRepository,
                                 SettingRepository settingRepository) {
        this(securityFindingRepository, settingRepository, null, null, null, null, null, null);
    }

    public static class SystemRiskAssessment {
        public AssessmentDecision overallDecision;
        public double compositeRiskScore; // 0.0 (safe) to 100.0 (critical hazard)
        public int totalFindings;
        public int criticalCount;
        public int highCount;
        public int mediumCount;
        public int lowCount;
        public int infoCount;
        public int unavailableChecksCount;
        public int notExecutedChecksCount;
        public int failedChecksCount;
        public int passedChecksCount;
        public Map<String, CheckExecutionStatus> checkStatuses = new HashMap<>();
        public Map<String, Integer> findingsByModule = new HashMap<>();
        public List<String> decisionJustifications = new ArrayList<>();
        public List<SecurityFinding> prioritizedFindings = new ArrayList<>();
    }

    /**
     * Records or updates the execution status of a named security check with full asset provenance metadata.
     */
    public void recordCheckStatus(String checkId, Long auditId, String assetId, String assetHash,
                                  CheckExecutionStatus status, String details) {
        if (checkId == null || status == null) {
            return;
        }
        if (checkStatusRepository != null) {
            MandatoryCheckRecord record = new MandatoryCheckRecord(checkId, auditId, assetId, assetHash, status, details);
            checkStatusRepository.save(record);
        }
        if (settingRepository != null) {
            settingRepository.setValue("check_status." + checkId, status.name());
        }
    }

    /**
     * Overload for recording check status that associates with the active asset version when known.
     */
    public void recordCheckStatus(String checkName, CheckExecutionStatus status) {
        String assetId = null;
        String assetHash = null;
        Long auditId = null;

        if ("model.baseline_inference".equals(checkName) || "model.trigger_perturbation".equals(checkName)) {
            if (modelRepository != null) {
                List<MlModel> models = modelRepository.findAll();
                Optional<MlModel> active = models.stream()
                        .filter(m -> m.getPath() != null && new File(m.getPath()).exists())
                        .findFirst();
                if (active.isPresent()) {
                    MlModel m = active.get();
                    assetId = String.valueOf(m.getId());
                    try {
                        assetHash = HashUtil.sha256(new File(m.getPath()));
                    } catch (Exception e) {
                        assetHash = m.getSha256Hash();
                    }
                    if (modelAuditRepository != null) {
                        List<ModelAudit> audits = modelAuditRepository.findByModelId(m.getId());
                        if (!audits.isEmpty()) {
                            auditId = audits.get(0).getId();
                        }
                    }
                }
            }
        } else if ("dataset.integrity".equals(checkName)) {
            if (datasetRepository != null) {
                List<Dataset> datasets = datasetRepository.findAll();
                Optional<Dataset> active = datasets.stream()
                        .filter(d -> d.getPath() != null && new File(d.getPath()).exists())
                        .findFirst();
                if (active.isPresent()) {
                    Dataset d = active.get();
                    assetId = String.valueOf(d.getId());
                    try {
                        assetHash = HashUtil.calculateDatasetDirectoryHash(new File(d.getPath()));
                    } catch (Exception e) {
                        assetHash = d.getSha256Hash();
                    }
                    if (datasetAuditRepository != null) {
                        List<DatasetAudit> audits = datasetAuditRepository.findByDatasetId(d.getId());
                        if (!audits.isEmpty()) {
                            auditId = audits.get(0).getId();
                        }
                    }
                }
            }
        } else if ("provenance.verification".equals(checkName)) {
            if (inferenceRecordRepository != null) {
                List<InferenceRecord> records = inferenceRecordRepository.findAllOrderedBySequence();
                if (!records.isEmpty()) {
                    assetId = "provenance-chain";
                    assetHash = records.get(records.size() - 1).getCurrentRecordHash();
                }
            }
        }

        recordCheckStatus(checkName, auditId, assetId, assetHash, status, null);
    }

    /**
     * Retrieves the authoritative execution status of a named check.
     * Enforces that PASSED statuses are valid strictly for the exact asset version that was audited.
     * Inactive, missing, stale, or interrupted checks resolve to NOT_EXECUTED, producing REVIEW.
     */
    public CheckExecutionStatus getCheckStatus(String checkName) {
        if (!MANDATORY_CHECKS.contains(checkName)) {
            // Unknown check identifier -> fail closed
            return CheckExecutionStatus.UNAVAILABLE;
        }

        if (checkStatusRepository == null) {
            if (settingRepository != null) {
                return settingRepository.getValue("check_status." + checkName)
                        .map(val -> {
                            try {
                                return CheckExecutionStatus.valueOf(val);
                            } catch (Exception e) {
                                return CheckExecutionStatus.NOT_EXECUTED;
                            }
                        })
                        .orElse(CheckExecutionStatus.NOT_EXECUTED);
            }
            return CheckExecutionStatus.UNAVAILABLE;
        }

        Optional<MandatoryCheckRecord> opt = checkStatusRepository.findById(checkName);
        if (opt.isEmpty()) {
            return CheckExecutionStatus.NOT_EXECUTED;
        }

        MandatoryCheckRecord record = opt.get();
        CheckExecutionStatus recordedStatus = record.getExecutionStatus();
        if (recordedStatus == null) {
            return CheckExecutionStatus.NOT_EXECUTED;
        }
        if (recordedStatus != CheckExecutionStatus.PASSED) {
            return recordedStatus;
        }

        // Validate freshness for PASSED status:
        return validateFreshnessForPassedCheck(checkName, record);
    }

    private CheckExecutionStatus validateFreshnessForPassedCheck(String checkName, MandatoryCheckRecord record) {
        switch (checkName) {
            case "model.baseline_inference", "model.trigger_perturbation" -> {
                if (modelRepository == null) return CheckExecutionStatus.UNAVAILABLE;
                List<MlModel> models;
                try {
                    models = modelRepository.findAll();
                } catch (Exception e) {
                    return CheckExecutionStatus.UNAVAILABLE;
                }
                if (models.isEmpty() || record.getAssetId() == null) {
                    return CheckExecutionStatus.NOT_EXECUTED;
                }
                Long modelId;
                try {
                    modelId = Long.parseLong(record.getAssetId());
                } catch (NumberFormatException e) {
                    return CheckExecutionStatus.NOT_EXECUTED;
                }
                Optional<MlModel> modelOpt = modelRepository.findById(modelId);
                if (modelOpt.isEmpty()) {
                    return CheckExecutionStatus.NOT_EXECUTED;
                }
                MlModel model = modelOpt.get();
                if (model.getPath() == null) {
                    return CheckExecutionStatus.NOT_EXECUTED;
                }
                File modelFile = new File(model.getPath());
                if (!modelFile.exists() || !modelFile.isFile()) {
                    return CheckExecutionStatus.UNAVAILABLE;
                }
                String currentFileHash;
                try {
                    currentFileHash = HashUtil.sha256(modelFile);
                } catch (Exception e) {
                    return CheckExecutionStatus.NOT_EXECUTED;
                }
                if (record.getAssetHash() == null || !currentFileHash.equalsIgnoreCase(record.getAssetHash())) {
                    return CheckExecutionStatus.NOT_EXECUTED;
                }
                if (model.getSha256Hash() == null || !currentFileHash.equalsIgnoreCase(model.getSha256Hash())) {
                    return CheckExecutionStatus.NOT_EXECUTED;
                }
                if (modelAuditRepository == null) {
                    return CheckExecutionStatus.UNAVAILABLE;
                }
                if (record.getAuditId() != null) {
                    Optional<ModelAudit> auditOpt = modelAuditRepository.findById(record.getAuditId());
                    if (auditOpt.isEmpty() || auditOpt.get().getStatus() != AuditStatus.COMPLETED) {
                        return CheckExecutionStatus.NOT_EXECUTED;
                    }
                }
                List<ModelAudit> audits = modelAuditRepository.findByModelId(modelId);
                Optional<ModelAudit> latestOpt = audits.stream().max(Comparator.comparing(ModelAudit::getId));
                if (latestOpt.isPresent()) {
                    ModelAudit latest = latestOpt.get();
                    if (record.getAuditId() == null || latest.getId() > record.getAuditId()) {
                        if (latest.getStatus() == AuditStatus.FAILED) {
                            return CheckExecutionStatus.FAILED;
                        }
                        if (latest.getStatus() != AuditStatus.COMPLETED) {
                            return CheckExecutionStatus.NOT_EXECUTED;
                        }
                    }
                }
                return CheckExecutionStatus.PASSED;
            }
            case "dataset.integrity" -> {
                if (datasetRepository == null) return CheckExecutionStatus.UNAVAILABLE;
                List<Dataset> datasets;
                try {
                    datasets = datasetRepository.findAll();
                } catch (Exception e) {
                    return CheckExecutionStatus.UNAVAILABLE;
                }
                if (datasets.isEmpty() || record.getAssetId() == null) {
                    return CheckExecutionStatus.NOT_EXECUTED;
                }
                Long datasetId;
                try {
                    datasetId = Long.parseLong(record.getAssetId());
                } catch (NumberFormatException e) {
                    return CheckExecutionStatus.NOT_EXECUTED;
                }
                Optional<Dataset> dsOpt = datasetRepository.findById(datasetId);
                if (dsOpt.isEmpty()) {
                    return CheckExecutionStatus.NOT_EXECUTED;
                }
                Dataset ds = dsOpt.get();
                if (ds.getPath() == null) {
                    return CheckExecutionStatus.NOT_EXECUTED;
                }
                File dsDir = new File(ds.getPath());
                if (!dsDir.exists() || !dsDir.isDirectory()) {
                    return CheckExecutionStatus.UNAVAILABLE;
                }
                String currentDirHash;
                try {
                    currentDirHash = HashUtil.calculateDatasetDirectoryHash(dsDir);
                } catch (Exception e) {
                    return CheckExecutionStatus.NOT_EXECUTED;
                }
                if (record.getAssetHash() == null || !currentDirHash.equalsIgnoreCase(record.getAssetHash())) {
                    return CheckExecutionStatus.NOT_EXECUTED;
                }
                if (ds.getSha256Hash() != null && !currentDirHash.equalsIgnoreCase(ds.getSha256Hash())) {
                    return CheckExecutionStatus.NOT_EXECUTED;
                }
                if (datasetAuditRepository == null) {
                    return CheckExecutionStatus.UNAVAILABLE;
                }
                if (record.getAuditId() != null) {
                    Optional<DatasetAudit> auditOpt = datasetAuditRepository.findById(record.getAuditId());
                    if (auditOpt.isEmpty() || auditOpt.get().getStatus() != AuditStatus.COMPLETED) {
                        return CheckExecutionStatus.NOT_EXECUTED;
                    }
                }
                List<DatasetAudit> audits = datasetAuditRepository.findByDatasetId(datasetId);
                Optional<DatasetAudit> latestOpt = audits.stream().max(Comparator.comparing(DatasetAudit::getId));
                if (latestOpt.isPresent()) {
                    DatasetAudit latest = latestOpt.get();
                    if (record.getAuditId() == null || latest.getId() > record.getAuditId()) {
                        if (latest.getStatus() == AuditStatus.FAILED) {
                            return CheckExecutionStatus.FAILED;
                        }
                        if (latest.getStatus() != AuditStatus.COMPLETED) {
                            return CheckExecutionStatus.NOT_EXECUTED;
                        }
                    }
                }
                return CheckExecutionStatus.PASSED;
            }
            case "provenance.verification" -> {
                if (inferenceRecordRepository == null) return CheckExecutionStatus.UNAVAILABLE;
                List<InferenceRecord> records;
                try {
                    records = inferenceRecordRepository.findAllOrderedBySequence();
                } catch (Exception e) {
                    logger.error("Failed to query inference records: {}", e.getMessage());
                    return CheckExecutionStatus.FAILED;
                }
                if (records.isEmpty()) {
                    return CheckExecutionStatus.NOT_EXECUTED;
                }
                if (provenanceKeyManager == null) {
                    return CheckExecutionStatus.UNAVAILABLE;
                }
                Optional<String> keyOpt = provenanceKeyManager.getVerificationKey();
                if (keyOpt.isEmpty()) {
                    return CheckExecutionStatus.UNAVAILABLE;
                }
                String hmacKey = keyOpt.get();

                try {
                    // Authoritative complete-chain verification on EVERY invocation
                    // ThreadLocal caching has been eliminated to prevent cross-request thread reuse leaks
                    // and to guarantee immediate detection of earlier record tampering.
                    InferenceValidator validator = inferenceValidator != null ? inferenceValidator : new InferenceValidator();
                    InferenceValidator.VerificationBatchResult batchResult = validator.verifyRecords(records, hmacKey);

                    if (batchResult.totalRecordsChecked == 0) {
                        return CheckExecutionStatus.NOT_EXECUTED;
                    } else if (batchResult.hasCryptographicTampering() || batchResult.brokenChainLinks > 0 || batchResult.failedCount > 0) {
                        return CheckExecutionStatus.FAILED;
                    } else if (batchResult.unavailableCount > 0) {
                        return CheckExecutionStatus.UNAVAILABLE;
                    } else if (batchResult.verifiedCount == batchResult.totalRecordsChecked) {
                        InferenceRecord last = records.get(records.size() - 1);
                        if (record.getAssetHash() == null || !record.getAssetHash().equalsIgnoreCase(last.getCurrentRecordHash())) {
                            return CheckExecutionStatus.NOT_EXECUTED;
                        }
                        return CheckExecutionStatus.PASSED;
                    } else {
                        return CheckExecutionStatus.FAILED;
                    }
                } catch (Exception e) {
                    logger.error("Provenance verification failed during freshness validation: {}", e.getMessage());
                    return CheckExecutionStatus.FAILED;
                }
            }
            default -> {
                return CheckExecutionStatus.UNAVAILABLE;
            }
        }
    }

    public void invalidateModelChecks(Long modelId) {
        if (checkStatusRepository != null) {
            checkStatusRepository.invalidate("model.baseline_inference");
            checkStatusRepository.invalidate("model.trigger_perturbation");
        }
        if (settingRepository != null) {
            settingRepository.setValue("check_status.model.baseline_inference", CheckExecutionStatus.NOT_EXECUTED.name());
            settingRepository.setValue("check_status.model.trigger_perturbation", CheckExecutionStatus.NOT_EXECUTED.name());
        }
    }

    public void invalidateDatasetChecks(Long datasetId) {
        if (checkStatusRepository != null) {
            checkStatusRepository.invalidate("dataset.integrity");
        }
        if (settingRepository != null) {
            settingRepository.setValue("check_status.dataset.integrity", CheckExecutionStatus.NOT_EXECUTED.name());
        }
    }

    public void invalidateProvenanceChecks() {
        if (checkStatusRepository != null) {
            checkStatusRepository.invalidate("provenance.verification");
        }
        if (settingRepository != null) {
            settingRepository.setValue("check_status.provenance.verification", CheckExecutionStatus.NOT_EXECUTED.name());
        }
    }

    public void recoverInterruptedAuditsAndInvalidate() {
        if (checkStatusRepository != null) {
            List<MandatoryCheckRecord> records = checkStatusRepository.findAll();
            for (MandatoryCheckRecord r : records) {
                if (r.getAuditId() != null) {
                    if (r.getCheckId().startsWith("model.") && modelAuditRepository != null) {
                        modelAuditRepository.findById(r.getAuditId()).ifPresent(audit -> {
                            if (audit.getStatus() != AuditStatus.COMPLETED) {
                                checkStatusRepository.invalidate(r.getCheckId());
                            }
                        });
                    } else if (r.getCheckId().startsWith("dataset.") && datasetAuditRepository != null) {
                        datasetAuditRepository.findById(r.getAuditId()).ifPresent(audit -> {
                            if (audit.getStatus() != AuditStatus.COMPLETED) {
                                checkStatusRepository.invalidate(r.getCheckId());
                            }
                        });
                    }
                }
            }
        }
    }

    /**
     * Returns a snapshot map of all mandatory security check execution statuses.
     */
    public Map<String, CheckExecutionStatus> getCurrentCheckStatuses() {
        Map<String, CheckExecutionStatus> map = new HashMap<>();
        for (String check : MANDATORY_CHECKS) {
            map.put(check, getCheckStatus(check));
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * Computes the aggregated system-wide security risk assessment using authoritative check statuses.
     */
    public SystemRiskAssessment assessSystemRisk() {
        List<SecurityFinding> allFindings = securityFindingRepository.findAll();
        Map<String, CheckExecutionStatus> currentStatuses = getCurrentCheckStatuses();
        return evaluateFindings(allFindings, currentStatuses);
    }

    /**
     * Evaluates findings with authoritative status check resolution.
     * Guaranteed never to bypass mandatory status verification.
     */
    public SystemRiskAssessment evaluateFindings(List<SecurityFinding> findings) {
        return evaluateFindings(findings, getCurrentCheckStatuses());
    }

    /**
     * Determines whether a finding explicitly represents an unavailable or unexecuted check,
     * regardless of its severity level.
     */
    public static boolean isUnavailableFinding(SecurityFinding f) {
        if (f == null) return false;
        String id = f.getFindingId() != null ? f.getFindingId().toUpperCase() : "";
        String desc = f.getDescription() != null ? f.getDescription().toUpperCase() : "";
        String cat = f.getCategory() != null ? f.getCategory().toUpperCase() : "";
        return id.contains("UNAVAILABLE")
                || desc.contains("UNAVAILABLE")
                || desc.contains("NOT EXECUTED")
                || desc.contains("FAILED TO EXECUTE BASELINE INFERENCE")
                || cat.contains("UNAVAILABLE");
    }

    /**
     * Evaluates a list of findings and structured check execution statuses
     * to produce a deterministic, explainable risk assessment with strict precedence.
     */
    public SystemRiskAssessment evaluateFindings(List<SecurityFinding> findings,
                                                 Map<String, CheckExecutionStatus> checkStatuses) {
        SystemRiskAssessment assessment = new SystemRiskAssessment();
        assessment.totalFindings = (findings != null) ? findings.size() : 0;

        double weightedRiskPoints = 0.0;

        if (findings != null) {
            for (SecurityFinding f : findings) {
                assessment.findingsByModule.merge(f.getModule(), 1, Integer::sum);

                // Check if finding indicates an unavailable or unexecuted check, regardless of severity
                if (isUnavailableFinding(f)) {
                    assessment.unavailableChecksCount++;
                }

                switch (f.getSeverity()) {
                    case CRITICAL -> {
                        assessment.criticalCount++;
                        weightedRiskPoints += 30.0 * f.getConfidence();
                    }
                    case HIGH -> {
                        assessment.highCount++;
                        weightedRiskPoints += 15.0 * f.getConfidence();
                    }
                    case MEDIUM -> {
                        assessment.mediumCount++;
                        weightedRiskPoints += 6.0 * f.getConfidence();
                    }
                    case LOW -> {
                        assessment.lowCount++;
                        weightedRiskPoints += 2.0 * f.getConfidence();
                    }
                    case INFO -> {
                        assessment.infoCount++;
                    }
                }
            }
        }

        // Process structured check execution statuses.
        // Guarantee all mandatory checks are represented; missing statuses default to NOT_EXECUTED.
        Map<String, CheckExecutionStatus> effectiveStatuses = new HashMap<>();
        if (checkStatuses != null) {
            effectiveStatuses.putAll(checkStatuses);
        }

        for (String mandatoryCheck : MANDATORY_CHECKS) {
            if (!effectiveStatuses.containsKey(mandatoryCheck) || effectiveStatuses.get(mandatoryCheck) == null) {
                effectiveStatuses.put(mandatoryCheck, CheckExecutionStatus.NOT_EXECUTED);
            }
        }

        for (Map.Entry<String, CheckExecutionStatus> entry : effectiveStatuses.entrySet()) {
            String checkName = entry.getKey();
            CheckExecutionStatus status = entry.getValue();

            if (status == null) {
                assessment.checkStatuses.put(checkName, CheckExecutionStatus.NOT_EXECUTED);
                assessment.notExecutedChecksCount++;
                assessment.decisionJustifications.add(String.format(
                        "Mandatory check '%s' status is missing/null; cannot be treated as PASSED (marked NOT_EXECUTED).", checkName));
            } else {
                assessment.checkStatuses.put(checkName, status);
                switch (status) {
                    case UNAVAILABLE -> {
                        assessment.unavailableChecksCount++;
                        assessment.decisionJustifications.add(String.format(
                                "Mandatory check '%s' is UNAVAILABLE.", checkName));
                    }
                    case NOT_EXECUTED -> {
                        assessment.notExecutedChecksCount++;
                        assessment.decisionJustifications.add(String.format(
                                "Mandatory check '%s' was NOT_EXECUTED.", checkName));
                    }
                    case FAILED -> {
                        assessment.failedChecksCount++;
                        assessment.decisionJustifications.add(String.format(
                                "Mandatory check '%s' FAILED.", checkName));
                    }
                    case PASSED -> {
                        assessment.passedChecksCount++;
                    }
                }
            }
        }

        assessment.compositeRiskScore = Math.min(100.0, weightedRiskPoints);
        double quarantineThreshold = settingRepository != null ? settingRepository.getDouble("quarantine_score", 80.0) : 80.0;
        double reviewThreshold = settingRepository != null ? settingRepository.getDouble("review_score", 40.0) : 40.0;

        // Strict Precedence Rules:
        // 1. Any critical integrity finding → QUARANTINE
        // 2. Composite risk score exceeding quarantine threshold → QUARANTINE
        // 3. Any high-risk finding → REVIEW
        // 4. Composite risk score exceeding review threshold → REVIEW
        // 5. Any check FAILED → REVIEW
        // 6. Any required check UNAVAILABLE → REVIEW
        // 7. Any required check NOT_EXECUTED → REVIEW
        // 8. Otherwise, ACCEPT only when all mandatory checks PASSED and no adverse findings
        if (assessment.criticalCount > 0) {
            assessment.overallDecision = AssessmentDecision.QUARANTINE;
            assessment.decisionJustifications.add(String.format(
                    "QUARANTINE mandated: %d CRITICAL integrity violation(s) detected.",
                    assessment.criticalCount));
        } else if (assessment.compositeRiskScore >= quarantineThreshold) {
            assessment.overallDecision = AssessmentDecision.QUARANTINE;
            assessment.decisionJustifications.add(String.format(
                    "QUARANTINE mandated: Composite risk score (%.1f) exceeds threshold (%.1f).",
                    assessment.compositeRiskScore, quarantineThreshold));
        } else if (assessment.highCount > 0) {
            assessment.overallDecision = AssessmentDecision.REVIEW;
            assessment.decisionJustifications.add(String.format(
                    "REVIEW required: %d HIGH severity finding(s) detected.",
                    assessment.highCount));
        } else if (assessment.compositeRiskScore >= reviewThreshold) {
            assessment.overallDecision = AssessmentDecision.REVIEW;
            assessment.decisionJustifications.add(String.format(
                    "REVIEW required: Composite risk score (%.1f) exceeds threshold (%.1f).",
                    assessment.compositeRiskScore, reviewThreshold));
        } else if (assessment.failedChecksCount > 0) {
            assessment.overallDecision = AssessmentDecision.REVIEW;
            assessment.decisionJustifications.add(String.format(
                    "REVIEW required: %d security check(s) FAILED.",
                    assessment.failedChecksCount));
        } else if (assessment.unavailableChecksCount > 0) {
            assessment.overallDecision = AssessmentDecision.REVIEW;
            assessment.decisionJustifications.add(String.format(
                    "REVIEW required: %d required security check(s) were UNAVAILABLE.",
                    assessment.unavailableChecksCount));
        } else if (assessment.notExecutedChecksCount > 0) {
            assessment.overallDecision = AssessmentDecision.REVIEW;
            assessment.decisionJustifications.add(String.format(
                    "REVIEW required: %d required security check(s) were NOT_EXECUTED or missing.",
                    assessment.notExecutedChecksCount));
        } else if (assessment.passedChecksCount >= MANDATORY_CHECKS.size()) {
            assessment.overallDecision = AssessmentDecision.ACCEPT;
            assessment.decisionJustifications.add(
                    "ACCEPT: No critical or high-risk integrity violations detected, and all required security checks passed.");
        } else {
            assessment.overallDecision = AssessmentDecision.REVIEW;
            assessment.decisionJustifications.add(
                    "REVIEW required: Incomplete mandatory check coverage; cannot certify as ACCEPT.");
        }

        // Sort findings by severity weight
        assessment.prioritizedFindings = new ArrayList<>(findings != null ? findings : List.of());
        assessment.prioritizedFindings.sort((a, b) -> Integer.compare(b.getSeverity().getWeight(), a.getSeverity().getWeight()));

        return assessment;
    }
}
