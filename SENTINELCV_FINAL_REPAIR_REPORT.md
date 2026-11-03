# SENTINELCV — FINAL SECURITY AND CORRECTNESS REPAIR REPORT

**System Title**: SentinelCV: Offline Computer Vision Security and Integrity Assessment System  
**Classification**: Offline Academic Security Assessment Prototype  
**Date**: November 2, 2026  
**Test Suite Status**: 96 Tests Run, 96 Passed, 0 Failures, 0 Errors (`BUILD SUCCESS`)  

---

## 1. Executive Summary

An independent security and correctness audit of the SentinelCV codebase identified five critical vulnerabilities and logic flaws:
1. **Archive Extraction Security**: Excessive quota bounds, missing compression ratio enforcement, lack of Unix symlink detection on archive entries, destination symlink vulnerabilities, and non-selective failure rollback.
2. **Provenance Canonical Serialization**: Canonical payload omitted `recordId` and `modelIdentifier`, used delimiter-based serialization susceptible to field injection collisions, and lacked explicit schema versioning.
3. **Provenance Key Management**: Local key file permissions did not enforce restrictive `0600` POSIX access, had potential secret leakage, and lacked explicit operator escalation on corrupted key files.
4. **Model Audit Decision Logic**: Missing verification of trigger perturbation analysis execution, allowing models with failed trigger tests to be marked `ACCEPT`.
5. **Global Risk Assessment Availability Precedence**: Fragile inference of check availability from severity strings/descriptions, vulnerability to positive numeric scores overriding unavailable mandatory checks, and fragile batch provenance precedence downgrading tampered records to `REVIEW` when replays were present.

All five vulnerabilities have been repaired in their respective production classes, verified through extensive negative and edge-case regression test suites, and validated across the complete 96-test suite. The existing Spring Boot + SQLite + Thymeleaf architecture, dashboard templates, and offline operation were 100% preserved.

---

## 2. Modified and Created Files

### Production Source Files
- [`src/main/java/com/sentinelcv/util/ArchiveUtil.java`](file:///Users/teju/Desktop/Java-project/src/main/java/com/sentinelcv/util/ArchiveUtil.java): Hardened ZIP extraction with 10k entry limit, 500MB total extraction limit, 100MB single-file limit, 100:1 compression ratio limit, Unix symlink entry detection (`ZipFile`), destination/parent symlink validation, and selective atomic rollback preserving pre-existing files.
- [`src/main/java/com/sentinelcv/inference/InferenceValidator.java`](file:///Users/teju/Desktop/Java-project/src/main/java/com/sentinelcv/inference/InferenceValidator.java): Implemented Schema V2 length-prefixed canonical encoding (`V2|<len>:<val>;...`) authenticating all 10 security fields, identical canonical bytes for SHA-256 and HMAC, and tracked `cryptographicFailures`.
- [`src/main/java/com/sentinelcv/provenance/ProvenanceKeyManager.java`](file:///Users/teju/Desktop/Java-project/src/main/java/com/sentinelcv/provenance/ProvenanceKeyManager.java): 256-bit `SecureRandom` key generation, atomic temporary file creation with `0600` POSIX permissions before writing secret, non-POSIX fallback, concurrency lock, secret safety in logs/exceptions, and explicit operator escalation on corrupt keys.
- [`src/main/java/com/sentinelcv/service/ModelAuditService.java`](file:///Users/teju/Desktop/Java-project/src/main/java/com/sentinelcv/service/ModelAuditService.java): Mandatory trigger perturbation analysis check (`!triggerReport.executed`) required for `ACCEPT`.
- [`src/main/java/com/sentinelcv/enums/CheckExecutionStatus.java`](file:///Users/teju/Desktop/Java-project/src/main/java/com/sentinelcv/enums/CheckExecutionStatus.java): Added structured check execution status enum (`PASSED`, `FAILED`, `UNAVAILABLE`, `NOT_EXECUTED`).
- [`src/main/java/com/sentinelcv/service/RiskAssessmentService.java`](file:///Users/teju/Desktop/Java-project/src/main/java/com/sentinelcv/service/RiskAssessmentService.java): Added explicit check execution status support, strict precedence (Critical $\to$ `QUARANTINE`, High $\to$ `REVIEW`, Unavailable/Not Executed $\to$ `REVIEW`, All passed $\to$ `ACCEPT`), prevented numerical score overrides, and blocked silent passing of missing statuses.
- [`src/main/java/com/sentinelcv/service/InferenceProvenanceService.java`](file:///Users/teju/Desktop/Java-project/src/main/java/com/sentinelcv/service/InferenceProvenanceService.java): Enforced strict precedence (Broken chain/tamper $\to$ `QUARANTINE`, Replay alone $\to$ `REVIEW`, Missing key $\to$ `REVIEW`, All valid $\to$ `VERIFIED`) independent of record processing order.
- [`src/main/java/com/sentinelcv/config/DataInitializer.java`](file:///Users/teju/Desktop/Java-project/src/main/java/com/sentinelcv/config/DataInitializer.java): Seeded database records re-generated using Schema V2 length-prefixed canonical payloads.

### Test Source Files
- [`src/test/java/com/sentinelcv/util/ArchiveSecurityTest.java`](file:///Users/teju/Desktop/Java-project/src/test/java/com/sentinelcv/util/ArchiveSecurityTest.java): 12 tests covering Zip-Slip, absolute path traversal, destination symlinks, entry symlinks, entry quotas, single-file quotas, total extraction quotas, compression ratio violation, null bytes, magic bytes, and selective atomic rollback.
- [`src/test/java/com/sentinelcv/inference/InferenceValidatorTest.java`](file:///Users/teju/Desktop/Java-project/src/test/java/com/sentinelcv/inference/InferenceValidatorTest.java): 18 tests covering V2 length-prefixed canonical payload, field hashes, genesis, recordId/modelId modification, delimiter collision immunity, HMAC byte identity, missing key `UNAVAILABLE`, and legacy schema rejection.
- [`src/test/java/com/sentinelcv/provenance/ProvenanceKeyManagerTest.java`](file:///Users/teju/Desktop/Java-project/src/test/java/com/sentinelcv/provenance/ProvenanceKeyManagerTest.java): 8 tests covering 256-bit key generation, reuse, corrupt key handling, concurrency, POSIX 0600 permissions, and secret safety in logs/exceptions.
- [`src/test/java/com/sentinelcv/model/RealOnnxModelAuditTest.java`](file:///Users/teju/Desktop/Java-project/src/test/java/com/sentinelcv/model/RealOnnxModelAuditTest.java): 10 tests covering baseline inference succeeds + trigger analysis fails $\to$ `REVIEW`, behavioral analysis fails $\to$ `REVIEW`, ONNX inference unavailable $\to$ `REVIEW`, critical finding $\to$ `QUARANTINE`, high finding $\to$ `REVIEW`, all mandatory checks pass $\to$ `ACCEPT`.
- [`src/test/java/com/sentinelcv/service/RiskAssessmentServiceTest.java`](file:///Users/teju/Desktop/Java-project/src/test/java/com/sentinelcv/service/RiskAssessmentServiceTest.java): 10 tests covering structured `CheckExecutionStatus`, strict precedence, MEDIUM finding + unavailable check $\to$ `REVIEW`, INFO finding + unavailable check $\to$ `REVIEW`, Critical finding + unavailable check $\to$ `QUARANTINE`, High finding $\to$ `REVIEW`, all mandatory checks pass $\to$ `ACCEPT`, missing check status not silently treated as PASSED.
- [`src/test/java/com/sentinelcv/service/InferenceProvenanceServiceTest.java`](file:///Users/teju/Desktop/Java-project/src/test/java/com/sentinelcv/service/InferenceProvenanceServiceTest.java): 6 tests covering broken chain + replay $\to$ `QUARANTINE`, tampered payload + replay $\to$ `QUARANTINE`, replay alone $\to$ `REVIEW`, missing key $\to$ `REVIEW`, entire valid chain $\to$ `VERIFIED`, and input record order independence.

### Documentation Files
- [`SECURITY_LIMITATIONS.md`](file:///Users/teju/Desktop/Java-project/SECURITY_LIMITATIONS.md)
- [`README.md`](file:///Users/teju/Desktop/Java-project/README.md)
- [`TESTING_REPORT.md`](file:///Users/teju/Desktop/Java-project/TESTING_REPORT.md)
- [`PROJECT_STATUS.md`](file:///Users/teju/Desktop/Java-project/PROJECT_STATUS.md)
- [`PROJECT_REPORT.md`](file:///Users/teju/Desktop/Java-project/PROJECT_REPORT.md)

---

## 3. Explanation of Vulnerabilities and Repairs

### Phase 1: Archive Extraction Security
- **Vulnerability**: Max entries was 50,000, max single file was 500 MB, max total size was 2 GB. Compression ratio checking was missing, leaving the system vulnerable to multi-gigabyte decompression bombs (e.g. 42.zip). Symlink archive entries and symlinked destination directories were not rejected. Extraction failure deleted the entire target directory rather than selectively rolling back only newly created files.
- **Repair**:
  - Implemented exact quantitative limits: `MAX_ENTRIES = 10,000`, `MAX_TOTAL_SIZE = 500 MB`, `MAX_SINGLE_ENTRY_SIZE = 100 MB`, `MAX_COMPRESSION_RATIO = 100.0`.
  - Added `validateDestinationDirectory(targetDirectory)` rejecting symbolic link destination paths and non-system symbolic link parent directories.
  - Replaced `java.util.zip.ZipInputStream` with Apache Commons Compress `ZipFile`, enabling access to central directory Unix external attributes (`entry.isUnixSymlink()` or `(mode & 0170000) == 0120000`) to detect and reject symlink entries before extraction.
  - Implemented `rollbackCreated(targetDirectory, initialFiles, createdPaths)` that snapshots files prior to extraction and on failure deletes only newly created entries, preserving pre-existing files.
- **Before vs After**:
  - *Before*: A 1KB deflate bomb expanded into gigabytes; symlinked destinations were followed; failed extraction deleted pre-existing files in the target directory.
  - *After*: Deflate bomb rejected with `SentinelCvException` ("Compression ratio limit exceeded"); symlinks blocked; pre-existing files remain untouched after rollback.

### Phase 2: Provenance Canonical Payload
- **Vulnerability**: Canonical payload omitted `recordId` and `modelIdentifier`, allowing these fields to be tampered with without invalidating cryptographic verification. Simple pipe delimiter serialization was vulnerable to delimiter injection collisions (e.g. `rec:1` + `model` vs `rec` + `1:model`).
- **Repair**:
  - Introduced Schema V2 length-prefixed canonical encoding:
    `V2|<len>:<recordId>;<len>:<sequenceNumber>;<len>:<timestamp>;<len>:<modelIdentifier>;<len>:<modelHash>;<len>:<inputImageHash>;<len>:<configHash>;<len>:<outputHash>;<len>:<nonce>;<len>:<previousRecordHash>`
  - Included all 10 security-relevant immutable record fields.
  - Length prefixes (`<len>:<val>;`) eliminate delimiter ambiguity and field boundary injection attacks.
  - Exactly identical canonical bytes are used for SHA-256 record hashing and HMAC generation/verification.
  - Missing keys or signatures produce `UNAVAILABLE` and cannot silently produce `VERIFIED`.
- **Before vs After**:
  - *Before*: Modifying `recordId` or `modelIdentifier` left SHA-256 hash and HMAC valid.
  - *After*: Modifying any authenticated field immediately causes SHA-256 and HMAC mismatch, failing overall verification.

### Phase 3: Hardened Key Management
- **Vulnerability**: Keys were written to local disk without guaranteed POSIX `0600` permissions. Corrupt key files were vulnerable to silent overwriting. Secret key material could be printed in exceptions or logs.
- **Repair**:
  - 256-bit key generated using `java.security.SecureRandom`.
  - Created key files atomically via temporary file and `StandardCopyOption.ATOMIC_MOVE`.
  - Permissions set immediately upon file creation to `0600` (`PosixFilePermissions.asFileAttribute(OWNER_READ | OWNER_WRITE)`), with restrictive fallback on non-POSIX filesystems.
  - Kept secret key material out of log messages and exception strings.
  - Corrupt key files (< 32 characters) throw explicit `IllegalStateException` demanding manual operator intervention rather than silently generating new keys.
- **Before vs After**:
  - *Before*: Default umask applied to key file; corrupt keys silently overwritten.
  - *After*: File strictly created with `0600` (`rw-------`); corrupt keys halt with explicit error demanding operator intervention.

### Phase 4: Model Audit Decision Logic
- **Vulnerability**: `ModelAuditService` checked `!metadata.inferenceSupported || !behavioralResult.inferenceExecuted`, but did NOT verify `!triggerReport.executed`. If trigger analysis failed, was skipped, or caught an exception, the model could still receive `ACCEPT`.
- **Repair**:
  - Updated decision condition to:
    ```java
    if (crit > 0) {
        decision = AssessmentDecision.QUARANTINE;
    } else if (high > 0 || triggerReport.unusualSensitivityDetected) {
        decision = AssessmentDecision.REVIEW;
    } else if (!metadata.inferenceSupported || !behavioralResult.inferenceExecuted || !triggerReport.executed) {
        decision = AssessmentDecision.REVIEW;
    } else {
        decision = AssessmentDecision.ACCEPT;
    }
    ```
- **Before vs After**:
  - *Before*: Baseline inference succeeded + perturbation analysis failed $\to$ `ACCEPT`.
  - *After*: Baseline inference succeeded + perturbation analysis failed $\to$ `REVIEW`.

### Phase 5: Global Risk Assessment Precedence
- **Vulnerability**: Unavailable checks were detected only if finding severity was `INFO` and description contained "unavailable". An unavailable check reported as `MEDIUM` severity was ignored, allowing a low risk score to falsely produce `ACCEPT`.
- **Repair**:
  - Created explicit `CheckExecutionStatus` enum (`PASSED`, `FAILED`, `UNAVAILABLE`, `NOT_EXECUTED`).
  - Added structured check status map support in `RiskAssessmentService.evaluateFindings(findings, checkStatuses)`.
  - Enforced strict precedence:
    1. Critical findings $\to$ `QUARANTINE`.
    2. High findings $\to$ `REVIEW`.
    3. Any required check `UNAVAILABLE` or `NOT_EXECUTED` $\to$ `REVIEW`.
    4. Any check `FAILED` $\to$ `REVIEW`.
    5. `ACCEPT` strictly when all required checks `PASSED`.
  - Added `isUnavailableFinding(f)` checking across all severities (`CRITICAL`, `HIGH`, `MEDIUM`, `LOW`, `INFO`).
  - Missing check status is never silently treated as `PASSED`.
- **Before vs After**:
  - *Before*: `MEDIUM` severity finding representing an unavailable check resulted in `ACCEPT` (score = 6.0 < 40.0).
  - *After*: `MEDIUM` severity finding representing an unavailable check strictly results in `REVIEW`.

### Phase 6: Provenance Batch Decision Precedence
- **Vulnerability**: In `InferenceProvenanceService`, condition `(batchResult.failedCount > 0 && batchResult.replayAttempts == 0)` meant that if a batch had BOTH a replay attempt AND a tampered payload/hash mismatch, the replay condition evaluated to true while the tamper condition was bypassed, downgrading `QUARANTINE` to `REVIEW`.
- **Repair**:
  - Tracked `cryptographicFailures` in `InferenceValidator.VerificationBatchResult`.
  - Emitted critical tamper finding regardless of whether replay indicators are present.
  - Enforced strict decision precedence:
    ```java
    boolean hasIntegrityCompromise = batchResult.brokenChainLinks > 0
            || batchResult.cryptographicFailures > 0
            || (batchResult.failedCount > 0 && batchResult.failedCount > batchResult.replayAttempts);

    if (hasIntegrityCompromise) {
        assessmentDecision = "QUARANTINE";
    } else if (batchResult.replayAttempts > 0) {
        assessmentDecision = "REVIEW";
    } else if (batchResult.unavailableCount > 0) {
        assessmentDecision = "REVIEW";
    } else {
        assessmentDecision = "VERIFIED";
    }
    ```
  - Ensured decision is independent of input record ordering.
- **Before vs After**:
  - *Before*: Batch with broken chain or tampered payload + replay indicator received `REVIEW`.
  - *After*: Batch with broken chain or tampered payload + replay indicator strictly receives `QUARANTINE`.

---

## 4. Test Execution Results

Command executed:
```bash
./mvnw_local clean test verify
```

Output Summary:
```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.sentinelcv.util.HashUtilTest (3 tests) - PASSED
[INFO] Running com.sentinelcv.util.CryptoUtilTest (3 tests) - PASSED
[INFO] Running com.sentinelcv.util.ImageHashUtilTest (1 test) - PASSED
[INFO] Running com.sentinelcv.util.StatisticalUtilTest (3 tests) - PASSED
[INFO] Running com.sentinelcv.util.ArchiveSecurityTest (12 tests) - PASSED
[INFO] Running com.sentinelcv.drift.DriftStatisticalEdgeCasesTest (7 tests) - PASSED
[INFO] Running com.sentinelcv.dataset.CocoParserTest (1 test) - PASSED
[INFO] Running com.sentinelcv.dataset.YoloParserTest (1 test) - PASSED
[INFO] Running com.sentinelcv.dataset.DuplicateDetectorTest (1 test) - PASSED
[INFO] Running com.sentinelcv.dataset.AnnotationValidatorTest (1 test) - PASSED
[INFO] Running com.sentinelcv.model.OnnxModelGeneratorTest (1 test) - PASSED
[INFO] Running com.sentinelcv.provenance.ProvenanceKeyManagerTest (8 tests) - PASSED
[INFO] Running com.sentinelcv.SentinelCvApplicationTests (1 test) - PASSED
[INFO] Running com.sentinelcv.model.RealOnnxModelAuditTest (10 tests) - PASSED
[INFO] Running com.sentinelcv.inference.InferenceValidatorTest (18 tests) - PASSED
[INFO] Running com.sentinelcv.service.AsyncAuditExecutionTest (2 tests) - PASSED
[INFO] Running com.sentinelcv.service.InferenceProvenanceServiceTest (6 tests) - PASSED
[INFO] Running com.sentinelcv.service.RiskAssessmentServiceTest (10 tests) - PASSED
[INFO] Running com.sentinelcv.integration.DatasetAuditIntegrationTest (1 test) - PASSED
[INFO] Running com.sentinelcv.integration.ModelAuditIntegrationTest (1 test) - PASSED
[INFO] Running com.sentinelcv.integration.InferenceProvenanceIntegrationTest (1 test) - PASSED
[INFO] Running com.sentinelcv.integration.DriftDetectionIntegrationTest (1 test) - PASSED
[INFO] Running com.sentinelcv.integration.ReportServiceIntegrationTest (3 tests) - PASSED
[INFO] 
[INFO] Results:
[INFO] Tests run: 96, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

---

## 5. Remaining Limitations and Boundary Disclaimers

1. **Software vs. Hardware Attestation**:
   Valid SHA-256 hashes and HMACs prove internal consistency with recorded metadata and detect log tampering/deletion. However, they do not prove that inference was genuinely executed inside a hardware-isolated enclave (e.g. Intel SGX / AMD SEV). An attacker with host root access could falsify inference outputs and sign them with a locally accessible key.
2. **Local Keystore Threat Model**:
   Keys stored on local disk in `./data/keys/provenance.key` are protected by POSIX `0600` permissions against other users on the host, but remain accessible to any process executing under the same user UID. This is local offline key management, NOT a Hardware Security Module (HSM).
3. **Synthetic ONNX Model Fixture**:
   The bundled ONNX model fixture (`FixtureGenerator`) is a pure Java binary Protocol Buffer model (`[1, 3, 224, 224]` with `Relu`). It demonstrates genuine ONNX Runtime execution, dynamic tensor flattening, and determinism verification; it is not a trained deep vision classifier.
4. **Heuristic Nature of Perturbation Testing**:
   Trigger perturbation analysis (localized spatial patches and global Gaussian jitter) is an empirical behavioral sensitivity heuristic. It does not mathematically prove the absence of backdoors or trojans.
5. **Academic Prototype Status**:
   SentinelCV is an **offline academic security assessment prototype**, designed for reproducible security research and assessment. It must not be presented as a commercially certified cybersecurity certification appliance.

---

## 6. How to Launch and Verify the Application

### 1. Launch the Application
```bash
./run.sh
```
Or with Maven wrapper:
```bash
./mvnw_local spring-boot:run
```

Access the web dashboard in your browser:
👉 **`http://localhost:8080`**

### 2. Verify Preserved Dashboard and Features
- **Dashboard (`/`)**: Displays active system risk assessment, total datasets, models, inferences, findings counts, and recent audits table.
- **Datasets (`/datasets`)**: Lists seeded `COCO Benchmark Sample` and `YOLO Traffic Sample`. Supports uploading ZIP archives with defensive extraction limits.
- **Models (`/models`)**: Lists `Demo Vision Classifier`. Supports running real ONNX model audits, deterministic repeatability tests, and perturbation analysis.
- **Provenance (`/provenance`)**: Lists 3 seeded cryptographically chained inference records authenticated with V2 canonical encoding. Click **Verify Chain** to observe full verification.
- **Distribution Shift (`/drift`)**: Allows running visual feature drift analysis between datasets with PSI and KS statistics.
- **Reports (`/reports`)**: Generates and downloads JSON, HTML, and OpenPDF audit reports.

---

## 7. Verification Checklist

- [x] Phase 1: Archive Extraction Security limits (10k entries, 500MB total, 100MB per file, 100:1 ratio, Unix symlink rejection, selective atomic rollback)
- [x] Phase 2: Provenance Canonical Payload (V2 schema, length-prefixed encoding, all 10 fields, delimiter collision immunity)
- [x] Phase 3: Provenance Key Management Hardening (256-bit SecureRandom, atomic 0600 POSIX permissions, corrupt key escalation)
- [x] Phase 4: Model Audit Decision Logic (Mandatory trigger perturbation check required for ACCEPT)
- [x] Phase 5: Global Risk Assessment Precedence (Structured CheckExecutionStatus, strict precedence, no numeric score override)
- [x] Phase 6: Provenance Batch Decision Precedence (Broken chain/tamper $\to$ QUARANTINE, Replay alone $\to$ REVIEW, order-independent)
- [x] Phase 7: Full Regression Testing (`./mvnw_local clean test verify` $\to$ 96/96 tests passed, BUILD SUCCESS)
- [x] Phase 8: Documentation Corrections (`README.md`, `TESTING_REPORT.md`, `PROJECT_REPORT.md`, `SECURITY_LIMITATIONS.md`, `PROJECT_STATUS.md`)
- [x] Handover Report: `SENTINELCV_FINAL_REPAIR_REPORT.md` created
