# SentinelCV — System-Wide Correctness & Security Audit

## 1. Executive Summary & Audit Baseline

SentinelCV is an offline computer vision security and integrity assessment system built on Java 21, Spring Boot 3.2.5, SQLite, ONNX Runtime 1.17.1, DJL 0.27.0, Thymeleaf, and Maven.

Prior audit cycles addressed isolated vulnerabilities; however, comprehensive static analysis of the full codebase revealed integration gaps, missing status propagation, false `ACCEPT` pathways, destination file overwrite risks during archive extraction, aggregate counter subtractions in provenance verification, unvalidated configured keys, and inconsistent lifecycle states.

Baseline test execution status: **96 tests executed, 0 failures, 0 errors**.

---

## 2. Audit Findings Log

### FINDING-01: RiskAssessmentService Bypasses Check Execution Statuses in Global Assessment
- **Finding ID**: FINDING-01
- **Affected File and Method**: `com.sentinelcv.service.RiskAssessmentService.java` — `assessSystemRisk()`, `evaluateFindings(List<SecurityFinding>)`, `evaluateFindings(List<SecurityFinding>, Map<String, CheckExecutionStatus>)`
- **Root Cause**: `assessSystemRisk()` delegated to `evaluateFindings(allFindings)`, which called `evaluateFindings(findings, null)`. In `evaluateFindings`, if `checkStatuses` was `null` or missing mandatory checks, and the findings list contained no critical or high severity items, the method defaulted to `AssessmentDecision.ACCEPT`. On an empty database or prior to running audits, the system falsely certified an `ACCEPT` decision.
- **Severity**: CRITICAL
- **Category**: Correctness & Security Decision Integrity (Directly Reachable)
- **Related Production Callers**: `DashboardController.dashboard()`, `ReportService.generateReport()`
- **Required Repair**:
  1. Define explicit mandatory security checks: `model.baseline_inference`, `model.trigger_perturbation`, `dataset.integrity`, and `provenance.verification`.
  2. Maintain and query persistent check execution statuses (`CheckExecutionStatus.PASSED`, `FAILED`, `UNAVAILABLE`, `NOT_EXECUTED`).
  3. Treat missing or null statuses as `NOT_EXECUTED`, mandating a `REVIEW` decision.
  4. Ensure `ACCEPT` is returned only when every mandatory check has explicitly `PASSED` and no adverse findings exist.
- **Regression Test Required**:
  - `RiskAssessmentServiceTest.testNoFindingsWithMissingStatusesProducesReview`
  - `RiskAssessmentServiceTest.testEmptyDatabaseProducesReview`
  - `RiskAssessmentServiceIntegrationTest.testDashboardAndReportConsistency`

---

### FINDING-02: Archive Extraction Overwrites Pre-Existing Destination Files and Destroys Rollback
- **Finding ID**: FINDING-02
- **Affected File and Method**: `com.sentinelcv.util.ArchiveUtil.java` — `extractZipSafely(File, Path)`
- **Root Cause**: Archive extraction opened a `FileOutputStream` directly on `targetDirectory.resolve(entryName)`. If a file already existed at that path, it was immediately truncated upon opening. When extraction subsequently failed, `rollbackCreated` only removed files in `createdFiles` (which excluded pre-existing files in `existingBefore`), leaving pre-existing files truncated and permanently destroyed.
- **Severity**: HIGH
- **Category**: Correctness & Data Integrity (Directly Exploitable via Malicious ZIP)
- **Related Production Callers**: `DatasetController.uploadDataset()`
- **Required Repair**:
  1. Never write directly into the destination directory during archive extraction.
  2. Extract and validate all archive entries in an isolated temporary staging directory on the same filesystem.
  3. Check destination for pre-existing file collisions before committing.
  4. Abort extraction safely if any destination file collision exists.
  5. Atomically move/commit extracted files to destination only after full archive validation succeeds.
  6. Clean up temporary staging directories unconditionally.
- **Regression Test Required**:
  - `ArchiveSecurityTest.testExistingDestinationFileCollisionRejection`
  - `ArchiveSecurityTest.testExistingFilePreservationOnFailedExtraction`
  - `ArchiveSecurityTest.testExistingFilePreservationOnSuccessfulExtraction`

---

### FINDING-03: Archive Compression Ratio Check Skipped for Small Entries
- **Finding ID**: FINDING-03
- **Affected File and Method**: `com.sentinelcv.util.ArchiveUtil.java` — `extractZipSafely(File, Path)`
- **Root Cause**: The per-entry compression ratio check evaluated:
  `if (compSize > 0 && entryBytes > MAX_COMPRESSION_RATIO * compSize && entryBytes > 64 * 1024)`.
  Entries smaller than 64 KB completely bypassed compression ratio checks, allowing high-ratio payloads (e.g. 50 KB compressing to 40 bytes) to escape ratio limits.
- **Severity**: MEDIUM
- **Category**: Security Vulnerability (Resource Exhaustion)
- **Related Production Callers**: `DatasetController.uploadDataset()`
- **Required Repair**: Remove the `&& entryBytes > 64 * 1024` bypass and enforce the 100:1 ratio consistently on all entries.
- **Regression Test Required**:
  - `ArchiveSecurityTest.testSmallEntryCompressionRatioViolationBlocked`

---

### FINDING-04: Duplicate Entry Names in Archives Not Rejected
- **Finding ID**: FINDING-04
- **Affected File and Method**: `com.sentinelcv.util.ArchiveUtil.java` — `extractZipSafely(File, Path)`
- **Root Cause**: `ArchiveUtil` did not track previously extracted entry names within the same archive. A crafted ZIP containing duplicate entry names could silently overwrite earlier extracted entries within the same extraction transaction.
- **Severity**: MEDIUM
- **Category**: Security Vulnerability (ZIP Parser Manipulation)
- **Related Production Callers**: `DatasetController.uploadDataset()`
- **Required Repair**: Maintain a set of normalized entry paths encountered during archive traversal; immediately throw `SentinelCvException` on detecting duplicate entry names.
- **Regression Test Required**:
  - `ArchiveSecurityTest.testDuplicateArchiveEntryNamesRejected`

---

### FINDING-05: Empty Batch Evaluated as VERIFIED in Inference Provenance Service
- **Finding ID**: FINDING-05
- **Affected File and Method**: `com.sentinelcv.service.InferenceProvenanceService.java` — `processAndVerifyRecords(List<InferenceRecord>)`
- **Root Cause**: The decision selection block evaluated:
  ```java
  } else if (batchResult.totalRecordsChecked > 0 && batchResult.verifiedCount == batchResult.totalRecordsChecked) {
      assessmentDecision = "VERIFIED";
  } else {
      assessmentDecision = "VERIFIED";
  }
  ```
  When an empty batch (`records.isEmpty()`, `totalRecordsChecked == 0`) was submitted, execution fell through to the `else` branch and returned `"VERIFIED"`.
- **Severity**: HIGH
- **Category**: Correctness & Security Decision Integrity
- **Related Production Callers**: `InferenceController.importRecords()`, `InferenceController.verifyAll()`
- **Required Repair**: Explicitly check for empty batch (`totalRecordsChecked == 0`) and return `"UNAVAILABLE"` or `"REVIEW"`. `VERIFIED` must strictly require `totalRecordsChecked > 0` and `verifiedCount == totalRecordsChecked`.
- **Regression Test Required**:
  - `InferenceProvenanceServiceTest.testEmptyBatchProducesUnavailable`

---

### FINDING-06: Aggregate Failure Counter Subtraction Used to Infer Cryptographic Tampering
- **Finding ID**: FINDING-06
- **Affected File and Method**: `com.sentinelcv.service.InferenceProvenanceService.java` — `processAndVerifyRecords(List<InferenceRecord>)`
- **Root Cause**: Cryptographic integrity compromise was inferred using the formula:
  `int tamperCount = batchResult.cryptographicFailures > 0 ? ... : Math.max(0, batchResult.failedCount - batchResult.replayAttempts);`
  and:
  `boolean hasIntegrityCompromise = ... || (batchResult.failedCount > 0 && batchResult.failedCount > batchResult.replayAttempts);`
  Subtracting aggregate counters creates ambiguity when multiple anomaly types overlap on the same records.
- **Severity**: MEDIUM
- **Category**: Correctness & Architectural Precision
- **Related Production Callers**: `InferenceController.importRecords()`
- **Required Repair**: Track explicit per-record failure categories (`FailureCategory` enum: `BROKEN_CHAIN`, `HASH_MISMATCH`, `HMAC_MISMATCH`, `REPLAY_DETECTED`, `INVALID_SEQUENCE`, `INVALID_TIMESTAMP`, `MISSING_KEY`, `UNSUPPORTED_SCHEMA`, `DUPLICATE_RECORD_ID`). Determine `QUARANTINE` based on whether confirmed cryptographic failures or broken chains occurred.
- **Regression Test Required**:
  - `InferenceProvenanceServiceTest.testBrokenChainPlusReplayProducesQuarantine`
  - `InferenceProvenanceServiceTest.testTamperedPayloadPlusReplayProducesQuarantine`
  - `InferenceProvenanceServiceTest.testReplayAloneProducesReview`

---

### FINDING-07: Duplicate Record ID and Duplicate Sequence Numbers Not Flagged in Inference Validation
- **Finding ID**: FINDING-07
- **Affected File and Method**: `com.sentinelcv.inference.InferenceValidator.java` — `verifyRecords(List<InferenceRecord>, String)`
- **Root Cause**: `InferenceValidator` validated `observedNonces`, but did not track `observedRecordIds` or detect duplicate sequence numbers within an input batch.
- **Severity**: MEDIUM
- **Category**: Correctness & Provenance Integrity
- **Related Production Callers**: `InferenceProvenanceService.processAndVerifyRecords()`
- **Required Repair**: Track `observedRecordIds` and reject duplicate record IDs. Verify monotonically increasing sequence numbers and reject duplicate sequence numbers.
- **Regression Test Required**:
  - `InferenceValidatorTest.testDuplicateRecordIdRejected`
  - `InferenceValidatorTest.testDuplicateSequenceNumberRejected`

---

### FINDING-08: Provenance Key Manager Allows Arbitrary Weak Keys from Properties and Environment
- **Finding ID**: FINDING-08
- **Affected File and Method**: `com.sentinelcv.provenance.ProvenanceKeyManager.java` — `getVerificationKey()`, `readAndValidateLocalKey(Path)`
- **Root Cause**: Configured property keys (`sentinelcv.provenance.hmac-key`) and environment keys (`SENTINELCV_HMAC_KEY`) were accepted without validating length, character set, or entropy. Hexadecimal keys were not validated to be exactly 64 valid hex characters. Key directory permissions were not restricted to 0700.
- **Severity**: MEDIUM
- **Category**: Security Vulnerability (Cryptographic Misconfiguration)
- **Related Production Callers**: `InferenceProvenanceService`, `DataInitializer`
- **Required Repair**:
  1. Enforce documented key format: 256-bit hexadecimal keys must be exactly 64 valid hexadecimal characters (`^[0-9a-fA-F]{64}$`).
  2. Reject weak, empty, or truncated keys from properties, environment, and local files.
  3. Restrict key directory permissions to owner-only (`0700`) where POSIX is supported.
  4. Ensure permission operations are validated.
- **Regression Test Required**:
  - `ProvenanceKeyManagerTest.testMalformedHexKeyRejected`
  - `ProvenanceKeyManagerTest.testShortEnvironmentKeyRejected`
  - `ProvenanceKeyManagerTest.testKeyDirectoryPermissions`

---

### FINDING-09: Empty Dataset Ingestion Produces False ACCEPT in Dataset Audit Service
- **Finding ID**: FINDING-09
- **Affected File and Method**: `com.sentinelcv.service.DatasetAuditService.java` — `executeAudit(Long)`
- **Root Cause**: When a dataset contained 0 images, `parseResult.images` was empty, zero findings were generated, `stats.integrityScore` defaulted to 100.0, and the audit concluded with `AssessmentDecision.ACCEPT`.
- **Severity**: HIGH
- **Category**: Correctness & Security Decision Integrity
- **Related Production Callers**: `DatasetController.startAudit()`
- **Required Repair**: Explicitly check for empty datasets (0 images). Generate finding `FIND-DATASET-EMPTY` (severity HIGH/CRITICAL) and mandate `REVIEW` or `QUARANTINE`. Never award `ACCEPT` to an empty dataset.
- **Regression Test Required**:
  - `DatasetAuditIntegrationTest.testEmptyDatasetProducesReview`

---

### FINDING-10: Stale RUNNING and PENDING Audits Persist Indefinitely Across Server Restarts
- **Finding ID**: FINDING-10
- **Affected File and Method**: `com.sentinelcv.config.DataInitializer.java` — `run(String...)`
- **Root Cause**: If the application is restarted or killed while asynchronous audits are in `RUNNING` or `PENDING` status, SQLite preserves this state indefinitely. Polling endpoints and dashboard views report jobs as permanently executing.
- **Severity**: MEDIUM
- **Category**: Operational Reliability & State Consistency
- **Related Production Callers**: `DataInitializer`, `DatasetController`, `ModelController`
- **Required Repair**: On application startup in `DataInitializer`, query for all audits in `RUNNING` or `PENDING` status and transition them to `FAILED` with an error message indicating system interruption.
- **Regression Test Required**:
  - `AsyncAuditExecutionTest.testInterruptedAuditCleanupOnStartup`

---

### FINDING-11: Unhandled ThreadPoolTaskExecutor Task Rejection Leaves Audits Stuck in PENDING
- **Finding ID**: FINDING-11
- **Affected File and Method**: `com.sentinelcv.config.ApplicationConfig.java`, `com.sentinelcv.controller.DatasetController.java`, `com.sentinelcv.controller.ModelController.java`
- **Root Cause**: `auditTaskExecutor` had a bounded queue (25) with default `AbortPolicy`. When the queue filled, `TaskRejectedException` was thrown, causing HTTP 500 errors and leaving the newly saved audit in `PENDING` state permanently.
- **Severity**: MEDIUM
- **Category**: Concurrency & Error Handling
- **Related Production Callers**: `DatasetController.startAudit()`, `ModelController.startAudit()`
- **Required Repair**: Configure a `CallerRunsPolicy` or handle `TaskRejectedException` cleanly by transitioning the audit status to `FAILED` with an explanatory error message.
- **Regression Test Required**:
  - `AsyncAuditExecutionTest.testExecutorSaturationHandling`

---

### FINDING-12: Disconnected Check Execution Statuses Across Auditing Services
- **Finding ID**: FINDING-12
- **Affected File and Method**: `com.sentinelcv.service.ModelAuditService.java`, `com.sentinelcv.service.DatasetAuditService.java`, `com.sentinelcv.service.InferenceProvenanceService.java`
- **Root Cause**: Auditing services calculated internal decisions but did not propagate structured check execution statuses (`CheckExecutionStatus`) to `RiskAssessmentService`. As a result, the centralized risk engine operated without knowledge of completed analyzer statuses.
- **Severity**: HIGH
- **Category**: Integration Gap
- **Related Production Callers**: `DashboardController`, `ReportService`
- **Required Repair**: Inject `RiskAssessmentService` into `ModelAuditService`, `DatasetAuditService`, and `InferenceProvenanceService`, and record explicit check execution statuses upon completion of each check.
- **Regression Test Required**:
  - `RiskAssessmentServiceIntegrationTest.testFullPipelineCheckStatusPropagation`

---

### FINDING-13: Clearing Inference Records Leaves Stale Provenance Check Status
- **Finding ID**: FINDING-13
- **Affected File and Method**: `com.sentinelcv.controller.InferenceController.java` — `clearRecords(RedirectAttributes)`
- **Root Cause**: When inference records were deleted via `/inference/clear`, the `provenance.verification` check status in `RiskAssessmentService` was not reset to `NOT_EXECUTED`.
- **Severity**: MEDIUM
- **Category**: State Consistency
- **Related Production Callers**: `InferenceController.clearRecords()`
- **Required Repair**: Reset `provenance.verification` status to `NOT_EXECUTED` when inference records are cleared.
- **Regression Test Required**:
  - `InferenceProvenanceIntegrationTest.testClearRecordsResetsCheckStatus`

---

### FINDING-14: Drift Analysis Constant-Distribution Shift Boundary
- **Finding ID**: FINDING-14
- **Affected File and Method**: `com.sentinelcv.util.StatisticalUtil.java` — `calculatePSI(double[], double[], int)`
- **Root Cause**: In `calculatePSI`, bin width calculation relied on `Math.abs(max - min) < EPSILON`. If one distribution had slight perturbation within epsilon range while the other was constant, bin division could suffer floating-point degradation.
- **Severity**: LOW
- **Category**: Numerical Stability & Correctness
- **Related Production Callers**: `DistributionComparator.compareFeature()`
- **Required Repair**: Ensure bin width is bounded from below and check finite distribution variance.
- **Regression Test Required**:
  - `DriftStatisticalEdgeCasesTest.testNearConstantDistributionShift`

---

### FINDING-15: Model Loader Tensor Element Count Overflow on Malformed Shape
- **Finding ID**: FINDING-15
- **Affected File and Method**: `com.sentinelcv.model.BehavioralAnalyzer.java` — `computeTotalElements(long[])`
- **Root Cause**: Integer overflow check inside `computeTotalElements` was checked after potential long overflow if dynamic negative dimensions were replaced by arbitrary inputs.
- **Severity**: LOW
- **Category**: Correctness & Resource Bounds
- **Related Production Callers**: `BehavioralAnalyzer.analyzeBehavior()`, `TriggerAnalyzer.analyzePerturbations()`
- **Required Repair**: Validate individual dimensions before multiplying; enforce maximum total elements strictly.
- **Regression Test Required**:
  - `RealOnnxModelAuditTest.testTensorElementCountOverflowProtection`

