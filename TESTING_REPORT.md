# SentinelCV: Comprehensive Testing Report

## 1. Executive Test Summary
- **Test Framework**: JUnit 5 + Spring Boot Test + Mockito (`mock-maker-subclass`) + Java ImageIO Synthetic Assertions
- **Total Tests Run**: **96**
- **Passed**: **96**
- **Failed**: **0**
- **Errors**: **0**
- **Skipped**: **0**
- **Build Status**: `BUILD SUCCESS`
- **Total Execution Time**: ~7.3 seconds (full `clean test verify`)
- **JVM**: Amazon Corretto 21.0.13 (Java 21 LTS) on macOS Apple Silicon

---

## 2. Test Execution Breakdown by Functional Area

### 2.1 Archive & File System Security (`com.sentinelcv.util.ArchiveSecurityTest`) — 12 Tests
| Method | Description | Outcome |
| :--- | :--- | :--- |
| `testValidZipExtraction` | Verifies extraction of standard compliant ZIP archives | **PASSED** |
| `testFakeZipMagicBytesRejection` | Blocks file upload pretending to be ZIP with invalid magic bytes | **PASSED** |
| `testZipSlipPathTraversalBlocked` | Enforces canonical path checks against `../../` Zip-Slip directory traversal | **PASSED** |
| `testAbsolutePathTraversalBlocked` | Enforces rejection of absolute archive paths (e.g., `/etc/shadow`) | **PASSED** |
| `testSymlinkDestinationDirectoryBlocked` | Enforces rejection when target destination directory is a symbolic link | **PASSED** |
| `testSymlinkArchiveEntryBlocked` | Enforces rejection of ZIP entries flagged as symlinks via Unix external attributes | **PASSED** |
| `testNullByteFilenameBlocked` | Rejects malicious archive entries containing embedded null bytes (`\0`) | **PASSED** |
| `testZipBombEntryLimitEnforced` | Enforces maximum 10,000 archive entries limit and rolls back | **PASSED** |
| `testZipBombOversizedSingleFileBlocked` | Enforces maximum 100 MB single file extraction limit | **PASSED** |
| `testZipBombOversizedTotalExtractionBlocked` | Enforces maximum 500 MB total extraction limit | **PASSED** |
| `testZipBombCompressionRatioViolationBlocked` | Enforces maximum 100:1 compression ratio limit (deflate bomb detection) | **PASSED** |
| `testAtomicRollbackPreservesPreExistingFiles` | Verifies atomic rollback deletes failed extraction files without deleting pre-existing files | **PASSED** |

### 2.2 Provenance Canonical Payload & Verification (`com.sentinelcv.inference.InferenceValidatorTest`) — 18 Tests
| Method | Description | Outcome |
| :--- | :--- | :--- |
| `testValidChainVerification` | Verifies sequential cryptographic chaining across 3 records using V2 schema | **PASSED** |
| `testDetectBrokenHashChain` | Detects corrupted previous record hashes | **PASSED** |
| `testDetectReplayDuplicateNonce` | Injects duplicate nonce and verifies replay attack detection | **PASSED** |
| `testInvalidInputHashFailsOverallVerification` | Injects corrupted input image hash and asserts overall failure | **PASSED** |
| `testInvalidModelHashFailsOverallVerification` | Injects corrupted model weight hash and asserts overall failure | **PASSED** |
| `testInvalidConfigHashFailsOverallVerification` | Injects corrupted config hash and asserts overall failure | **PASSED** |
| `testInvalidOutputHashFailsOverallVerification` | Injects corrupted output logits hash and asserts overall failure | **PASSED** |
| `testMalformedTimestampFailsOverallVerification` | Injects invalid ISO timestamp and asserts parse failure | **PASSED** |
| `testMissingHmacProducesUnavailableRatherThanVerified` | Asserts missing HMAC produces `UNAVAILABLE` rather than `VERIFIED` | **PASSED** |
| `testNonGenesisPreviousHashOnFirstRecordFails` | Validates first record's previous hash against `GENESIS` | **PASSED** |
| `testNonSequentialStartingSequenceNumberFails` | Verifies sequence starts at exactly 1 | **PASSED** |
| `testSequenceGapFailsOverallVerification` | Injects sequence skip ($1 \to 3$) and asserts failure | **PASSED** |
| `testTamperedPayloadFailsHashValidation` | Injects payload tampering and verifies SHA-256 mismatch | **PASSED** |
| `testModifyingRecordIdInvalidatesVerification` | Modifying `recordId` changes canonical payload and fails verification | **PASSED** |
| `testModifyingModelIdentifierInvalidatesVerification` | Modifying `modelIdentifier` changes canonical payload and fails verification | **PASSED** |
| `testDelimiterCollisionImmunity` | Length-prefixed encoding prevents cross-field delimiter injection ambiguity | **PASSED** |
| `testHmacAndSha256UseIdenticalCanonicalBytes` | Verifies HMAC and SHA-256 process identical canonical payload bytes | **PASSED** |
| `testLegacySchemaRecordFailsVerification` | Verifies unauthenticated/legacy schema records fail verification | **PASSED** |

### 2.3 Hardened Key Management (`com.sentinelcv.provenance.ProvenanceKeyManagerTest`) — 8 Tests
| Method | Description | Outcome |
| :--- | :--- | :--- |
| `testMissingKeyReturnsEmpty` | Missing key store and properties returns `Optional.empty()` | **PASSED** |
| `testConfiguredPropertyPrecedence` | Explicit property key takes precedence over local file | **PASSED** |
| `testKeyGenerationCreates256BitKey` | Generates 256-bit (64 hex characters) secure key via `SecureRandom` | **PASSED** |
| `testExistingKeyReuseDoesNotOverwrite` | Calling key generation repeatedly reuses existing key without overwrite | **PASSED** |
| `testCorruptKeyThrowsExceptionAndRequiresOperatorAction` | Corrupt key (< 32 chars) throws `IllegalStateException` requiring operator action | **PASSED** |
| `testConcurrentInitialization` | 10 concurrent threads initialize safely without race conditions | **PASSED** |
| `testPosixPermissionsWhereSupported` | Key file permissions set to `0600` (`OWNER_READ | OWNER_WRITE` only) | **PASSED** |
| `testNoSecretLeakageInExceptions` | Exception messages and logs never leak secret key material | **PASSED** |

### 2.4 Model Audit Decision Logic (`com.sentinelcv.model.RealOnnxModelAuditTest`) — 10 Tests
| Method | Description | Outcome |
| :--- | :--- | :--- |
| `testRealOnnxLoadingAndMetadata` | Verifies ONNX Runtime loads valid model and extracts tensor metadata | **PASSED** |
| `testRealOnnxInferenceAndDeterminism` | Executes real inference; asserts 3x repeated runs are deterministic | **PASSED** |
| `testBehavioralAnalysisAndSpatialPatchPerturbation` | Executes multi-channel spatial patch perturbation on tensor grid | **PASSED** |
| `testUnsupportedTorchScriptModelProducesReview` | Verifies unsupported model runtimes mandate `REVIEW`, never `ACCEPT` | **PASSED** |
| `testBaselineInferenceSucceedsTriggerAnalysisFailsProducesReview` | Baseline inference succeeds, trigger analysis fails $\to$ `REVIEW` | **PASSED** |
| `testBehavioralAnalysisFailsProducesReview` | Behavioral baseline inference fails $\to$ `REVIEW` | **PASSED** |
| `testOnnxInferenceUnavailableProducesReview` | ONNX runtime inference unsupported $\to$ `REVIEW` | **PASSED** |
| `testCriticalFindingProducesQuarantine` | Critical security finding $\to$ `QUARANTINE` | **PASSED** |
| `testHighFindingProducesReview` | High severity finding $\to$ `REVIEW` | **PASSED** |
| `testAllMandatoryChecksExecuteSuccessfullyProducesAccept` | All mandatory checks succeed with clean findings $\to$ `ACCEPT` | **PASSED** |

### 2.5 Global Risk Assessment (`com.sentinelcv.service.RiskAssessmentServiceTest`) — 10 Tests
| Method | Description | Outcome |
| :--- | :--- | :--- |
| `testAcceptDecisionWhenNoIssues` | Verifies `ACCEPT` decision when all findings are clean | **PASSED** |
| `testQuarantineOnCriticalFinding` | Verifies `QUARANTINE` decision when $\ge 1$ CRITICAL finding exists | **PASSED** |
| `testReviewOnHighFinding` | Verifies `REVIEW` decision on HIGH severity findings | **PASSED** |
| `testUnavailableDistinction` | Verifies that unavailable checks trigger `REVIEW` instead of `ACCEPT` | **PASSED** |
| `testMediumFindingPlusUnavailableMandatoryCheckProducesReview` | MEDIUM finding + unavailable check $\to$ `REVIEW` (no numerical override) | **PASSED** |
| `testMediumFindingRepresentingUnavailableCheckProducesReview` | MEDIUM finding representing unavailable check $\to$ `REVIEW` | **PASSED** |
| `testInfoFindingPlusUnavailableMandatoryCheckProducesReview` | INFO finding + unavailable check $\to$ `REVIEW` | **PASSED** |
| `testCriticalFindingPlusUnavailableCheckProducesQuarantine` | Critical finding + unavailable check $\to$ `QUARANTINE` (critical precedence) | **PASSED** |
| `testAllMandatoryChecksPassedAndNoAdverseFindingsProducesAccept` | All mandatory checks PASSED and clean $\to$ `ACCEPT` | **PASSED** |
| `testMissingCheckStatusIsNotSilentlyTreatedAsPassed` | Missing or null check status is NOT treated as PASSED $\to$ `REVIEW` | **PASSED** |

### 2.6 Provenance Batch Decision Precedence (`com.sentinelcv.service.InferenceProvenanceServiceTest`) — 6 Tests
| Method | Description | Outcome |
| :--- | :--- | :--- |
| `testBrokenChainPlusReplayProducesQuarantine` | Broken chain + replay indicator $\to$ `QUARANTINE` | **PASSED** |
| `testTamperedPayloadPlusReplayProducesQuarantine` | Tampered payload + replay indicator $\to$ `QUARANTINE` | **PASSED** |
| `testReplayAloneProducesReview` | Replay alone without cryptographic compromise $\to$ `REVIEW` | **PASSED** |
| `testMissingKeyProducesReview` | Missing verification key $\to$ `REVIEW` | **PASSED** |
| `testEntireValidChainProducesVerified` | Entire valid chain passes all checks $\to$ `VERIFIED` | **PASSED** |
| `testDecisionIsIndependentOfRecordOrder` | Reversing input record order yields identical `QUARANTINE` decision | **PASSED** |

### 2.7 Cryptographic, Statistical, Dataset, Drift & Integration Tests — 32 Tests
- **`HashUtilTest`** (3 tests): String SHA-256, buffered file SHA-256, null/empty edge cases.
- **`CryptoUtilTest`** (3 tests): HMAC compute/verify, RSA-2048 signing/verification, nonce generation.
- **`OnnxModelGeneratorTest`** (1 test): Pure Java Protocol Buffer generator creates valid ONNX model.
- **`StatisticalUtilTest`** (3 tests): PSI on identical distributions, PSI on shifted distributions, KS statistic.
- **`DriftStatisticalEdgeCasesTest`** (7 tests): Zero drift on identical sets, brightness shift, categorical class shift, small sample ($N<5$) unavailable, empty sets, NaN/Infinity filtering, constant-value distributions.
- **`ImageHashUtilTest`** (1 test): Perceptual dHash computation, Hamming distance, similarity.
- **`CocoParserTest`** (1 test): Microsoft COCO JSON format parsing, categories, image metadata.
- **`YoloParserTest`** (1 test): YOLO format parsing, `data.yaml` classes, coordinates.
- **`DuplicateDetectorTest`** (1 test): Exact SHA-256 duplicate image file clustering.
- **`AnnotationValidatorTest`** (1 test): Zero-area bounding boxes, negative coordinates, out-of-bounds detection.
- **`AsyncAuditExecutionTest`** (2 tests): Dataset and model audit non-blocking execution on `AuditWorker-*` threads.
- **Integration Tests** (8 tests):
  - `DatasetAuditIntegrationTest`: End-to-end dataset audit and SQLite persistence.
  - `ModelAuditIntegrationTest`: TorchScript static check and honest UNAVAILABLE finding.
  - `InferenceProvenanceIntegrationTest`: Database batch verification of seeded inference records.
  - `DriftDetectionIntegrationTest`: End-to-end distribution shift analysis between datasets.
  - `ReportServiceIntegrationTest` (3 tests): JSON, interactive HTML, and OpenPDF report compilation.
  - `SentinelCvApplicationTests`: Spring Boot application context bootstrap and WAL mode verification.

---

## 3. How to Execute Tests
```bash
# Execute entire test suite (96 tests)
./mvnw_local test

# Execute single test class
./mvnw_local test -Dtest=ArchiveSecurityTest

# Clean and run all tests
./mvnw_local clean test

# Package and verify executable JAR
./mvnw_local clean test verify
```
