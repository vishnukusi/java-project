# SentinelCV

**Offline Computer Vision Security and Integrity Assessment System**

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![SQLite](https://img.shields.io/badge/SQLite-3.45-blue.svg)](https://www.sqlite.org/)
[![License](https://img.shields.io/badge/License-Academic-lightgrey.svg)]()
[![Build](https://img.shields.io/badge/Build-Passing%20(96%2F96%20Tests)-success.svg)]()

---

## 1. Project Overview
Modern computer vision pipelines ingest datasets from disparate open-source repositories, evaluate pre-trained black-box models, and exchange inference outputs across untrusted network boundaries. Traditional validation approaches focus almost exclusively on accuracy metrics (mAP, F1-score) rather than pipeline integrity, supply-chain provenance, or adversarial perturbations.

**SentinelCV** is a standalone, offline Java-based security assessment platform designed to independently audit:
1. **Datasets**: Integrity validation, SHA-256 and perceptual near-duplicate detection, bounding box boundary compliance, and contributor risk.
2. **Machine Learning Models**: Cryptographic identity fingerprinting, tensor metadata inspection, deterministic repeatability verification, and black-box localized trigger perturbation assessment.
3. **Inference Provenance**: Blockchain-style hash-linked audit records, replay detection via nonces and monotonic sequences, and length-prefixed HMAC-SHA256 signature verification.
4. **Distribution Shift**: Quantitative statistical comparison (Population Stability Index - PSI, Kolmogorov-Smirnov statistic, Wasserstein distance) across visual features.
5. **Consolidated Risk Assessment**: Multi-factor decision engine issuing explainable `ACCEPT`, `REVIEW`, or `QUARANTINE` verdicts with downloadable JSON, HTML, and PDF reports.

The system is strictly offline, requiring no external network access, cloud APIs, or model retraining.

> **Academic Prototype Notice**: SentinelCV is an offline academic prototype designed for security research and reproducible evaluation. It provides automated structural integrity checks, forensic logging, and explainable risk scores, but does not provide formal cryptographic attestation or proof of backdoor absence.

---

## 2. Technology Stack
- **Language**: Java 21 (LTS)
- **Framework**: Spring Boot 3.2.5 (Spring MVC, Thymeleaf, Spring Data JDBC)
- **Database**: SQLite 3 (WAL mode enabled, foreign keys enforced)
- **Image Processing**: Java ImageIO & AWT Graphics (Difference Hashing `dHash`, Average Hashing `aHash`, luminance histograms)
- **Machine Learning**: Microsoft ONNX Runtime Java API (1.17.1), Deep Java Library (DJL 0.27.0)
- **Cryptography**: Java Cryptography Architecture (JCA) — SHA-256, HMAC-SHA256, RSA-2048, SecureRandom
- **Document Generation**: OpenPDF 1.3.35, Jackson 2.16
- **Frontend**: Bootstrap 5, Bootstrap Icons, Vanilla CSS design tokens

---

## 3. Quick Start & Execution

### Prerequisites
- macOS, Linux, or Windows with Bash/WSL.
- The project includes a bundled Amazon Corretto 21 JDK and Apache Maven 3.9.6 in `.tools/`.

### Running Locally
To launch the system using the bundled runtime:
```bash
./run.sh
```
Or using the local Maven wrapper directly:
```bash
./mvnw_local spring-boot:run
```

Once running, access the dashboard at:
👉 **`http://localhost:8080`**

### Running Tests
Execute the complete test suite (96 passing tests):
```bash
# Run all tests
./mvnw_local test

# Clean build and test
./mvnw_local clean test

# Build and verify executable JAR
./mvnw_local clean test verify
```

---

## 4. Key Functional Modules & Repaired Security Properties

### Module 1: Dataset Integrity & Archive Extraction Security (`ArchiveUtil.java`)
- **Supported Formats**: Microsoft COCO JSON format and YOLO text annotation format.
- **Defensive Extraction Limits**:
  - Maximum archive entries: **10,000**.
  - Maximum total uncompressed extraction size: **500 MB**.
  - Maximum individual file size: **100 MB**.
  - Maximum compression ratio: **100:1** (accurate byte accounting, zero-division safe).
- **Symlink Protection**:
  - Rejects symbolic link destination directories and parent symlinks.
  - Rejects ZIP archive symbolic link entries using Apache Commons Compress `ZipFile` Unix external attributes (`isUnixSymlink()` or `(mode & 0170000) == 0120000`).
- **Path Traversal & Sanitization**:
  - Strictly prevents Zip-Slip path traversal (`../../`) and absolute paths (`/etc/shadow`).
  - Filters embedded null bytes (`\0`).
  - Verifies standard ZIP magic bytes (`0x50, 0x4B, 0x03, 0x04`).
- **Selective Atomic Rollback**:
  - Snapshots pre-existing files prior to extraction. On failure, removes only files/directories created during the failed extraction, preserving unrelated pre-existing data.

### Module 2: Model Integrity, Behavioral Auditing & Real Inference (`ModelAuditService.java`)
- **Genuine ONNX Runtime Execution**: Features a built-in pure Java Protocol Buffer model generator (`FixtureGenerator`) that outputs fully compliant ONNX vision models (`[1, 3, 224, 224]` with `Relu`).
- **Behavioral Fingerprinting**: Runs structured baseline tensor inputs and asserts deterministic repeatability across 3 consecutive inference passes.
- **Spatial Patch Perturbation Testing**: Evaluates localized multi-channel spatial patch perturbations and global Gaussian jitter against prediction output stability.
- **Mandatory Trigger Perturbation Check for ACCEPT**:
  - The final audit decision mandates that baseline inference (`behavioralResult.inferenceExecuted`) AND trigger perturbation analysis (`triggerReport.executed`) must both complete successfully.
  - If trigger analysis is skipped, unexecuted, or fails, the audit decision is mandated to `REVIEW`, never falsely producing `ACCEPT`.

### Module 3: Inference Provenance Verification & Key Management (`InferenceValidator.java`, `ProvenanceKeyManager.java`, `InferenceProvenanceService.java`)
- **Length-Prefixed Deterministic Canonical Serialization (Schema V2)**:
  - Format: `V2|<len>:<recordId>;<len>:<sequenceNumber>;<len>:<timestamp>;<len>:<modelIdentifier>;<len>:<modelHash>;<len>:<inputImageHash>;<len>:<configHash>;<len>:<outputHash>;<len>:<nonce>;<len>:<previousRecordHash>`
  - Encodes all 10 security-relevant immutable record fields.
  - Length-prefixed encoding prevents delimiter collision and field ambiguity attacks.
  - Identical canonical payload bytes are used for SHA-256 record hashing and HMAC generation/verification.
- **Hardened Local Key Management**:
  - Keys generated using `SecureRandom` (256-bit).
  - Key file created atomically with `0600` POSIX permissions (`rw-------`).
  - Secret keys are never output to logs or exception messages.
  - Corrupt or low-entropy key files (< 32 chars) throw explicit `IllegalStateException`, halting execution and requiring operator intervention rather than silently generating new inconsistent keys.
- **Strict Batch Decision Precedence**:
  1. Any broken hash chain → `QUARANTINE`.
  2. Any tampered authenticated payload / invalid hash → `QUARANTINE`.
  3. Replay detection without cryptographic integrity compromise → `REVIEW`.
  4. Missing key or unavailable cryptographic verification → `REVIEW`.
  5. `VERIFIED` only if all records pass all checks.
  - Decision is deterministic and independent of record processing order.

### Module 4: Distribution Shift Detection & Statistical Safeguards (`DistributionShiftService.java`)
- **Visual Feature Shift**: Calculates Population Stability Index (PSI) and Kolmogorov-Smirnov (KS) statistic across luminance, contrast, mean intensity, and RGB color channels.
- **Numerical Stability**: Sanitizes inputs with `Double.isFinite()` to eliminate `NaN` or `Infinity` distortions.
- **Sample Size Safeguards**: If sample size $N < 5$, statistical confidence is deemed insufficient and marked `UNAVAILABLE`, mandating `REVIEW`.

### Module 5: Centralized Security Assessment (`RiskAssessmentService.java`)
- **Explicit Structured Check Execution Status**:
  - Status enum: `CheckExecutionStatus` (`PASSED`, `FAILED`, `UNAVAILABLE`, `NOT_EXECUTED`).
  - Availability is not inferred solely from INFO severity or description text.
- **Strict Decision Precedence**:
  1. Any critical integrity finding → `QUARANTINE`.
  2. Otherwise, any high-risk finding → `REVIEW`.
  3. Otherwise, any required check `UNAVAILABLE` or `NOT_EXECUTED` → `REVIEW`.
  4. Otherwise, `ACCEPT` only when all mandatory checks `PASSED`.
- **Positive numerical risk scores cannot override an unavailable check.**
- **Missing check statuses are never silently treated as `PASSED`.**

---

## 5. Verification Commands

To verify all components and test suites:
```bash
# Run complete test suite (96 tests)
./mvnw_local test

# Run archive security test suite (12 tests)
./mvnw_local test -Dtest=ArchiveSecurityTest

# Run provenance canonical payload test suite (18 tests)
./mvnw_local test -Dtest=InferenceValidatorTest

# Run hardened key management test suite (8 tests)
./mvnw_local test -Dtest=ProvenanceKeyManagerTest

# Run model audit decision test suite (10 tests)
./mvnw_local test -Dtest=RealOnnxModelAuditTest

# Run global risk assessment test suite (10 tests)
./mvnw_local test -Dtest=RiskAssessmentServiceTest

# Run provenance batch precedence test suite (6 tests)
./mvnw_local test -Dtest=InferenceProvenanceServiceTest
```
