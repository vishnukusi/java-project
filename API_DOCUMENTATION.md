# SentinelCV: REST & Endpoint Documentation

## 1. Overview
SentinelCV provides web dashboard and REST-compatible endpoints for automating pipeline security audits. All endpoints run locally without external authentication or cloud dependencies.

---

## 2. Dataset Endpoints

### 2.1 List Datasets
- **Endpoint**: `GET /datasets`
- **Description**: Returns all registered datasets and their summary metrics.
- **Response**: HTML View (`dataset/list`)

### 2.2 Upload Dataset Archive
- **Endpoint**: `POST /datasets/upload`
- **Content-Type**: `multipart/form-data`
- **Parameters**:
  - `name` (String, required): Display name of the dataset.
  - `format` (String, required): `COCO` or `YOLO`.
  - `archive` (MultipartFile, required): ZIP archive with images and annotations.
- **Behavior**: Performs Zip-Slip safe extraction, calculates SHA-256 hash of archive, and registers dataset in SQLite.
- **Redirects to**: `/datasets/{id}`

### 2.3 View Dataset Details
- **Endpoint**: `GET /datasets/{id}`
- **Parameters**: `id` (Long, Path Variable): Dataset ID.
- **Response**: HTML View (`dataset/view`) with image counts, annotation counts, and audit history.

### 2.4 Start Dataset Integrity Audit
- **Endpoint**: `POST /datasets/{id}/audit`
- **Description**: Initiates synchronous or async dataset integrity audit.
- **Behavior**: Executes `CocoParser`/`YoloParser`, `DuplicateDetector` (dHash & SHA-256), `AnnotationValidator`, `ImageIntegrityAnalyzer`, and `DatasetStatisticsService`.
- **Redirects to**: `/datasets/{id}/audits/{auditId}`

### 2.5 View Dataset Audit Findings
- **Endpoint**: `GET /datasets/{id}/audits/{auditId}`
- **Description**: Displays audit results, integrity score ($0-100$), decision (`ACCEPT`, `REVIEW`, `QUARANTINE`), and findings table with evidence.

---

## 3. Model Endpoints

### 3.1 List Models
- **Endpoint**: `GET /models`
- **Description**: Lists all registered ML models.

### 3.2 Upload Model
- **Endpoint**: `POST /models/upload`
- **Content-Type**: `multipart/form-data`
- **Parameters**:
  - `name` (String, required): Model name.
  - `format` (String, required): `ONNX` or `TORCHSCRIPT`.
  - `modelFile` (MultipartFile, required): `.onnx` or `.pt`/`.pth` file.
- **Behavior**: Computes SHA-256 hash, stores model binary, and registers model record.

### 3.3 Start Model Audit
- **Endpoint**: `POST /models/{id}/audit`
- **Behavior**: Inspects tensor metadata, structural fingerprint, verifies deterministic repeatability, executes localized perturbation tests, and records findings.

---

## 4. Inference Provenance Endpoints

### 4.1 View Provenance Chain
- **Endpoint**: `GET /inference`
- **Description**: Shows all inference records, nonces, previous hash links, and verification status badges.

### 4.2 Import Inference Records
- **Endpoint**: `POST /inference/import`
- **Parameters**: `jsonFile` (MultipartFile) OR `jsonContent` (String).
- **Behavior**: Imports JSON array of inference records and verifies the cryptographic chain.

### 4.3 Verify All Records
- **Endpoint**: `POST /inference/verify-all`
- **Behavior**: Re-evaluates hash links, nonces, sequence numbers, and HMAC signatures across all records.

---

## 5. Distribution Shift Endpoints

### 5.1 Drift Overview
- **Endpoint**: `GET /drift`
- **Description**: Drift analysis portal and history.

### 5.2 Execute Drift Analysis
- **Endpoint**: `POST /drift/run`
- **Parameters**:
  - `referenceDatasetId` (Long): Baseline dataset.
  - `evaluationDatasetId` (Long): Target evaluation dataset.
- **Behavior**: Extracts image statistics (brightness, contrast, intensity, RGB) and calculates PSI, Kolmogorov-Smirnov, and Wasserstein metrics.

---

## 6. Report Endpoints

### 6.1 Generate Report
- **Endpoint**: `POST /reports/generate`
- **Parameters**:
  - `title` (String): Report title.
  - `format` (String): `JSON`, `HTML`, or `PDF`.
- **Behavior**: Compiles findings, metrics, and justifications, saving the file to `data/reports/`.

### 6.2 Download Report
- **Endpoint**: `GET /reports/download/{id}`
- **Response**: Binary file download with appropriate `Content-Disposition`.
