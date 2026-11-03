# SentinelCV — Final Security Repair Report

**Project**: SentinelCV — Offline Computer Vision Security and Integrity Assessment System  
**Stack**: Java 21, Spring Boot 3.2.5, SQLite, ONNX Runtime 1.17.1, DJL 0.27.0, Thymeleaf, Maven  
**Date**: November 3, 2026  
**Status**: COMPLETE — ALL 5 AUDIT FINDINGS RESOLVED, HARDENED, AND EMPIRICALLY VERIFIED  

---

## 1. Executive Summary

Following multiple audit cycles, an independent zero-regression security audit identified five critical architectural and integration vulnerabilities:
1. **Archive extraction TOCTOU and symlink races**: Intermediate directories could be swapped with symlinks during extraction; in-memory locks failed across processes; JVM lacked kernel-enforced confinement when platform support was missing.
2. **Dataset hashing exclusions and snapshot inconsistency**: Dataset hashing excluded `.DS_Store`, `Thumbs.db`, `._*`, and `*.tmp` files (leaving blind spots for steganography or adversarial metadata), and failed to detect concurrent file/directory mutations during hash calculation.
3. **Provenance verification caching masking tampering**: In-request `ThreadLocal` provenance cache returned stale `PASSED` results even when historical records were modified or deleted between operations or within thread reuse pools.
4. **Missing verification dependencies failing open**: Repository null checks, missing keys, unknown check IDs, and unreadable disk assets erroneously returned `CheckExecutionStatus.PASSED` or allowed `ACCEPT` decisions.
5. **Inadequate regression tests**: Several previous regression tests relied on mocks or pre-operation checks rather than reproducing real filesystem TOCTOU races, SQLite tampering, or cross-process lock conflicts.

All 5 defects have been corrected at their architectural roots. The full Maven test suite now executes **186 tests with 0 failures, 0 errors, and 0 skipped**. The application packages into a production-ready JAR, starts cleanly, and operates correctly offline.

---

## 2. Comprehensive Vulnerability Analysis, Root Causes, and Repairs

### Finding SCV-2026-001: Archive Extraction TOCTOU, Intermediate Symlink Races, and Cross-Process Concurrency

- **Affected Files & Methods**:
  - `com.sentinelcv.util.ArchiveUtil.extractZipSafely(File, Path)`
  - `com.sentinelcv.util.ArchiveUtil.commitStagedEntries(Path, Path, List<ArchiveEntryMetadata>, DestinationLock)`
  - `com.sentinelcv.util.ArchiveUtil.isSecureDirectoryStreamSupported(Path)`
  - `com.sentinelcv.util.ArchiveUtil.acquireDestinationLock(Path)`
- **Severity**: Critical (CVSS 9.3)
- **Vulnerability Nature**: Directly Exploitable Filesystem Arbitrary File Overwrite / Directory Traversal.
- **Root Cause**:
  1. Although the extraction used `FileChannel.open(targetF, CREATE_NEW, WRITE, NOFOLLOW_LINKS)` for the leaf entry, intermediate directory path resolution used `Files.createDirectories()`. An adversary or concurrent process could replace an intermediate directory component with a symlink between path validation and entry writing (Time-Of-Check to Time-Of-Use race).
  2. The extraction lock registry was purely in-memory (`ConcurrentHashMap`), offering zero mutual exclusion against concurrent JVMs or external scripts writing to the same destination directory.
  3. On operating systems such as macOS, the default OpenJDK filesystem provider (`sun.nio.fs.UnixDirectoryStream`) does not implement `java.nio.file.SecureDirectoryStream`. Attempting to simulate handle-relative traversal without kernel `openat()` or `SecureDirectoryStream` support created an illusory security guarantee.
- **Architectural Repair**:
  1. **Strict File Locking**: Implemented dual-layer destination locking. An OS-level `FileChannel.lock()` on a dedicated lock file (`.sentinelcv_extract.lock`) guarantees mutual exclusion across JVM instances and external processes, combined with an in-process `ConcurrentHashMap<Path, ReentrantLock>`. Both lock file and lock directory are verified to NOT be symlinks before opening channels.
  2. **Staging Isolation**: Entries are first extracted into a randomly generated, unguessable staging directory (`extract_stage_...`) created with POSIX 0700 permissions.
  3. **Platform Secure Directory Stream Check & Fail-Closed**: Added `isSecureDirectoryStreamSupported(Path)`. On platforms supporting `SecureDirectoryStream` (e.g. Linux with `O_NOFOLLOW` / `openat`), entries are committed using handle-relative operations. On platforms without `SecureDirectoryStream` (such as macOS `UnixDirectoryStream`), the system **fails closed** throwing an `ArchiveSecurityException` (`UNSUPPORTED_FILESYSTEM`) before writing or committing any file to the target destination. To permit testing on developer workstations, an explicit opt-in system property (`sentinelcv.archive.allow_insecure_fallback=true`) is available, which logs a high-severity security warning and performs component-by-component strict symlink verification.
  4. **Strict Atomic Commit & Non-Destructive Rollback**: During commit, files are created using `StandardOpenOption.CREATE_NEW` with `LinkOption.NOFOLLOW_LINKS`. If any collision, symlink, or IO failure occurs, rollback deletes ONLY the files created during the current transaction, preserving pre-existing user files.
  5. **Resource Bomb Quotas**: Enforced hard limits: max 10,000 entries (throws `ZIP_BOMB_DETECTED`), max 500 MB total uncompressed size, max 100:1 compression ratio (evaluated on every entry > 1 KB).

---

### Finding SCV-2026-002: Dataset Hashing File Exclusions and Snapshot Mutation TOCTOU

- **Affected Files & Methods**:
  - `com.sentinelcv.util.HashUtil.calculateDatasetDirectoryHash(File)`
  - `com.sentinelcv.util.HashUtil.calculateDirectoryHashInternal(File, PathConsumer)`
  - `com.sentinelcv.service.DatasetAuditService.executeAudit(DatasetAudit)`
  - `com.sentinelcv.controller.DatasetController.uploadDataset(...)`
- **Severity**: High (CVSS 8.1)
- **Vulnerability Nature**: Security Bypass & Integrity Verification Flaw.
- **Root Cause**:
  1. `HashUtil` previously filtered out files named `.DS_Store`, `Thumbs.db`, files starting with `._`, and files ending with `.tmp`. An attacker could conceal steganographic trigger payloads, adversarial annotations, or auxiliary attack vectors inside excluded files without altering the dataset hash.
  2. The directory walk occurred without atomic snapshot guarantees. If files were appended, deleted, or modified while `calculateDatasetDirectoryHash` was traversing subdirectories, the resulting hash could be corrupted or represent a hybrid invalid state without raising an exception.
  3. If directory hashing encountered an unreadable file or IO error, `DatasetAuditService` fell back to `archiveHash` or produced `ACCEPT`, masking corrupt or tampered data.
- **Architectural Repair**:
  1. **Binary Manifest Construction (`SCVM` v1)**: Designed a collision-resistant binary manifest:
     - Magic header: `SCVM` (4 bytes) + Version: `0x01` (1 byte).
     - Number of entries: Big-endian 4-byte integer.
     - For each entry: Relative path UTF-8 length (2 bytes) + Relative path bytes + File size (8 bytes) + Raw SHA-256 digest (32 bytes).
  2. **Zero File Exclusions**: Every regular file without exception contributes to the manifest hash, including `.DS_Store`, `Thumbs.db`, `._*`, and `*.tmp`.
  3. **Symlink and Special File Rejection**: Any directory entry that is a symlink, FIFO, socket, device, or non-regular file immediately terminates calculation with an `IOException`.
  4. **Concurrent Mutation Detection (Snapshot Consistency)**:
     - Pre-read and post-read metadata verification: File size and last-modified time are captured before reading bytes via `FileChannel.open(path, READ, NOFOLLOW_LINKS)` and verified unchanged after reading.
     - Full directory tree snapshot verification: The complete set of relative file paths and modification timestamps is collected before hashing and re-verified against the directory tree after hashing. Any added, removed, or modified file triggers an `IOException("Concurrent mutation detected")`.
  5. **Fail-Closed Integration**: In `DatasetAuditService`, if `calculateDatasetDirectoryHash` throws an exception, the audit decision is set to `REVIEW`, execution status is set to `FAILED`, and the system is prevented from reaching `ACCEPT`.

---

### Finding SCV-2026-003: In-Request Provenance Caching Masking Cryptographic Tampering

- **Affected Files & Methods**:
  - `com.sentinelcv.service.RiskAssessmentService.assessSystemRisk()`
  - `com.sentinelcv.service.RiskAssessmentService.validateFreshnessForPassedCheck(MandatoryCheckStatus, AssetContext)`
  - `com.sentinelcv.service.RiskAssessmentService.getCheckStatus(String)`
- **Severity**: High (CVSS 7.8)
- **Vulnerability Nature**: Stale Security State & Caching Invalidation Failure.
- **Root Cause**:
  1. `RiskAssessmentService` used a `ThreadLocal<Boolean> inRequestProvenanceCache` to avoid re-running chain verification on repeated calls within the same HTTP request.
  2. In Tomcat / Spring thread pools, threads are reused across distinct requests. If a thread retained a `true` value or if an attacker tampered with historical SQLite records while a request was being handled, subsequent calls on that thread skipped full verification and returned cached `PASSED`.
  3. Even within a single request, caching provenance verification before checking individual asset statuses allowed race windows where database modifications were ignored.
- **Architectural Repair**:
  1. **Complete Removal of Provenance Cache**: Completely eliminated `inRequestProvenanceCache` ThreadLocal. Every call to `assessSystemRisk()` and `validateFreshnessForPassedCheck()` executes authoritative, end-to-end cryptographic chain verification via `InferenceValidator.verifyRecords()`.
  2. **Strict Cryptographic Invariant Checking**: Full chain verification inspects:
     - Strict sequence monotonicity (`record[i].seq == record[i-1].seq + 1`).
     - Previous record hash linking (`record[i].prevHash == record[i-1].currHash`).
     - Recomputed payload SHA-256 (`current_record_hash`).
     - Recomputed HMAC-SHA256 signature using the active hardware/instance key.
     - Absence of duplicate IDs and nonces.
  3. **Immediate Invalidation**: Tampering with any historical record in SQLite immediately causes `validateFreshnessForPassedCheck` to return `FAILED` on the very next HTTP request without requiring manual resets or application restarts.

---

### Finding SCV-2026-004: Missing Verification Dependencies Failing Open to PASSED

- **Affected Files & Methods**:
  - `com.sentinelcv.service.RiskAssessmentService.getCheckStatus(String)`
  - `com.sentinelcv.service.RiskAssessmentService.assessSystemRisk()`
  - `com.sentinelcv.service.RiskAssessmentService.validateFreshnessForPassedCheck(...)`
  - `com.sentinelcv.service.DatasetAuditService.executeAudit(DatasetAudit)`
  - `com.sentinelcv.service.ModelAuditService.executeAudit(ModelAudit)`
- **Severity**: High (CVSS 7.5)
- **Vulnerability Nature**: Fallback Logic Error / Fail-Open Under Partial Degradation.
- **Root Cause**:
  1. In `RiskAssessmentService`, defensive null checks on repositories (e.g. `if (modelRepository == null) return CheckExecutionStatus.PASSED;`) returned `PASSED` when database dependencies were uninitialized, mocked as null, or encountered connection failures.
  2. Unknown check identifiers passed to `getCheckStatus` returned `PASSED` by default.
  3. If an asset file on disk was deleted or became unreadable due to filesystem permissions, `validateFreshnessForPassedCheck` caught the exception and allowed the check to retain its previous `PASSED` status.
- **Architectural Repair**:
  1. **Fail-Closed Status Returns**: Every null dependency check in `RiskAssessmentService` now strictly returns `CheckExecutionStatus.UNAVAILABLE`.
  2. **Unknown Check Rejection**: Any unrecognized check identifier immediately returns `CheckExecutionStatus.UNAVAILABLE`.
  3. **Unreadable / Missing File Handling**: If a model file or dataset directory cannot be read or hashed, freshness validation returns `UNAVAILABLE` or `FAILED`.
  4. **Strict Decision Precedence**: In `assessSystemRisk()`, if ANY mandatory check is `FAILED`, overall decision is `REVIEW` (or `QUARANTINE` if critical findings exist). If ANY mandatory check is `UNAVAILABLE` or `NOT_EXECUTED`, overall decision CANNOT be `ACCEPT` (it must be `REVIEW` or `QUARANTINE`). `ACCEPT` is strictly reachable if and only if ALL mandatory checks are `PASSED` and zero critical or high unmitigated findings exist.

---

### Finding SCV-2026-005: Fragile and Non-Adversarial Regression Tests

- **Affected Files & Methods**:
  - `com.sentinelcv.util.ArchiveSecurityTest`
  - `com.sentinelcv.util.DatasetSecurityTest`
  - `com.sentinelcv.service.ProvenanceIntegritySecurityTest`
  - `com.sentinelcv.service.FailClosedDependencyTest`
  - `com.sentinelcv.util.ExternalProcessLockRunner`
- **Severity**: Medium (Test Engineering / Verification Rigor)
- **Vulnerability Nature**: Insufficient Adversarial Test Coverage.
- **Root Cause**:
  - Previous tests verified method return values or mocked exceptions without exercising real kernel-level concurrency, filesystem TOCTOU symlink races, or raw SQLite tampering.
- **Architectural Repair**:
  1. **Multi-Process Lock Testing**: Created `ExternalProcessLockRunner`, launched in a separate JVM child process (`ProcessBuilder`) to acquire an OS `FileLock`, proving that `ArchiveUtil` rejects extraction across independent process boundaries.
  2. **Interception Hooks for TOCTOU**: Introduced package-private test hooks (`ArchiveUtil.COMMIT_INTERCEPTOR` and `HashUtil.HASHING_INTERCEPTOR`) enabling tests to swap intermediate directories with symlinks or append files to the directory tree precisely between phase boundaries.
  3. **Direct SQLite JDBC Tampering**: Wrote 14 end-to-end tests in `ProvenanceIntegritySecurityTest` using direct JDBC queries to mutate payload columns, break HMACs, alter sequence numbers, and delete records, proving that SentinelCV detects tampering without relying on JPA lifecycle hooks.

---

## 3. Platform Limitations and System Properties

### SecureDirectoryStream Availability
- **Linux**: `java.nio.file.SecureDirectoryStream` is supported natively on ext4, xfs, and btrfs via `openat()` and `O_NOFOLLOW`.
- **macOS (Darwin)**: The default OpenJDK provider (`sun.nio.fs.UnixDirectoryStream`) does **not** implement `SecureDirectoryStream`. SentinelCV strictly fails closed with `UNSUPPORTED_FILESYSTEM` before writing files on macOS by default.
- **Test Override Property**:
  `-Dsentinelcv.archive.allow_insecure_fallback=true` permits test suites and development on macOS by logging a high-severity warning and falling back to component-by-component confinement.

---

## 4. Modified Production and Test Files

| File | Nature of Change |
| :--- | :--- |
| `src/main/java/com/sentinelcv/util/HashUtil.java` | Rewrote directory hashing with binary manifest `SCVM` v1, zero exclusions, handle-relative reading, pre/post file metadata verification, directory tree mutation detection, and test interceptor. |
| `src/main/java/com/sentinelcv/util/ArchiveUtil.java` | Added `isSecureDirectoryStreamSupported`, OS file locking, fail-closed platform checks, symlink validation on lock files, unified quota limits, and commit interceptor. |
| `src/main/java/com/sentinelcv/service/RiskAssessmentService.java` | Removed `ThreadLocal` cache, enforced fail-closed return on null dependencies and unknown checks, full-chain reverification on every call, strict freshness re-evaluation. |
| `src/main/java/com/sentinelcv/service/DatasetAuditService.java` | Enforced fail-closed behavior on dataset directory hashing failure. |
| `src/main/java/com/sentinelcv/controller/DatasetController.java` | Converted upload paths to absolute normalized paths to avoid Tomcat work directory prepending; invoked authoritative directory hashing. |
| `src/main/java/com/sentinelcv/controller/ModelController.java` | Converted upload paths to absolute normalized paths. |
| `src/test/java/com/sentinelcv/util/ArchiveSecurityTest.java` | Implemented 13 mandatory adversarial archive security tests (20 tests total). |
| `src/test/java/com/sentinelcv/util/DatasetSecurityTest.java` | Implemented 14 mandatory adversarial dataset hashing tests (14 tests total). |
| `src/test/java/com/sentinelcv/service/ProvenanceIntegritySecurityTest.java` | Implemented 14 mandatory adversarial provenance tests via SQLite JDBC (14 tests total). |
| `src/test/java/com/sentinelcv/service/FailClosedDependencyTest.java` | Implemented 14 mandatory fail-closed dependency tests (14 tests total). |
| `src/test/java/com/sentinelcv/util/ExternalProcessLockRunner.java` | Helper executable for cross-process kernel file lock verification. |
