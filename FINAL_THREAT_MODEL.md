# SentinelCV — Final Threat Model & Security Architecture

**Project**: SentinelCV — Offline Computer Vision Security and Integrity Assessment System  
**Stack**: Java 21, Spring Boot 3.2.5, SQLite, ONNX Runtime 1.17.1, DJL 0.27.0, Thymeleaf, Maven  
**Date**: November 3, 2026  
**Status**: APPROVED — COMPREHENSIVE ZERO-REGRESSION THREAT SPECIFICATION  

---

## 1. System Overview & Trust Boundaries

SentinelCV is designed as an autonomous, offline assessment system that evaluates computer vision models, dataset corpora, and runtime inference provenance for security risks, adversarial vulnerabilities, and data tampering.

```
+-----------------------------------------------------------------------------------+
|                              UNTRUSTED BOUNDARY                                   |
|  - Untrusted ZIP Archives (Datasets)                                              |
|  - Untrusted ONNX Models                                                          |
|  - Unauthenticated Local HTTP Clients / Operators                                 |
|  - Concurrent External OS Processes on Shared Filesystem                         |
+-----------------------------------------------------------------------------------+
                                        |
                                        v
+-----------------------------------------------------------------------------------+
|                        SENTINELCV VERIFICATION CORE                               |
|                                                                                   |
|  +-----------------------+   +------------------------+   +--------------------+  |
|  | Archive Extraction    |   | Deterministic Hashing  |   | Provenance Engine  |  |
|  | Confinement & Quotas  |   | Manifest 'SCVM' v1     |   | HMAC-SHA256 Chain  |  |
|  +-----------------------+   +------------------------+   +--------------------+  |
|                                                                                   |
|  +-----------------------------------------------------------------------------+  |
|  | Global Risk Assessment Engine (Strict Precedence & Freshness Validation)    |  |
|  +-----------------------------------------------------------------------------+  |
+-----------------------------------------------------------------------------------+
                                        |
                                        v
+-----------------------------------------------------------------------------------+
|                               PROTECTED STORAGE                                   |
|  - SQLite Database (`sentinelcv.db`)                                              |
|  - Root-protected Provenance HMAC Key (`sentinelcv.key`, POSIX 0600)              |
|  - Datasets (`./data/datasets/`)                                                  |
|  - Model Repository (`./data/models/`)                                            |
|  - Compliance Reports (`./data/reports/`)                                         |
+-----------------------------------------------------------------------------------+
```

---

## 2. Protected Assets

| Asset | Description | Integrity & Confidentiality Requirement |
| :--- | :--- | :--- |
| **Model Weights (`.onnx`)** | Binary weights executing inference pipelines. | Integrity & authenticity. Tampering must be detected on disk before evaluation. |
| **Dataset Corpora** | Extracted image directories, labels, and metadata. | Complete snapshot integrity. No excluded files, no symlink escapes. |
| **Inference Provenance Chain** | Cryptographically chained audit trail (`inference_records`). | Strict append-only integrity. Sequence monotonicity, HMAC authentication, previous-hash linkage. |
| **Provenance Secret Key** | 256-bit symmetric key (`sentinelcv.key`). | Strict confidentiality & integrity. POSIX `0600` / Windows single-owner ACL. Must never be exposed via logs or API. |
| **Audit Findings & Reports** | Compliance reports and risk decisions (`ACCEPT`, `REVIEW`, `QUARANTINE`). | Authenticity and alignment. System risk assessment must reflect real-time ground truth. |

---

## 3. Adversary Profiles & Threat Vectors

### Threat Vector T1: Local Filesystem Attacker (Symlink TOCTOU & Directory Swaps)
- **Attacker Profile**: Local unprivileged process or multi-tenant user sharing the host filesystem.
- **Attack Methods**:
  - Replacing intermediate directory components with symlinks while SentinelCV extracts a dataset archive.
  - Swapping the destination directory root during the extraction commit phase.
  - Pre-creating target files to overwrite existing system files (`/etc/passwd`, application code).
- **SentinelCV Defense**:
  - Mutual exclusion via kernel `FileLock` (`.sentinelcv_extract.lock`) preventing concurrent extractions.
  - Unguessable staging directory with POSIX 0700 permissions.
  - Component-by-component symlink validation using `LinkOption.NOFOLLOW_LINKS`.
  - Atomic leaf creation via `StandardOpenOption.CREATE_NEW` (`O_CREAT | O_EXCL`).
  - Fail-closed platform check: If `SecureDirectoryStream` is not supported (macOS Darwin), extraction is aborted with `UNSUPPORTED_FILESYSTEM` before writing to destination.

### Threat Vector T2: Malicious Archive Delivery (Zip-Slip & Resource Bombs)
- **Attacker Profile**: External entity uploading malicious dataset archives through the UI or API.
- **Attack Methods**:
  - Path traversal entry names (`../../`, absolute paths `/tmp/evil`, null-byte injections `test\0.txt`).
  - Zip bombs (> 10,000 entries, 500 MB expansion, > 100:1 compression ratio).
- **SentinelCV Defense**:
  - Strictest zip entry normalization rejecting any path with `..` or leading `/`.
  - Quota accounting rejecting archives exceeding 10,000 entries or 500 MB.
  - Continuous ratio enforcement checking every entry > 1 KB against the 100:1 ratio limit.

### Threat Vector T3: Steganography & Blind-Spot Hashing Bypass
- **Attacker Profile**: Adversary attempting to embed backdoors or triggers into unmonitored files.
- **Attack Methods**:
  - Storing adversarial triggers in `.DS_Store`, `Thumbs.db`, `._*` AppleDouble files, or `*.tmp` files under the assumption they are skipped by hashing algorithms.
  - Modifying dataset files concurrently while hashing is taking place.
- **SentinelCV Defense**:
  - Binary Manifest format (`SCVM` v1): Zero exclusions. Every regular file without exception contributes to the hash.
  - Non-regular files (symlinks, FIFOs, character/block devices) immediately abort hashing.
  - Pre-read and post-read file metadata checks combined with whole-directory snapshot tree verification detect any concurrent modification.

### Threat Vector T4: Database Insider & Provenance Chain Tampering
- **Attacker Profile**: Rogue DBA, local user with direct SQLite access, or malware altering database tables.
- **Attack Methods**:
  - Modifying historical inference outputs (`raw_output`, `model_identifier`) to conceal malicious inference.
  - Truncating or deleting intermediate records.
  - Inserting forged records with matching sequential IDs.
  - Tampering with HMAC values.
- **SentinelCV Defense**:
  - Full cryptographic chain verification on every risk assessment.
  - Zero in-request caching (`ThreadLocal` removed); every call executes full chain verification.
  - Strict verification of payload hash, HMAC-SHA256 signature, sequence monotonicity, and previous-record hash linking.
  - Any single broken record marks `provenance.verification` as `FAILED` and forces overall system decision to `REVIEW` or `QUARANTINE`.

### Threat Vector T5: Partial Degradation & Fail-Open Exploitation
- **Attacker Profile**: Adversary causing denial of service on database or unreadable filesystem permissions to bypass checks.
- **Attack Methods**:
  - Causing repository null pointers or connection drops hoping the system defaults to `PASSED`.
  - Supplying invalid check IDs.
- **SentinelCV Defense**:
  - Strict fail-closed defaults across all services. Null repositories, unreadable assets, and unknown check IDs return `UNAVAILABLE`.
  - Strict precedence: `ACCEPT` strictly requires 100% of mandatory checks to be `PASSED` and zero unmitigated findings.

---

## 4. Platform Limitations & Boundaries

### 1. macOS SecureDirectoryStream Limitation
The macOS Darwin implementation of OpenJDK (`sun.nio.fs.UnixDirectoryStream`) does not implement `java.nio.file.SecureDirectoryStream`. Because POSIX `openat()` semantics are unavailable through Java NIO on macOS, genuine race-free relative directory operations cannot be guaranteed by the JVM on this platform.
- **Default SentinelCV Behavior on macOS**: Fails closed with `ArchiveSecurityException` (`UNSUPPORTED_FILESYSTEM`).
- **Development/Test Property**: `-Dsentinelcv.archive.allow_insecure_fallback=true` permits test suites to run on macOS while emitting high-priority security warnings.
- **Production Recommendation**: Deploy SentinelCV on Linux (Ubuntu 22.04+ / RHEL 9+) on ext4 or xfs, where `SecureDirectoryStream` is fully supported.

### 2. Physical Machine & Kernel Security
SentinelCV protects against local unprivileged processes and malicious data feeds. It does **not** protect against:
- Compromise of the root/administrator account or the kernel running the JVM.
- Cold-boot attacks or direct physical tampering with hardware RAM.
- Modification of JVM bytecode via Java agent injection by users with matching process ownership.

---

## 5. Summary of Security Guarantees

| Security Property | Architectural Mechanism | Guarantee Level |
| :--- | :--- | :--- |
| **Archive Traversal Prevention** | Component-by-component canonical resolution + `CREATE_NEW` | Guaranteed (Provable) |
| **Cross-Process Race Prevention** | Kernel-level OS `FileLock` + in-process `ReentrantLock` | Guaranteed |
| **Dataset Manifest Integrity** | Length-prefixed binary manifest (`SCVM` v1) with raw SHA-256 | Guaranteed (Collision Resistant) |
| **Concurrent Mutation Detection** | Snapshot tree verification + pre/post file metadata verification | Guaranteed |
| **Provenance Chain Non-Repudiation** | HMAC-SHA256 with root key + recursive hash chain | Guaranteed |
| **Freshness Verification** | Ground-truth on-disk hash recomputation on every assessment | Guaranteed (Zero Stale State) |
| **Fail-Closed Fallbacks** | Null checks and IO exceptions map to `UNAVAILABLE` / `REVIEW` | Guaranteed (Zero Fail-Open) |

