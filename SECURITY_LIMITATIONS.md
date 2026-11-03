# SentinelCV: Security Principles & Limitations

## 1. Security Principles Implemented

### 1.1 Offline Air-Gapped Operation
SentinelCV is engineered to operate completely offline without internet connectivity:
- No third-party analytics, tracking scripts, CDNs, or telemetry.
- No external model downloads or cloud inference endpoints.
- Database runs locally via SQLite in Write-Ahead Logging (WAL) mode.
- System operates in fully isolated, air-gapped environments.

### 1.2 Defensive Archive Extraction Security (`ArchiveUtil.java`)
- **Strict Quantitative Limits**:
  - Maximum archive entries: **10,000**.
  - Maximum total uncompressed extraction size: **500 MB**.
  - Maximum individual entry size: **100 MB**.
  - Maximum compression ratio: **100:1** (calculated with accurate byte accounting, preventing division-by-zero).
- **Symlink Traversal Prevention**:
  - Rejects symbolic-link destination directory and non-system symbolic-link parent path components.
  - Inspects ZIP entry metadata and Unix external attributes (`entry.isUnixSymlink()` and `(entry.getUnixMode() & 0170000) == 0120000`) using Apache Commons Compress `ZipFile`. Entries identified as symbolic links are rejected prior to extraction.
  - *Limitation Note*: On non-POSIX archives or filesystems lacking Unix external attribute metadata, symbolic link detection relies on standard entry attributes.
- **Normalization and Zip-Slip Protection**:
  - Normalizes all paths and verifies every target file remains strictly inside the canonical extraction directory.
  - Overwriting files outside the extraction directory is strictly prevented.
- **Null-Byte Filename Truncation Filter**:
  - Reject filenames containing embedded null bytes (`\0`).
- **Magic-Byte Verification**:
  - Inspects initial file header for standard ZIP magic bytes (`0x50, 0x4B, 0x03, 0x04`).
- **Selective Atomic Rollback**:
  - Snapshots pre-existing files prior to extraction. On any extraction failure or security violation, only newly created files and directories are rolled back, preserving unrelated pre-existing data.

### 1.3 Cryptographic Provenance & Deterministic Canonical Serialization (`InferenceValidator.java`)
- **Unambiguous Length-Prefixed Canonical Encoding (Schema V2)**:
  - Canonical format: `V2|<len>:<recordId>;<len>:<sequenceNumber>;<len>:<timestamp>;<len>:<modelIdentifier>;<len>:<modelHash>;<len>:<inputImageHash>;<len>:<configHash>;<len>:<outputHash>;<len>:<nonce>;<len>:<previousRecordHash>`
  - Encodes all 10 security-relevant immutable record fields.
  - Length-prefixed encoding eliminates field boundary ambiguity and delimiter collision attacks.
  - Identical canonical bytes are used for both SHA-256 record hashing and HMAC generation/verification.
  - Missing keys or signatures result in `UNAVAILABLE` and cannot silently produce `VERIFIED`.
  - Malformed fields fail verification immediately.

### 1.4 Hardened Provenance Key Management (`ProvenanceKeyManager.java`)
- **Key Generation**: 256-bit cryptographically secure keys generated via `java.security.SecureRandom`.
- **Atomic POSIX Keystore Creation**:
  - Created atomically via temporary file and atomic filesystem move (`ATOMIC_MOVE`).
  - Permissions set immediately upon creation to `0600` (`OWNER_READ | OWNER_WRITE` only) on POSIX filesystems.
  - Fallback restrictive permission configuration applied on non-POSIX filesystems (Windows).
- **Secret Safety**:
  - Secret key values are never output to logs or included in exception messages.
  - Concurrent initialization is thread-safe (`synchronized`).
  - Key generation will never overwrite an existing valid key file.
- **Explicit Operator Action on Corruption**:
  - Corrupt or insufficient-entropy key files (< 32 characters) throw explicit `IllegalStateException`, halting execution and requiring manual operator intervention rather than silently generating new inconsistent keys.

### 1.5 Strict Assessment & Risk Decision Precedence
- **Model Audit Accept Decision**:
  - Model audit requires that runtime inference is supported (`metadata.inferenceSupported`), baseline inference was executed (`behavioralResult.inferenceExecuted`), and trigger perturbation analysis was executed (`triggerReport.executed`).
  - If any mandatory check was not executed or failed, decision is mandated to `REVIEW`, never falsely producing `ACCEPT`.
- **Global Risk Assessment Precedence (`RiskAssessmentService.java`)**:
  - Explicit check execution status (`CheckExecutionStatus`: `PASSED`, `FAILED`, `UNAVAILABLE`, `NOT_EXECUTED`).
  - Availability is not inferred solely from INFO severity or description strings; checks report status explicitly.
  - Strict precedence:
    1. Any critical integrity finding → `QUARANTINE`.
    2. Otherwise, any high-risk finding → `REVIEW`.
    3. Otherwise, any required check `UNAVAILABLE` or `NOT_EXECUTED` → `REVIEW`.
    4. Otherwise, `ACCEPT` only when all mandatory checks `PASSED`.
  - Positive numerical risk scores cannot override an unavailable check.
  - Missing or null check statuses are never silently treated as `PASSED`.
- **Provenance Batch Decision Precedence (`InferenceProvenanceService.java`)**:
  - 1. Any broken hash chain → `QUARANTINE`.
  - 2. Any tampered authenticated payload / invalid hash → `QUARANTINE`.
  - 3. Replay detection without cryptographic integrity compromise → `REVIEW`.
  - 4. Missing key or unavailable cryptographic verification → `REVIEW`.
  - 5. `VERIFIED` only if all records pass all checks.
  - Decision is deterministic and independent of record input order.

---

## 2. Fundamental Limitations and Boundary Assumptions

### 2.1 Software Hashing vs. Hardware Attestation
A valid SHA-256 hash or HMAC proves internal consistency with recorded metadata and validates that intermediate log entries have not been excised or tampered with. **It does not prove that an inference was genuinely executed on trusted hardware or inside a secure enclave.**
- *Threat*: An adversary with root/administrator access on the host could synthesize outputs, hash them correctly, and sign them with a locally accessible key.
- *Remediation*: High-assurance environments must combine SentinelCV's software hash chains with hardware-based remote attestation (TPM 2.0 or Intel SGX / AMD SEV).

### 2.2 Local Key Storage Threat Model
- Local key management stores keys on the host filesystem (`./data/keys/provenance.key`).
- While permissions are restricted to `0600`, any process running under the same user UID has access to the secret.
- **Disclaimer**: This is local offline key management, NOT a Hardware Security Module (HSM). Enterprise environments requiring non-exportable keys must integrate with an external HSM or KMS.

### 2.3 Heuristic Nature of Behavioral & Perturbation Testing
- **Synthetic Fixture Purpose**: The synthetic ReLU ONNX fixture demonstrates runtime execution, determinism testing, and pipeline mechanics; it is not a trained deep vision classifier.
- **Perturbation Limits**: Perturbation testing (localized spatial patch and global Gaussian jitter) is an empirical behavioral heuristic, **NOT a mathematical proof of the absence of backdoors or trojans**.
- Advanced clean-label, frequency-domain, or multi-trigger backdoors cannot be ruled out without white-box architectural proofs and training dataset supply chain verification.

### 2.4 Model Runtime Operator Compatibility
- Deep learning runtime inference relies on native ONNX Runtime C++ binaries. Models utilizing unsupported custom operators cannot execute dynamic inference in pure Java.
- SentinelCV transparently marks such checks as `UNAVAILABLE` and mandates `REVIEW`, adhering to fail-safe defaults.

### 2.5 Academic Prototype Classification
SentinelCV is an **offline academic security assessment prototype**. It provides automated structural integrity checks, forensic logging, and explainable risk scores, but must not be treated as a commercially certified cybersecurity certification appliance.
