# SentinelCV: System Architecture Document

## 1. Architectural Overview
**SentinelCV** is an offline, multi-tier security and integrity assessment system specifically designed for computer vision pipelines. It operates on three fundamental pillars of computer vision systems:
1. **Datasets** (COCO and YOLO formats)
2. **Models** (ONNX and PyTorch/TorchScript representations)
3. **Inference Provenance** (Cryptographic hash chains, digital signatures, and replay detection)
4. **Distribution Shift** (Statistical covariate drift and concept shift detection)
5. **Centralized Assessment & Reporting** (Deterministic multi-factor risk engine)

```
+-------------------------------------------------------------------------+
|                         SentinelCV Dashboard (Web UI)                    |
|             (Bootstrap 5, Thymeleaf, JavaScript, Responsive UI)          |
+------------------------------------+------------------------------------+
                                     | HTTP Requests / Forms / APIs
+------------------------------------+------------------------------------+
|                         Spring Boot Controller Layer                    |
|   Dashboard | Dataset | Model | Inference | Drift | Findings | Reports  |
+------------------------------------+------------------------------------+
                                     |
+------------------------------------+------------------------------------+
|                         Service Orchestration Layer                     |
|  DatasetAuditService | ModelAuditService | InferenceProvenanceService   |
|      DriftDetectionService | RiskAssessmentService | ReportService      |
+----+-------------------+-------------------+--------------------+-------+
     |                   |                   |                    |
+----+----+         +----+----+         +----+----+          +----+----+
| Dataset |         |  Model  |         |Inference|          |  Drift  |
| Engine  |         | Engine  |         | Proven. |          | Engine  |
+----+----+         +----+----+         +----+----+          +----+----+
     |                   |                   |                    |
     | COCO/YOLO         | ONNX/TorchScript  | Merkle Chain /     | PSI / KS
     | ImageIO / Hashes  | Metadata / Tensors| Nonce Verification | Metrics
     |                   |                   |                    |
+----+-------------------+-------------------+--------------------+-------+
|                    Persistence Layer (Spring JDBC)                      |
|                  SQLite Database (WAL Mode, Indexes)                    |
+-------------------------------------------------------------------------+
```

---

## 2. Design Patterns Implemented

### 2.1 Strategy Pattern
- **Dataset Parsing**: `DatasetParser` interface with concrete implementations `CocoParser` and `YoloParser`. At runtime, SentinelCV inspects the dataset layout and delegates parsing to the matching strategy.
- **Model Loading**: `ModelLoader` interface with concrete implementations `OnnxModelLoader` and `TorchScriptModelLoader`.

### 2.2 Builder Pattern
- Implemented in `SecurityFinding.Builder` to create immutable, strongly typed, and validated security findings with consistent metadata across modules.

### 2.3 Repository Pattern
- Decouples business logic from persistence via Spring `JdbcTemplate` data access objects (`DatasetRepository`, `ModelRepository`, `InferenceRecordRepository`, etc.).

### 2.4 Dependency Injection
- Spring Inversion of Control (IoC) container manages component lifecycles, thread pools, and cross-cutting concerns.

---

## 3. Core Subsystems

### 3.1 Dataset Integrity Engine
- **Duplicate Detection**: Computes 64-bit difference hashes (`dHash`) and standard SHA-256 hashes to group exact duplicates and near-duplicates within configurable Hamming distance thresholds.
- **Annotation Validation**: Mathematically verifies bounding box coordinates ($[x, y, w, h]$), checks for zero/negative areas, out-of-bounds boundaries, and detects orphaned or undefined class IDs.
- **Contributor Risk**: Analyzes author concentration where metadata exists; otherwise reports an explicit `UNAVAILABLE` finding.

### 3.2 Model Behavioral & Security Engine
- **Static Identity**: SHA-256 fingerprinting and structural tensor signature hashing.
- **Dynamic Inspection**: ONNX Runtime integration for inspecting input/output shapes, tensor data types, and deterministic execution repeatability.
- **Perturbation Testing**: Heuristic black-box perturbation analysis applying localized watermark patches and global jitter to evaluate prediction sensitivity and label flips.

### 3.3 Cryptographic Provenance Engine
- **Hash-Linked History**: Each inference record references the previous record's canonical SHA-256 hash, forming an immutable sequence.
- **Replay Protection**: Verifies nonce uniqueness and strict sequence number monotonicity.
- **Integrity Authentication**: Supports HMAC-SHA256 and RSA signatures.

### 3.4 Distribution Shift Detection
- **Statistical Tests**: Computes Population Stability Index (PSI), two-sample Kolmogorov-Smirnov statistic ($D$), and 1D Wasserstein distance on visual metrics (brightness, contrast, intensity, RGB channels).

### 3.5 Centralized Assessment & Reporting
- Aggregates findings into weighted risk scores with transparent rules for `ACCEPT`, `REVIEW`, and `QUARANTINE` outcomes.
- Multi-format report generator produces JSON, standalone HTML, and OpenPDF documents.
