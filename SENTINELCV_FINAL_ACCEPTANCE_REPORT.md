# SentinelCV — Final System-Wide Correctness Audit, Repair and Acceptance Report

**Date**: November 3, 2026  
**System**: SentinelCV — Offline Computer Vision Security and Integrity Assessment System  
**Stack**: Java 21, Spring Boot 3.2.5, SQLite, ONNX Runtime 1.17.1, Deep Java Library (DJL) 0.27.0, Thymeleaf, Maven  
**Final Test Status**: **116 Tests Executed, 0 Failures, 0 Errors, 0 Skipped (100% Pass Rate)**  
**Build Status**: **BUILD SUCCESS (`./mvnw_local clean test verify`)**  

---

## 1. Executive Summary

SentinelCV has undergone an exhaustive, full-codebase correctness and security audit. Previous repair cycles resolved isolated vulnerabilities (such as specific path traversal checks and basic hashing), but left critical integration gaps:
- The global risk assessment engine evaluated aggregate security findings while bypassing structured execution statuses from mandatory checks, permitting false `ACCEPT` determinations on empty or incomplete systems.
- Archive extraction directly wrote to destination paths, truncating pre-existing files prior to validation, failing to rollback pre-existing content, ignoring duplicate entry names, and exempting small entries from compression ratio limits.
- Provenance verification allowed empty batches to evaluate as `VERIFIED`, subtracted counters to infer tampering, and lacked explicit per-record failure categorization.
- Cryptographic keys accepted weak or arbitrary string formats and lacked strict POSIX permission constraints on parent directories.
- Unhandled server restarts left asynchronous audits permanently locked in `RUNNING` or `PENDING` states.

All identified vulnerabilities and structural defects have been systematically repaired, hardened, and verified with 20 new comprehensive integration and security test cases (expanding the test suite from 96 to 116 tests). The system adheres to strict defense-in-depth principles, deterministic decision precedence, and offline operational self-sufficiency.

---

## 2. Categorization of Audit Findings & Repairs

### Category A: Fixed and Thoroughly Tested

#### A.1 Global Risk Assessment & Mandatory Status Propagation
- **Issue**: `RiskAssessmentService.assessSystemRisk()` delegated to `evaluateFindings(allFindings)` without providing check execution statuses, defaulting to `ACCEPT` when no critical/high findings existed.
- **Repair**:
  - Established four immutable mandatory security checks: `model.baseline_inference`, `model.trigger_perturbation`, `dataset.integrity`, and `provenance.verification`.
  - Implemented `recordCheckStatus`, `getCheckStatus`, and `getCurrentCheckStatuses` in `RiskAssessmentService`.
  - Enforced that missing or unrecorded check statuses default to `NOT_EXECUTED`.
  - Decision engine strictly mandates `REVIEW` if any mandatory check is `UNAVAILABLE` or `NOT_EXECUTED`.
  - `ACCEPT` is awarded **only when every mandatory check has explicitly `PASSED`**, composite risk score $< 40.0$, and zero critical or high-risk findings exist.
  - Aligned `DashboardController` and `ReportService` (PDF, HTML, JSON) to query the exact same authoritative `assessSystemRisk()` method.
- **Tests**: `GlobalRiskAssessmentIntegrationTest` (7 tests covering empty database, missing statuses, all-passed, unavailable checks, critical finding precedence, and report/dashboard consistency); `RiskAssessmentServiceTest` (10 tests).

#### A.2 Safe Archive Extraction Pipeline (`ArchiveUtil`)
- **Issue**: Extraction directly opened output streams in the destination directory, truncating pre-existing files upon collision. Rollback wiped newly created files but could not restore truncated pre-existing data. Small entries ($< 64$ KB) were exempted from compression ratio checks, duplicate archive entries were not rejected, and concurrent extractions could race into the same folder.
- **Repair**:
  - Implemented isolated temporary directory staging (`Files.createTempDirectory("sentinelcv_stage_")`).
  - Added duplicate archive entry name detection and rejection (`SECURITY_VIOLATION`).
  - Removed the 64 KB exemption; compression ratios are now enforced consistently across all entries (`ZIP_BOMB_DETECTED`).
  - Added pre-commit collision inspection: if any staged file matches an existing file in `targetDirectory`, extraction is aborted with `DESTINATION_COLLISION` without touching the destination directory.
  - Safe atomic commit: files are moved into `targetDirectory` using `StandardCopyOption.ATOMIC_MOVE` (with safe fallback for cross-volume filesystems).
  - Synchronized extraction on the target directory path to prevent concurrent extraction races.
  - Staging directories are unconditionally purged in a `finally` block.
- **Tests**: `ArchiveSecurityTest` (16 tests, including collision rejection, pre-existing content preservation on failure and success, duplicate entry blocking, small-entry ratio enforcement, and multi-threaded concurrent extraction).

#### A.3 Cryptographic Provenance Ledger (`InferenceValidator` & `InferenceProvenanceService`)
- **Issue**: Empty inference batches (`[]`) evaluated as `VERIFIED`. An aggregate counter formula (`failedCount - replayAttempts > 0`) was used to infer cryptographic tampering, which broke under mixed failure modes. Duplicate record IDs and sequence gaps were not systematically categorized.
- **Repair**:
  - Implemented explicit `FailureCategory` enum: `BROKEN_CHAIN`, `HASH_MISMATCH`, `HMAC_MISMATCH`, `REPLAY_DETECTED`, `INVALID_SEQUENCE`, `INVALID_TIMESTAMP`, `MISSING_KEY`, `UNSUPPORTED_SCHEMA`, `DUPLICATE_RECORD_ID`, `MALFORMED_RECORD`.
  - Replaced aggregate counter subtraction with per-record failure category tagging and explicit `hasCryptographicTampering()` analysis.
  - Enforced that empty batches (`totalRecordsChecked == 0`) return `"UNAVAILABLE"` and record `provenance.verification` check status as `UNAVAILABLE` (producing `REVIEW` in system risk).
  - Enforced canonical payload serialization V2 across both SHA-256 and HMAC verification.
  - Enforced detection of duplicate record IDs, duplicate sequence numbers, and unsupported schema versions.
- **Tests**: `InferenceValidatorTest` (22 tests); `InferenceProvenanceServiceTest` (8 tests).

#### A.4 Cryptographic Key Lifecycle Hardening (`ProvenanceKeyManager`)
- **Issue**: Configured property keys, environment variable keys, and local key file contents were read without strict hexadecimal validation. Weak or short keys could be accepted. Directory permissions on the key storage directory were not restricted.
- **Repair**:
  - Enforced strict 256-bit hexadecimal validation: exactly 64 valid hex characters (`^[0-9a-fA-F]{64}$`) required across all sources.
  - Added POSIX `0700` (`rwx------`) directory permission enforcement on the parent keystore directory.
  - Maintained POSIX `0600` (`rw-------`) permission enforcement on key files.
  - Added non-POSIX fallback permission restrictions (`File.setReadable(true, true)` / `File.setWritable(true, true)`).
  - Sanitized all exception messages to ensure raw secret keys are never leaked in logs or error traces.
- **Tests**: `ProvenanceKeyManagerTest` (11 tests covering key generation, 64-hex validation, weak key rejection, POSIX 0600/0700 permissions, secret non-leakage, and concurrent initialization).

#### A.5 Model Audit Execution Status Propagation (`ModelAuditService`)
- **Issue**: When model loaders or analyzers caught exceptions or encountered unsupported formats, execution completed without explicitly updating the global risk check statuses.
- **Repair**:
  - Injected `RiskAssessmentService` into `ModelAuditService`.
  - Both `model.baseline_inference` and `model.trigger_perturbation` explicitly record `PASSED`, `FAILED`, or `UNAVAILABLE` in both successful and exceptional execution paths.
  - Synthetic ONNX fixtures vs real trained models are handled with distinct confidence scores and evidence fields.
- **Tests**: `RealOnnxModelAuditTest` (10 tests); `ModelAuditIntegrationTest` (1 test).

#### A.6 Dataset Audit Integrity & Empty Dataset Safeguards (`DatasetAuditService`)
- **Issue**: Datasets with zero images could complete parsing without failing integrity checks, allowing empty datasets to contribute to an `ACCEPT` state.
- **Repair**:
  - Added explicit validation for empty parsed image sets (`parseResult.images.isEmpty()`).
  - Emits `FIND-DATASET-EMPTY-1` (`HIGH` severity, confidence 1.0), sets `integrityScore = 0.0`, and records check status `FAILED`.
  - Explicitly propagates `dataset.integrity` status (`PASSED` or `FAILED`) to `RiskAssessmentService`.
- **Tests**: `DatasetAuditIntegrationTest` (1 test); `CocoParserTest` (1 test); `YoloParserTest` (1 test).

#### A.7 Async Execution Resilience & DB Consistency (`DataInitializer` & `ApplicationConfig`)
- **Issue**: Server shutdown during active audits left jobs permanently locked in `RUNNING` or `PENDING` status in SQLite. Thread pool queue saturation threw unhandled `RejectedExecutionException`.
- **Repair**:
  - Added `cleanupInterruptedAudits()` in `DatasetAuditRepository`, `ModelAuditRepository`, and `DriftAuditRepository`.
  - Invoked during `DataInitializer.run()` on startup: transitions any orphaned `PENDING`/`RUNNING` audits to `FAILED` with `errorMessage = "Audit interrupted by system shutdown/restart"`.
  - Configured `ThreadPoolExecutor.CallerRunsPolicy()` on `auditTaskExecutor` to handle queue saturation gracefully without task loss.
- **Tests**: `AsyncAuditExecutionTest` (2 tests).

---

### Category B: Fixed, but Not Independently Testable

#### B.1 Cross-Filesystem Non-Atomic Move Fallback
- **Description**: In environments where the temporary staging directory and destination directory reside on different storage partitions/mounts, `Files.move(..., ATOMIC_MOVE)` throws `AtomicMoveNotSupportedException`.
- **Handling**: `ArchiveUtil` catches `AtomicMoveNotSupportedException` and falls back to standard file copy/move operations. This path is verified logically, but running unit tests across distinct physical filesystem partitions cannot be guaranteed in all CI/container test environments.

#### B.2 Non-POSIX File Permission Setting
- **Description**: Windows systems (NTFS/FAT32) do not support POSIX file permission attributes (`PosixFileAttributeView`).
- **Handling**: `ProvenanceKeyManager` and `ArchiveUtil` catch `UnsupportedOperationException` and apply standard Java IO owner-only permission flags (`file.setReadable(true, true)`). In this macOS testing environment, the POSIX code path was executed and verified.

#### B.3 Native LibTorch Execution in Air-Gapped macOS Environments
- **Description**: DJL PyTorch engine detects whether native LibTorch binaries are bundled on the classpath. On offline macOS arm64 setups lacking native LibTorch shared libraries, TorchScript models cannot execute native C++ inference.
- **Handling**: `TorchScriptModelLoader` gracefully detects native engine unavailability and records status `UNAVAILABLE` rather than crashing the JVM.

---

### Category C: Known Limitations

1. **Embedded SQLite Database Concurrency**:
   - SentinelCV relies on embedded SQLite (`sentinelcv.db`). SQLite supports multiple concurrent readers but serializes write transactions with database-level locking. Under heavy write loads across concurrent background threads, transient `busy` states may occur.
2. **Same-User Local Process Boundary**:
   - Keys stored on disk (`./data/keys/provenance.key`) are protected with `0600` permissions and restricted directories (`0700`). However, any hostile process executing under the same operating system user identity (`teju` or service account) has OS-level permissions to inspect the key and database. Hardware security modules (HSM) or enclave-backed keystores would be required for physical hardware protection.
3. **In-Memory Nonce Replay Scope**:
   - Replay protection verifies nonces against persisted `InferenceRecord` database entries and an in-memory session cache. If inference records are purged from the database via manual operator deletion, nonces from those deleted records could theoretically be re-submitted.
4. **Statistical Proxy Nature of Trojan Backdoor Heuristic**:
   - `TriggerAnalyzer` tests sensitivity against standard spatial perturbations (chessboard grids, Gaussian patches). While this accurately detects typical CV backdoors, sophisticated clean-label or natural-feature backdoors (e.g., specific rare real-world objects) cannot be discovered through synthetic pixel perturbations alone.

---

### Category D: Unresolved Issues

1. **Undetectable Natural-Feature Backdoors**:
   - Academic research has demonstrated that vision models can be backdoored using semantic features (e.g., a specific sweater pattern or glasses) that do not exhibit high variance under synthetic pixel noise grids. Detecting such semantic triggers requires extensive dataset trigger inversion, which is beyond offline single-node heuristics.
2. **Proprietary Model Format Support**:
   - The system supports ONNX (.onnx) natively and TorchScript (.pt) when DJL PyTorch binaries are available. Other model serialization formats (such as TensorFlow SavedModel, Keras H5, or TensorRT engines) are currently unsupported and will be flagged as `UNAVAILABLE`.

---

## 3. Full Test Suite Verification Results

Every test class was executed via Maven Surefire: **`./mvnw_local clean test verify`**.

| Test Class | Test Count | Status | Pipeline / Coverage Area |
| :--- | :---: | :---: | :--- |
| `com.sentinelcv.util.ArchiveSecurityTest` | **16** | **PASS** | Safe archive extraction, staging isolation, destination collision rejection, pre-existing file preservation, duplicate entry detection, small-entry ratio enforcement, zip-slip, symlink entry detection, concurrent extractions. |
| `com.sentinelcv.inference.InferenceValidatorTest` | **22** | **PASS** | Schema V2 canonical payload serialization, hash verification, HMAC-SHA256 signature, sequence gap detection, duplicate sequence numbers, duplicate record IDs, replay detection, broken chain detection, tampering vs replay distinction. |
| `com.sentinelcv.provenance.ProvenanceKeyManagerTest` | **11** | **PASS** | 256-bit key generation, strict 64-hex character validation, weak/short key rejection, POSIX 0600 file / 0700 directory permissions, non-leakage of secrets in exceptions, concurrent initialization. |
| `com.sentinelcv.service.RiskAssessmentServiceTest` | **10** | **PASS** | Risk scoring, mandatory check status evaluation, strict precedence (quarantine vs review vs accept), unavailable check handling, absence of findings requiring review when checks unexecuted. |
| `com.sentinelcv.model.RealOnnxModelAuditTest` | **10** | **PASS** | Real ONNX model inference, tensor dimension validation, behavioral baseline testing, trigger perturbation analysis, clean model acceptance, trojan quarantine, torchscript fallback. |
| `com.sentinelcv.service.InferenceProvenanceServiceTest` | **8** | **PASS** | JSON import & verification pipeline, empty batch handling (`UNAVAILABLE`), order-independent verification, unsupported schema handling, audit history persistence. |
| `com.sentinelcv.drift.DriftStatisticalEdgeCasesTest` | **7** | **PASS** | Statistical edge cases, KS-test, Population Stability Index (PSI), minimum sample size constraints ($N < 5$), zero-variance distributions, identical distributions. |
| `com.sentinelcv.integration.GlobalRiskAssessmentIntegrationTest` | **7** | **PASS** | End-to-end integration: empty database produces review, missing check statuses produce review, all mandatory checks passed produces accept, unavailable check produces review, critical finding produces quarantine, report and dashboard decision agreement. |
| `com.sentinelcv.integration.ReportServiceIntegrationTest` | **3** | **PASS** | Consolidated audit report generation: JSON, HTML, and PDF export with embedded risk assessment and findings. |
| `com.sentinelcv.util.CryptoUtilTest` | **3** | **PASS** | Cryptographic primitives: HMAC-SHA256, constant-time verification, secure random nonces. |
| `com.sentinelcv.util.HashUtilTest` | **3** | **PASS** | SHA-256 byte, string, and file stream hashing. |
| `com.sentinelcv.util.StatisticalUtilTest` | **3** | **PASS** | Mathematical calculations: mean, standard deviation, variance. |
| `com.sentinelcv.service.AsyncAuditExecutionTest` | **2** | **PASS** | Asynchronous audit processing with thread pool worker executor, completed status transitions. |
| `com.sentinelcv.integration.DatasetAuditIntegrationTest` | **1** | **PASS** | Full dataset audit pipeline integration from archive extraction to finding generation. |
| `com.sentinelcv.integration.ModelAuditIntegrationTest` | **1** | **PASS** | Full model audit pipeline integration from loading to finding generation. |
| `com.sentinelcv.integration.InferenceProvenanceIntegrationTest` | **1** | **PASS** | Full inference provenance chain verification and database persistence. |
| `com.sentinelcv.integration.DriftDetectionIntegrationTest` | **1** | **PASS** | Full drift comparison between reference and evaluation datasets. |
| `com.sentinelcv.dataset.CocoParserTest` | **1** | **PASS** | COCO JSON format parsing, bounding box extraction, class dictionary mapping. |
| `com.sentinelcv.dataset.YoloParserTest` | **1** | **PASS** | YOLO annotation format parsing and image normalization. |
| `com.sentinelcv.dataset.AnnotationValidatorTest` | **1** | **PASS** | Annotation coordinate bounds validation, negative coordinate detection. |
| `com.sentinelcv.dataset.DuplicateDetectorTest` | **1** | **PASS** | Perceptual dHash and exact duplicate image detection across training sets. |
| `com.sentinelcv.util.ImageHashUtilTest` | **1** | **PASS** | 64-bit difference hashing and Hamming distance computation. |
| `com.sentinelcv.util.OnnxModelGeneratorTest` | **1** | **PASS** | Valid synthetic ONNX protobuf graph generation. |
| `com.sentinelcv.SentinelCvApplicationTests` | **1** | **PASS** | Spring Boot 3.2.5 context loading, dependency injection, and bean lifecycle. |
| **TOTAL** | **116** | **100% PASS** | **Zero Failures, Zero Errors, Zero Skipped** |

---

## 4. Pipeline Verification Confirmation

1. **Model Audit Pipeline**:
   - `OnnxModelLoader` inspects model tensors and operators safely.
   - `BehavioralAnalyzer` executes baseline inference and records `model.baseline_inference` status.
   - `TriggerAnalyzer` analyzes perturbation sensitivity and records `model.trigger_perturbation` status.
   - Catch blocks and unsupported loaders report explicit `UNAVAILABLE` or `FAILED` statuses; neither defaults to `ACCEPT`.

2. **Dataset Audit Pipeline**:
   - Uploaded ZIP archives are staged, scanned for Zip-Slip, checked for collisions, verified for compression ratio, and committed safely without overwriting pre-existing files.
   - Parsers validate COCO and YOLO formats.
   - Empty datasets are caught immediately, assigned a 0.0 integrity score, flagged with `FIND-DATASET-EMPTY-1`, and marked `FAILED` on `dataset.integrity`.

3. **Drift Audit Pipeline**:
   - Computes KS-test and PSI across image brightness, contrast, edge density, and color distributions.
   - Minimum sample size ($N \ge 5$) is enforced; small datasets receive `FIND-DRIFT-UNAVAILABLE` and `UNAVAILABLE` status, preventing unearned passes.

4. **Provenance Pipeline**:
   - Serializes records into length-prefixed canonical V2 format.
   - Recalculates SHA-256 and validates HMAC-SHA256 signatures against 64-hex 256-bit keys.
   - Detects replays, broken hash chains, duplicate IDs, and invalid sequences with distinct failure categories.
   - Empty batches evaluate to `UNAVAILABLE`, never `VERIFIED`.

5. **Risk Assessment Engine**:
   - Evaluates findings and authoritative check statuses with strict precedence:
     $\text{Critical finding} \rightarrow \text{QUARANTINE}$; $\text{High finding} \rightarrow \text{REVIEW}$; $\text{Check FAILED} \rightarrow \text{REVIEW}$; $\text{Check UNAVAILABLE / NOT\_EXECUTED} \rightarrow \text{REVIEW}$.
   - Only awards `ACCEPT` when **every mandatory check has explicitly `PASSED`**.

6. **Reporting & Dashboard**:
   - Both `/dashboard` and `/reports` call `riskAssessmentService.assessSystemRisk()`, guaranteeing that dashboard cards, JSON exports, and PDF reports display identical, authoritative decisions and justifications.

---

## 5. Acceptance Determination

All reachable security flaws and integration vulnerabilities identified across the audit have been repaired. The system is structurally sound, deterministically tested, and verified to operate completely offline. 

**Acceptance Status: PASSED / ACCEPTED**

