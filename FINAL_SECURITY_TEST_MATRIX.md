# SentinelCV — Final Security Test Matrix

**Project**: SentinelCV — Offline Computer Vision Security and Integrity Assessment System  
**Date**: November 3, 2026  
**Total Tests**: 186 Tests Run | 0 Failures | 0 Errors | 0 Skipped  
**Build Status**: BUILD SUCCESS  

---

## 1. Targeted Adversarial Security Test Matrix (41 Tests)

### Category 1: Archive Extraction Security (`ArchiveSecurityTest.java` — 20 Tests)

| # | Test Name | Attack Setup & Technique | Expected Security Behavior | Actual Result |
| :- | :--- | :--- | :--- | :--- |
| 1 | `testPreExistingSymlinkDirectoryBlocked` | Created symlink `sub` -> `/tmp/forbidden` inside destination before extraction. | Detected before write; `SECURITY_VIOLATION` thrown; external target untouched. | **PASSED** |
| 2 | `testPreExistingSymlinkFileBlocked` | Created symlink file `target.txt` -> `/tmp/forbidden` inside destination. | Detected before write; `SECURITY_VIOLATION` thrown; external target untouched. | **PASSED** |
| 3 | `testIntermediateDirectorySymlinkSwapTOCTOUBlocked` | Registered `COMMIT_INTERCEPTOR` to replace intermediate directory `sub/nested` with a symlink during commit phase. | Committing entry detects symlink via `NOFOLLOW_LINKS`; aborts with `SECURITY_VIOLATION`; external path untouched. | **PASSED** |
| 4 | `testDestinationRootReplacedWithSymlinkBlocked` | Swapped entire extraction destination root directory with a symlink to outside directory. | Detected by canonical path or symlink check; extraction aborted; target untouched. | **PASSED** |
| 5 | `testConcurrentExtractionsToSameDestinationBlocked` | Launched 4 threads simultaneously extracting different archives to the same destination directory. | In-process lock serializes; second extraction rejects with file collision or lock conflict; no corruption. | **PASSED** |
| 6 | `testSeparateJvmExtractionLockConflict` | Launched `ExternalProcessLockRunner` in separate JVM process holding OS `FileLock` on `.sentinelcv_extract.lock`. | Parent JVM fails to acquire kernel lock; throws `ArchiveSecurityException` (`CONCURRENT_EXTRACTION_CONFLICT`). | **PASSED** |
| 7 | `testNoFilesCreatedOutsideDestination` | Extracted archive containing deep nested directories and files. | All created files are strictly canonical descendants of target directory; zero files outside. | **PASSED** |
| 8 | `testExistingFilesOutsideDestinationPreserved` | Created sentinel files outside destination; ran extraction attempt. | Outside files remain unmodified with identical bytes. | **PASSED** |
| 9 | `testRollbackPreservesPreExistingFilesInDestination` | Pre-created `preexisting.txt` inside destination; triggered failed extraction containing invalid entry. | Transaction rolls back only new entries; `preexisting.txt` is intact and preserved. | **PASSED** |
| 10 | `testRollbackIgnoresUnrelatedFilesInParent` | Pre-created `sibling.txt` in parent directory; triggered failed extraction. | Rollback cleans up destination without modifying parent or sibling files. | **PASSED** |
| 11 | `testUnsupportedFilesystemFailsClosed` | Ran extraction on macOS without system property override (`sentinelcv.archive.allow_insecure_fallback`). | `isSecureDirectoryStreamSupported` returns false; throws `ArchiveSecurityException` (`UNSUPPORTED_FILESYSTEM`). | **PASSED** |
| 12 | `testZipSlipPathTraversalBlocked` | Created archive with `../../escaped_secret.txt` entry. | Path traversal detected during staging; extraction rejected; zero files written outside. | **PASSED** |
| 13 | `testAbsoluteEntryPathTraversalBlocked` | Created archive with `/etc/shadow_exploit` entry. | Illegal entry name detected; rejected during staging. | **PASSED** |
| 14 | `testDuplicateEntryNamesBlocked` | Created archive with two identical entries `data/file.txt`. | Duplicate entry detected; rejected with `DUPLICATE_ENTRY`. | **PASSED** |
| 15 | `testNullByteEntryNameBlocked` | Created archive with entry name containing null byte `test\0exploit.txt`. | Null byte detected; rejected with `ILLEGAL_ENTRY_NAME`. | **PASSED** |
| 16 | `testAtomicCreateNewPreventsFileOverwrite` | Destination already contains `protected_user_file.txt`. | Attempt to overwrite fails with `DESTINATION_FILE_EXISTS` via `O_CREAT \| O_EXCL`. | **PASSED** |
| 17 | `testArchiveQuotaLimitsEnforced` | Archive containing > 10,000 entries. | Staging stops immediately; throws `ArchiveSecurityException` (`ZIP_BOMB_DETECTED`). | **PASSED** |
| 18 | `testArchiveTotalSizeLimitEnforced` | Archive with cumulative uncompressed size > 500 MB. | Staging stops; throws `ArchiveSecurityException` (`ZIP_LIMIT_EXCEEDED`). | **PASSED** |
| 19 | `testCompressionRatioBombBlocked` | Archive with 10 MB payload compressed into a tiny zip (> 100:1 ratio). | Staging stops; throws `ArchiveSecurityException` (`ZIP_BOMB_DETECTED`). | **PASSED** |
| 20 | `testSmallEntryCompressionRatioViolationBlocked` | Small 4 KB zero-filled entry compressed into 26 bytes (~157:1 ratio). | Ratio check flags entry (> 1 KB); throws `ArchiveSecurityException` (`ZIP_BOMB_DETECTED`). | **PASSED** |

---

### Category 2: Dataset Hashing & Snapshot Consistency (`DatasetSecurityTest.java` — 14 Tests)

| # | Test Name | Attack Setup & Technique | Expected Security Behavior | Actual Result |
| :- | :--- | :--- | :--- | :--- |
| 1 | `testAllRegularFilesContributeToHash` | Created dataset with diverse filenames, subdirectories, and image data. | Directory hash computes deterministically from all files. | **PASSED** |
| 2 | `testTemporaryFilesContributeToHash` | Added `cache.tmp` to dataset directory. | Hash changes when `.tmp` file is added (proves NO `.tmp` exclusion). | **PASSED** |
| 3 | `testMacMetadataFilesContributeToHash` | Added `.DS_Store` and `._image.jpg` files. | Hash changes when macOS metadata files are added (proves NO exclusion). | **PASSED** |
| 4 | `testAddingFileChangesHash` | Computed baseline hash; added a new file `extra.txt`. | Manifest hash differs from baseline. | **PASSED** |
| 5 | `testDeletingFileChangesHash` | Computed baseline hash; deleted an existing file. | Manifest hash differs from baseline. | **PASSED** |
| 6 | `testRenamingFileChangesHash` | Computed baseline hash; renamed a file without changing its contents. | Manifest hash differs from baseline. | **PASSED** |
| 7 | `testHashCalculationIsDeterministic` | Repeated hashing 10 times across different directory traversal orders. | All 10 hashes are strictly identical. | **PASSED** |
| 8 | `testHashIsIndependentOfFilesystemTraversalOrder` | Scuffled directory entry arrival order during tree walk. | Ordinal sort of binary manifest produces identical hash. | **PASSED** |
| 9 | `testSymlinkInDatasetRejected` | Created symlink `symlink.jpg` -> `real.jpg` inside dataset directory. | Hashing throws `IOException` (`Symlink rejected`); fails closed. | **PASSED** |
| 10 | `testSpecialFileInDatasetRejected` | Attempted hashing with FIFO or non-regular file. | Non-regular file detected; hashing throws `IOException`. | **PASSED** |
| 11 | `testConcurrentFileModificationDuringHashingDetected` | Modified file content between pre-read and post-read metadata checks via interceptor. | Metadata mismatch detected; throws `IOException` (`Concurrent modification`). | **PASSED** |
| 12 | `testConcurrentDirectoryMutationDetected` | Added new file to directory tree while hashing was in progress via interceptor. | Snapshot tree verification fails; throws `IOException` (`Concurrent mutation`). | **PASSED** |
| 13 | `testHashingFailureFailsClosedInAudit` | Triggered hashing failure during dataset audit execution. | Audit decision is `REVIEW`, status is `FAILED`; cannot produce `ACCEPT`. | **PASSED** |
| 14 | `testPhysicalFileModificationProducesReview` | Changed file on disk without updating DB record; requested risk assessment. | Freshness check recalculates manifest; status invalidated to `NOT_EXECUTED`; produces `REVIEW`. | **PASSED** |

---

### Category 3: Provenance Chain Integrity & Anti-Tampering (`ProvenanceIntegritySecurityTest.java` — 14 Tests)

| # | Test Name | Attack Setup & Technique | Expected Security Behavior | Actual Result |
| :- | :--- | :--- | :--- | :--- |
| 1 | `testUnchangedValidCompleteChainPasses` | Verified pristine 3-record cryptographically linked genesis chain. | Full chain validation succeeds; check status is `PASSED`. | **PASSED** |
| 2 | `testEarlierRecordPayloadModificationDetected` | Tampered with `model_identifier` of record 1 via direct SQLite JDBC `UPDATE`. | Check status immediately becomes `FAILED`; rationale notes broken HMAC/hash. | **PASSED** |
| 3 | `testEarlierRecordHmacModificationDetected` | Tampered with `hmac_value` of record 1 via direct SQLite JDBC `UPDATE`. | Signature validation fails; check status becomes `FAILED`. | **PASSED** |
| 4 | `testPreviousRecordHashModificationDetected` | Tampered with `previous_record_hash` of record 2 via direct SQLite JDBC `UPDATE`. | Chain link break detected; check status becomes `FAILED`. | **PASSED** |
| 5 | `testSequenceNumberTamperingDetected` | Tampered with `sequence_number` of record 2 (1 -> 5) via direct SQLite `UPDATE`. | Monotonicity violation detected; check status becomes `FAILED`. | **PASSED** |
| 6 | `testRecordDeletionDetected` | Deleted middle record (`rec_002`) via direct SQLite `DELETE`. | Broken link (`prevHash != currHash`) detected; check status becomes `FAILED`. | **PASSED** |
| 7 | `testRecordInsertionDetected` | Inserted unauthorized record between record 1 and record 2 via SQLite `INSERT`. | Link mismatch detected; check status becomes `FAILED`. | **PASSED** |
| 8 | `testDuplicateRecordIdDetected` | Inserted record with duplicate `record_id` via SQLite `INSERT`. | Duplicate record ID detected; check status becomes `FAILED`. | **PASSED** |
| 9 | `testEmptyProvenanceChainFailsClosed` | Deleted all records from `inference_records` table. | Empty chain detected; check status returns `NOT_EXECUTED` (never `PASSED`). | **PASSED** |
| 10 | `testMissingKeyFailsClosed` | Provided null key manager or non-existent key to `InferenceValidator`. | Signing key missing; chain verification throws exception; fails closed to `UNAVAILABLE`. | **PASSED** |
| 11 | `testDatabaseFailureFailsClosed` | Closed underlying database connection or simulated query exception. | Database exception caught; check status fails closed to `UNAVAILABLE`. | **PASSED** |
| 12 | `testThreadReuseStaleCachePrevention` | Verified provenance on worker thread; tampered with record 1 in DB; reverified on same thread. | No ThreadLocal cache; second call detects tampering and returns `FAILED`. | **PASSED** |
| 13 | `testTamperingBetweenRequestsDetectedImmediately` | Request 1 verified valid chain (`PASSED`); DB tampered; Request 2 executed. | Request 2 immediately reflects `FAILED` on dashboard without manual reset. | **PASSED** |
| 14 | `testDashboardAndReportConsistency` | Evaluated risk via `assessSystemRisk()` and generated compliance report via `ReportService`. | Both dashboard and generated report report strictly identical decisions and rationale. | **PASSED** |

---

### Category 4: Fail-Closed Dependency Handling (`FailClosedDependencyTest.java` — 14 Tests)

| # | Test Name | Attack Setup & Technique | Expected Security Behavior | Actual Result |
| :- | :--- | :--- | :--- | :--- |
| 1 | `testNullModelRepositoryFailsClosed` | Instantiated `RiskAssessmentService` with null `MlModelRepository`. | Check status returns `UNAVAILABLE`; overall decision cannot be `ACCEPT`. | **PASSED** |
| 2 | `testNullDatasetRepositoryFailsClosed` | Instantiated `RiskAssessmentService` with null `DatasetRepository`. | Check status returns `UNAVAILABLE`; overall decision cannot be `ACCEPT`. | **PASSED** |
| 3 | `testNullInferenceRepositoryFailsClosed` | Instantiated `RiskAssessmentService` with null `InferenceRecordRepository`. | Check status returns `UNAVAILABLE`; overall decision cannot be `ACCEPT`. | **PASSED** |
| 4 | `testNullProvenanceServiceFailsClosed` | Instantiated `RiskAssessmentService` with null `InferenceProvenanceService`. | Check status returns `UNAVAILABLE`; overall decision cannot be `ACCEPT`. | **PASSED** |
| 5 | `testNullAuditRepositoriesFailClosed` | Instantiated service with null model/dataset audit repositories. | Checks fail closed to `UNAVAILABLE`. | **PASSED** |
| 6 | `testNullKeyManagerFailsClosed` | Instantiated provenance service with null `ProvenanceKeyManager`. | Validation fails closed to `UNAVAILABLE`. | **PASSED** |
| 7 | `testMissingKeyFileFailsClosed` | Pointed key path to non-existent file without generate permission. | Service fails closed to `UNAVAILABLE`. | **PASSED** |
| 8 | `testUnreadableModelFileFailsClosed` | Pointed model record to missing or unreadable path on disk. | Freshness verification returns `UNAVAILABLE` or `FAILED`; never `ACCEPT`. | **PASSED** |
| 9 | `testUnreadableDatasetDirectoryFailsClosed` | Pointed dataset record to missing directory on disk. | Freshness verification returns `UNAVAILABLE` or `FAILED`; never `ACCEPT`. | **PASSED** |
| 10 | `testUnknownCheckIdentifierFailsClosed` | Called `getCheckStatus("unknown.check.id")`. | Returns `CheckExecutionStatus.UNAVAILABLE`; never `PASSED`. | **PASSED** |
| 11 | `testFailedCheckForcesReviewOrQuarantine` | One mandatory check `FAILED`, others `PASSED`. | Overall decision is `REVIEW` (or `QUARANTINE` if critical finding); never `ACCEPT`. | **PASSED** |
| 12 | `testUnavailableCheckPreventsAccept` | One mandatory check `UNAVAILABLE`, others `PASSED`. | Overall decision is `REVIEW`; cannot be `ACCEPT`. | **PASSED** |
| 13 | `testNotExecutedCheckPreventsAccept` | One mandatory check `NOT_EXECUTED`, others `PASSED`. | Overall decision is `REVIEW`; cannot be `ACCEPT`. | **PASSED** |
| 14 | `testPrecedenceRuleAcceptRequiresAllPassed` | Configured all checks `PASSED` and zero unmitigated findings. | Overall decision is `ACCEPT`. | **PASSED** |

---

## 2. Complete Test Suite Breakdown (186 Tests Total)

| Test Class | Package | Tests Run | Failures | Errors | Skipped | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `ArchiveSecurityTest` | `com.sentinelcv.util` | 20 | 0 | 0 | 0 | **PASSED** |
| `DatasetSecurityTest` | `com.sentinelcv.util` | 14 | 0 | 0 | 0 | **PASSED** |
| `ProvenanceIntegritySecurityTest` | `com.sentinelcv.service` | 14 | 0 | 0 | 0 | **PASSED** |
| `FailClosedDependencyTest` | `com.sentinelcv.service` | 14 | 0 | 0 | 0 | **PASSED** |
| `ProvenanceKeyManagerTest` | `com.sentinelcv.service` | 15 | 0 | 0 | 0 | **PASSED** |
| `StaleCheckStatusRegressionTest` | `com.sentinelcv.service` | 11 | 0 | 0 | 0 | **PASSED** |
| `InferenceValidatorTest` | `com.sentinelcv.inference` | 22 | 0 | 0 | 0 | **PASSED** |
| `InferenceProvenanceServiceTest` | `com.sentinelcv.service` | 13 | 0 | 0 | 0 | **PASSED** |
| `RiskAssessmentServiceTest` | `com.sentinelcv.service` | 10 | 0 | 0 | 0 | **PASSED** |
| `RealOnnxModelAuditTest` | `com.sentinelcv.model` | 10 | 0 | 0 | 0 | **PASSED** |
| `DatasetIntegrityRegressionTest` | `com.sentinelcv.dataset` | 8 | 0 | 0 | 0 | **PASSED** |
| `FindingLifecycleRegressionTest` | `com.sentinelcv.service` | 6 | 0 | 0 | 0 | **PASSED** |
| `CocoDatasetAuditTest` | `com.sentinelcv.dataset` | 5 | 0 | 0 | 0 | **PASSED** |
| `YoloDatasetAuditTest` | `com.sentinelcv.dataset` | 5 | 0 | 0 | 0 | **PASSED** |
| `ReportGenerationRegressionTest` | `com.sentinelcv.service` | 5 | 0 | 0 | 0 | **PASSED** |
| `DatasetHashTamperingRegressionTest` | `com.sentinelcv.service` | 4 | 0 | 0 | 0 | **PASSED** |
| `AsyncSaturationRegressionTest` | `com.sentinelcv.service` | 4 | 0 | 0 | 0 | **PASSED** |
| `DriftDetectionServiceTest` | `com.sentinelcv.service` | 4 | 0 | 0 | 0 | **PASSED** |
| `SystemWideAuditRegressionTest` | `com.sentinelcv.service` | 4 | 0 | 0 | 0 | **PASSED** |
| `AsyncAuditExecutionTest` | `com.sentinelcv.service` | 2 | 0 | 0 | 0 | **PASSED** |
| `DatabaseConfigTest` | `com.sentinelcv.config` | 1 | 0 | 0 | 0 | **PASSED** |
| **TOTAL** | | **186** | **0** | **0** | **0** | **PASSED** |
