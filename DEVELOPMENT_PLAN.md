# SentinelCV: Development Plan

## Project Overview
**SentinelCV** is an offline Computer Vision Security and Integrity Assessment System developed in Java 21 with Spring Boot, SQLite, Java ImageIO, ONNX Runtime, DJL, and standard Java Cryptography Architecture (JCA).

---

## Phase Breakdown

### Phase 0: Project Inspection and Architecture [IN PROGRESS]
- [x] Workspace inspection and environment verification
- [x] Initial directory structure creation
- [x] Maven POM configuration (`pom.xml`) with Java 21, Spring Boot 3.2.5, SQLite, ONNX Runtime, DJL
- [x] Core Enums, Custom Exceptions, Global Exception Handler
- [x] Database Schema (`schema.sql`) and `DatabaseConfig` (WAL mode, SQLite DataSource)
- [x] Application Config (`ApplicationConfig`, `SecurityConfig`)
- [x] `DEVELOPMENT_PLAN.md` and `PROJECT_STATUS.md`

### Phase 1: Application Foundation & Data Access Layer
- [ ] Implement JDBC Repositories for all 10 domain entities:
  - `DatasetRepository`, `DatasetAuditRepository`, `DatasetFindingRepository`
  - `MlModelRepository`, `ModelAuditRepository`, `ModelFindingRepository`
  - `InferenceRecordRepository`, `ProvenanceVerificationRepository`
  - `DriftAuditRepository`, `SecurityFindingRepository`, `AuditReportRepository`, `AuditHistoryRepository`
- [ ] Implement Security, Crypto, and Hashing Utilities:
  - `HashUtil` (SHA-256 for streams, files, byte arrays)
  - `CryptoUtil` (HMAC, Digital Signatures, Secure Random Nonce)
  - `ArchiveUtil` (ZipSlip-safe archive extraction, path traversal protection)
- [ ] Basic Controller & Dashboard Endpoints
- [ ] Unit & Integration Tests for Foundation

### Phase 2: Dataset Ingestion & Parsing
- [ ] `DatasetParser` interface and Strategy pattern
- [ ] `CocoParser` (JSON parsing, annotation format validation, image reference resolution)
- [ ] `YoloParser` (Images & labels directories, normalized coordinates, class mapping)
- [ ] Dataset Upload Service with file-type checking and safe extraction
- [ ] Unit tests for valid/invalid COCO & YOLO datasets

### Phase 3: Dataset Integrity Engine
- [ ] `ImageIntegrityAnalyzer` (Corrupt image detection, dimension consistency)
- [ ] `DuplicateDetector` (Exact SHA-256 match + Perceptual Hashing dHash/pHash)
- [ ] `AnnotationValidator` (Zero-area, out-of-bounds bounding boxes, invalid class IDs)
- [ ] `DatasetStatisticsService` (Class distribution, imbalance, metrics, integrity score)
- [ ] Contributor risk analysis (where metadata exists; explicit UNAVAILABLE otherwise)
- [ ] `DatasetAuditService` orchestration and asynchronous execution
- [ ] Unit tests with synthetic image test fixtures

### Phase 4: Cryptographic Provenance Engine
- [ ] `InferenceRecord` verification (Input, model, config, output hash checks)
- [ ] Hash-linked audit blockchain / Merkle link verification
- [ ] Replay detection (Nonce uniqueness, sequence monotonically increasing, timestamp validation)
- [ ] HMAC and Signature verification via JCA
- [ ] `InferenceAuditService` and report generation
- [ ] Unit tests for tampering, replay, and broken hash chain scenarios

### Phase 5: Model Inspection and Behavioral Auditing
- [ ] `ModelLoader` interface and implementations:
  - `OnnxModelLoader` (ONNX Runtime Java API, metadata, input/output tensors)
  - `TorchScriptModelLoader` (DJL engine or explicit UNAVAILABLE fallback)
- [ ] `ModelFingerprintService` (SHA-256 identity, file size, tensor signatures)
- [ ] `BehavioralAnalyzer` (Controlled baseline inference, deterministic output validation)
- [ ] `TriggerAnalyzer` (Black-box localized perturbation testing, prediction variance)
- [ ] Model Audit Orchestrator and finding generator
- [ ] Unit tests with ONNX model fixture & fallback tests

### Phase 6: Distribution Shift & Anomaly Detection
- [ ] `ImageStatisticsExtractor` (Pixel intensity, brightness, contrast, RGB distributions)
- [ ] `DistributionComparator` (Population Stability Index - PSI, Kolmogorov-Smirnov statistic)
- [ ] Class distribution shift comparison
- [ ] Drift severity classification and thresholds
- [ ] Unit tests for drift scenarios

### Phase 7: Centralized Security Assessment
- [ ] Finding aggregation across all modules
- [ ] Severity weighting and Risk Score calculation
- [ ] Transparent Decision Engine: ACCEPT, REVIEW, QUARANTINE
- [ ] Actionable remediation recommendations
- [ ] Audit history logging

### Phase 8: Web Dashboard & Frontend Integration
- [ ] Professional enterprise UI with Bootstrap 5, modern typography & dark/light theme
- [ ] Dashboard view (Key metrics, charts, recent audits)
- [ ] Dataset Management & Audit Details view
- [ ] Model Management & Audit Details view
- [ ] Inference Provenance Verification view
- [ ] Distribution Shift Analysis view
- [ ] Security Findings & Severity view
- [ ] Audit History & Settings views

### Phase 9: Reporting & Final Integration
- [ ] JSON report export
- [ ] PDF report generation via OpenPDF
- [ ] End-to-end integration workflows
- [ ] Fixture datasets, sample ONNX models, and synthetic inference logs

### Phase 10: Final QA, Verification & Handover Documentation
- [ ] Full test suite execution (`mvn test`, `mvn verify`)
- [ ] Fix any edge cases and warnings
- [ ] Comprehensive `README.md`, `PROJECT_REPORT.md` (academic project report with UML and viva Q&A)
- [ ] `ARCHITECTURE.md`, `API_DOCUMENTATION.md`, `TESTING_REPORT.md`, `JAVA_CONCEPT_MAPPING.md`
